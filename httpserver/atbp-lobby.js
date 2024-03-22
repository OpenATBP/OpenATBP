const net = require('net');
const config = require('./config.js');

var queues = [];
var users = [];
var teams = [];
var queueNum = 0;

function tryParseJSONObject (jsonString){
  try {
    var obj = JSON.parse(jsonString);
    if (obj && typeof obj === "object") {
        return obj;
    }
  }
  catch (err) {
    return false;
  }
};

function sendAll (sockets, response){
  response = JSON.stringify(response);
  for(var socket of sockets){
    console.log("Sending to " + socket.player.name + "->", response);
    let lengthBytes = Buffer.alloc(2);
    lengthBytes.writeInt16BE(Buffer.byteLength(response, 'utf8'));
    socket.write(lengthBytes);
    socket.write(response);
  }
}

function safeSendAll(sockets,command,response){
  return new Promise(function(resolve, reject) {
    if(sockets.length == 0) resolve(true);
    else {
      sendCommand(sockets[0],command,response).then(() => {
        sockets.shift();
        resolve(safeSendAll(sockets,command,response));
      }).catch(console.error);
    }
  });
}

function sendCommand (socket, command, response){
    console.log(`Sending ${command} to ${socket.player.name}`);
    return new Promise(function(resolve, reject) {
      if(command == undefined && response == undefined) reject();
      var package = {
        'cmd': command,
        'payload': response
      };
      console.log("Sent ", package);
      package = JSON.stringify(package);
      let lengthBytes = Buffer.alloc(2);
      lengthBytes.writeInt16BE(Buffer.byteLength(package, 'utf8'));
      socket.write(lengthBytes);
      socket.write(package, () => {
        console.log("Finished sending package to ", socket.player.name);
        resolve();
      });
    });
}

function joinQueue(socket, queue){
  var res = {};
  queue.players.push(socket.player);
  socket.player.queueNum = queue.queueNum;
  displayTeams();
  displayQueues();
  if(queue.players.length < queue.max){
  var size = queue.players.length;
  if(size>3){
    size = 3;
    safeSendAll(users.filter(user => queue.players.includes(user.player) && user.player.onTeam),'team_full',{'full':true}).catch(console.error);
  }
    res = {
      'cmd': 'queue_update',
      'payload': {
        'size': size
      }
    };

    sendAll(users.filter(user => (queue.players.includes(user.player))), res);
  }else{
    safeSendAll(users.filter(user => queue.players.includes(user.player)),'team_full',{'full':true}).then(()=> {
        var purpleTeam = [];
        var blueTeam = [];

        for(var p of queue.players){
          var playerObj = {
            'name': p.name,
            'player': p.player,
            'teg_id': `${p.teg_id}`,
            'avatar': 'unassigned',
            'is_ready': false
          };
          if(p.team == 0) purpleTeam.push(playerObj);
          else blueTeam.push(playerObj);
          p.inGame = true;
        }

        queue.purple = purpleTeam;
        queue.blue = blueTeam;
        queue.stage = 1;

        safeSendAll(users.filter(user => queue.players.includes(user.player) && user.player.team == 0), 'game_ready',{
          'countdown': 60,
          'ip': config.lobbyserver.gameIp,
          'port': config.lobbyserver.gamePort,
          'policy_port': config.sockpol.port,
          'room_id': `GAME${queue.queueNum}_${queue.type}`,
          'password': '',
          'team': 'PURPLE'
        }).then(() => {
          safeSendAll(users.filter(user => user.player.team == 0 && user.player.queueNum == queue.queueNum),'team_update', {
            'players': purpleTeam,
            'team': `PURPLE`
          });
        }).catch(console.error);

        safeSendAll(users.filter(user => queue.players.includes(user.player) && user.player.team == 1), 'game_ready',{
          'countdown': 60,
          'ip': config.lobbyserver.gameIp,
          'port': config.lobbyserver.gamePort,
          'policy_port': config.sockpol.port,
          'room_id': `GAME${queue.queueNum}_${queue.type}`,
          'password': '',
          'team': 'BLUE'
        }).then(() => {
          safeSendAll(users.filter(user => user.player.team == 1 && user.player.queueNum == queue.queueNum),'team_update', {
            'players': blueTeam,
            'team': `BLUE`
          });
        }).catch(console.error);
    }).catch(console.error);
  }
}

function leaveQueue(socket){
  for(var q of queues){
    if(q.players.includes(socket.player)){
      q.players = q.players.filter(p => p != socket.player);
      var res = {};
      if(q.players.length == 0) break;
      if(q.stage == 1){
        safeSendAll(users.filter(u=> q.players.includes(u.player)), 'team_disband', {'reason': 'error_lobby_playerLeftMatch'}).catch(console.error);
        q.players = [];
        socket.player.inGame = false;
      }else{
        var size = q.players.length;
        if(size > 3) size = 3;
          res = {
            'cmd': 'queue_update',
            'payload': {
              size: size
            }
          };
        }
        sendAll(users.filter(user => q.players.includes(user.player)),res);
        break;
      }
    }
  queues = queues.filter(q => q.players.length > 0);
  socket.player.queueNum = -1;
  socket.player.team = -1;
  response = {
    'cmd': 'queue_update',
    'payload': {
      'size': 0
    }
  };
}

function createQueue(socket, type, queueSize){
  if(queueSize == 1){
    var playerObj = {
      'name': socket.player.name,
      'player': socket.player.player,
      'teg_id': `${socket.player.teg_id}`,
      'avatar': 'unassigned',
      'is_ready': false
    };
    socket.player.team = 0;
    socket.player.inGame = true;
    queues.push({
      'type': type,
      'players': [socket.player],
      'queueNum': queueNum,
      'blue': [],
      'purple': [playerObj],
      'ready': 0,
      'max': queueSize,
      'stage': 1
    });
    sendCommand(socket,'game_ready',{
      'countdown': 60,
      'ip': config.lobbyserver.gameIp,
      'port': config.lobbyserver.gamePort,
      'policy_port': config.sockpol.port,
      'room_id': `${socket.player.name}_${type}`,
      'password': '',
      'team': 'PURPLE'
    }).then(() => {
      sendCommand(socket,'team_update', {
        'players': [playerObj],
        'team': `PURPLE`
      });
    }).catch(console.error);
  }else{
    response = {
      'cmd': 'queue_update',
      'payload': {
        'size': 1
      }
    };
    queues.push({
      'type': type,
      'players': [socket.player],
      'queueNum': queueNum,
      'blue': [],
      'purple': [],
      'ready': 0,
      'max': queueSize,
      'stage': 0
    });
  }
  socket.player.queueNum = queueNum;
  socket.player.team = 0;
  queueNum++;
}

function leaveTeam(socket){
  var team = teams.find(t => t.players.includes(socket.player));
  if(team != undefined){
    socket.player.onTeam = false;
    team.players = team.players.filter(p => p.name != socket.player.name);
    console.log(team.players);
    if(socket.player == team.teamLeader){
      safeSendAll(users.filter(u=> team.players.includes(u.player)), 'team_disband', {'reason': 'error_lobby_teamLeader'}).catch(console.error);
      for(var p of team.players){
        p.onTeam = false;
      }
      team.teamLeader = undefined;
    }else{
      var teamObj = [];
      for(var p of team.players){
        var playerObj = {
          'name': p.name,
          'teg_id': p.teg_id,
          'player': p.player
        };
        teamObj.push(playerObj);
      }
      safeSendAll(users.filter(u => team.players.includes(u.player)), 'team_update', {'players': teamObj, 'team': team.team}).catch(console.error);
      sendCommand(users.find(u => u.player == team.teamLeader), 'invite_declined', {'player': socket.player.teg_id}).catch(console.error);
    }
    if(team.queueNum != -1){
      leaveQueue(socket);
      for(var p of team.players){
        leaveQueue(users.find(u=>u.player == p));
      }
      team.queueNum = -1;
    }
    teams = teams.filter(t => t.teamLeader != undefined);
  }
}

function displayQueues(){
  for(var q of queues){
    var blue = 0;
    var purple = 0;
    for(var p of q.players){
      if(p.team == 0) purple++;
      else blue++;
    }
    console.log(`QUEUE ${q.queueNum}: ${q.players.length} players, Type ${q.type}, Stage ${q.stage}, Blue: ${blue}, Purple: ${purple}`);
  }
  if(queues.length == 0) console.log("NO QUEUES");
}

function displayTeams(){
  for(var t of teams){
    console.log(`${t.teamLeader.name}'s Team: ${t.players.length} members, In Queue: ${t.queueNum != -1}'`);
  }
  if(teams.length == 0) console.log("NO TEAMS");
}

// TODO: move out to separate file
function handleRequest (jsonString, socket) {
  let jsonObject = tryParseJSONObject(jsonString);
  if (!jsonObject) {
    return
  }

  let response = null;
  console.log("<-", jsonObject['req'], jsonObject['payload'])

  switch (jsonObject['req']) {
    case "handshake":
      response = {
        'cmd': "handshake",
        'payload': {
          'result': true
        }
      };
      break;

    case "login":
      response = {
        'cmd': 'login',
        'payload': {
          'name': decodeURI(jsonObject['payload'].name),
          'player': Number(jsonObject['payload'].auth_id),
          'teg_id': jsonObject['payload'].teg_id
        }
      };
      break;

      case 'auto_join':
        var tries = 0;
        var act = jsonObject['payload'].act.split("_");
        var type = act[act.length-1];
        var res = {};
        var queueSize = 1;
        console.log("Joining type " + type);
        if(type.includes('p') && type != 'practice') queueSize = Number(type.replace("p",""));
        if(queueSize == 3) queueSize = 2; //Turns bots to 1v1
        for(var q of queues){
          if(q.type == type && q.players.length < q.max){ //Queue is the same type and has space
            var blueTeam = 0;
            var purpleTeam = 0;
            var teamMax = q.max/2;
            for(var p of q.players){
              if(p.team == 0) purpleTeam++;
              else blueTeam++;
            }
            var canJoinBlue = blueTeam < teamMax;
            var canJoinPurple = purpleTeam < teamMax;
            if (blueTeam < purpleTeam && canJoinPurple){ //Join purple
              socket.player.team = 0;
            }else if(purpleTeam < blueTeam && canJoinBlue){ // Join blue
              socket.player.team = 1;
            }else if (canJoinPurple){ // Join purple as last resort
              socket.player.team = 0;
            }else if(canJoinBlue){ // Join blue as last resort
              socket.player.team = 1;
            }else{
              console.log("Can't join queue - Team Error");
              tries++;
              break;
            }
            // JOINS QUEUE
            console.log("Joining queue...");
            joinQueue(socket,q);
            break;
          } else{
            //console.log("Can't join queue! ", q);
            tries++;
          }
        }
        if(tries == queues.length){
          createQueue(socket,type, queueSize);
        }
        displayQueues();
        displayTeams();
      break;

      case 'leave_team':
        if(socket.player.onTeam){
          leaveTeam(socket);
        }else leaveQueue(socket);
        displayQueues();
        displayTeams();
        break;

      case 'set_avatar':
        for(var q of queues){
          if(q.queueNum == socket.player.queueNum){
            if(q.ready == q.max) break;
            if(socket.player.team == 0){
              for(var pUser of q.purple){
                if(pUser.name == socket.player.name){
                  if(pUser.is_ready) shouldSend = false;
                  pUser.avatar = jsonObject['payload'].name;
                  break;
                }
              }
                sendAll(users.filter(user => user.player.team == 0 && user.player.queueNum == q.queueNum), {
                    'cmd': 'team_update',
                    'payload': {
                      'players': q.purple,
                      'team': `PURPLE`
                    }
                });
            }else{
              for(var pUser of q.blue){
                if(pUser.name == socket.player.name){
                  if(pUser.is_ready) shouldSend = false;
                  pUser.avatar = jsonObject['payload'].name;
                  break;
                }
              }
              sendAll(users.filter(user => user.player.team == 1 && user.player.queueNum == q.queueNum), {
                  'cmd': 'team_update',
                  'payload': {
                  'players': q.blue,
                  'team': `BLUE`
                  }
              });
            }
            break;
          }
        }
        break;

      case 'set_ready':
      for(var q of queues){
        if(q.queueNum == socket.player.queueNum){
            var blueTrigger = false;
            var purpleTrigger = false;
            for(var pUser of q.purple){
              if(pUser.name == socket.player.name){
                pUser.is_ready = true;
                purpleTrigger = true;
                q.ready++;
                break;
              }
            }
            for(var pUser of q.blue){
              if(pUser.name == socket.player.name){
                pUser.is_ready = true;
                blueTrigger = true;
                q.ready++;
                break;
              }
            }
            if(purpleTrigger){
              sendAll(users.filter(user => user.player.team == 0 && user.player.queueNum == q.queueNum), {
                'cmd': 'team_update',
                'payload': {
                  'players': q.purple,
                  'team': `PURPLE`
                }
              });
            }else if(blueTrigger){
              sendAll(users.filter(user => user.player.team == 1 && user.player.queueNum == q.queueNum), {
                'cmd': 'team_update',
                'payload': {
                  'players': q.blue,
                  'team': `BLUE`
                }
              });
            }
            if(q.ready == q.max) q.stage = 2;
          break;
        }
      }
        break;
      case 'chat_message':
        for(var q of queues){
          var player = q.players.find(p => p.teg_id == jsonObject['payload'].teg_id);
          if(player != undefined) {
            for(var p of q.players){
              var sock = users.find(u => u.player == p);
              if(sock != undefined) sendCommand(sock,'chat_message',{'name':player.name,'teg_id':player.teg_id,'message_id':Number(jsonObject['payload'].message_id)}).catch(console.error);
              else console.log("No socket found!");
            }
          }
        }
        break;

        case 'create_team':
          var playerObj = {
            'name': socket.player.name,
            'teg_id': socket.player.teg_id,
            'player': socket.player.player
          };
          var teamObj = {
            'teamLeader': socket.player,
            'players': [socket.player],
            'team': socket.player.teg_id,
            'queueNum': -1,
            'type': jsonObject['payload'].act
          };
          response = {
            'cmd': 'team_update',
            'payload': {
              'players': [playerObj],
              'team': teamObj.team
            }
          };
          socket.player.onTeam = true;
          teams.push(teamObj);
          displayTeams();
          break;

        case 'send_invite':
          var invitedPlayer = users.find(user => user.player.teg_id == jsonObject['payload'].player);
          if(invitedPlayer != undefined){
            var team = teams.find(t => t.teamLeader == socket.player);
            if(team != undefined){
              sendCommand(invitedPlayer, 'receive_invite', {'name': socket.player.name, 'player': socket.player.player, 'act': team.type, 'vs': true, 'team': socket.player.teg_id}).catch(console.error);
            }else console.log("Team Leader invalid!");
          }else console.log("Can't find player!");
          break;

        case 'join_team':
          var team = teams.find(t => t.teamLeader.teg_id == jsonObject['payload'].name);
          if(team != undefined && team.players.length < 3 && team.queueNum == -1){
            socket.player.onTeam = true;

            team.players.push(socket.player);
            var teamObjs = [];
            for(var p of team.players) {
              var playerObj = {
                'name': p.name,
                'teg_id': p.teg_id,
                'player': p.player
              };
              teamObjs.push(playerObj);
            }
            response = {
              'cmd': 'invite_verified',
              'payload': {
                'result': 'success'
              }
            };
            safeSendAll(users.filter(u => team.players.includes(u.player)),'team_update',{'players': teamObjs, 'team': team.team}).catch(console.error);
          }else{
            response = {
              'cmd': 'invite_verified',
              'payload': {
                'result': 'failed'
              }
            };
          }
          displayTeams();
          break;

        case 'decline_invite':
          var partyLeader = jsonObject['payload'].party_leader;
          if(partyLeader != undefined){
            var teamLeader = users.find(user => Math.abs(jsonObject['payload'].party_leader - user.player.player) <= 500);
          }else console.log(jsonObject['payload']);
          if(teamLeader != undefined) sendCommand(teamLeader, 'invite_declined', {'player':socket.player.teg_id});
          else {
            console.log("No leader found!");
          }
          break;

        case 'unlock_team':
          var team = teams.find(t => t.team == jsonObject['payload'].team);
          var tries = 0;
          if(team != undefined){
            for(var q of queues){
              if(q.players.length + team.players.length <= q.max){
                var blueTeam = 0;
                var purpleTeam = 0;
                var teamJoined = -1;
                for(var p of q.players){
                  if(p.team == 0) purpleTeam++;
                  else blueTeam++;
                }
                if(blueTeam+team.players.length <= 3){
                  teamJoined = 1;
                }else if(purpleTeam+team.players.length <= 3){
                  teamJoined = 0;
                }else tries++;

                if(teamJoined != -1){
                  for(var user of users.filter(u => team.players.includes(u.player))){
                    user.player.team = teamJoined;
                    joinQueue(user,q);
                  }
                  break;
                }
              }
            }
            if(tries == queues.length){
              for(var user of users.filter(u => team.players.includes(u.player))){
                user.player.team = 0;
                user.player.queueNum = queueNum;
              }
              //TODO: Change createQueue to make this work with it
              var act = team.type.split("_");
              var type = act[act.length-1];
              var queueSize = 1;
              if(type.includes('p') && type != 'practice') queueSize = Number(type.replace("p",""));
              queues.push({
                'type': type,
                'players': [...team.players],
                'queueNum': queueNum,
                'blue': [],
                'purple': [],
                'ready': 0,
                'max': queueSize,
                'stage': 0
              });
              team.queueNum = queueNum;
              queueNum++;

            }
          }
          displayQueues();
          displayTeams();
          break;
  };

  if (response){
    console.log("->", response['cmd'], response['payload']);
    if(response['cmd'] == 'login'){
      users.push(socket);
      socket.player = {
       'name': response['payload'].name,
       'teg_id': response['payload'].teg_id,
       'player': response['payload'].player,
       'queueNum': -1,
       'inGame': false,
       'team': -1,
       'onTeam': false
     };
      console.log("Logged In ->", response['payload'].name);
    }
  } else {
    console.log("Unhandled request", jsonObject['req'])
  }

  return JSON.stringify(response);
}

module.exports = class ATBPLobbyServer {
  constructor(port) {
    this.port = port;
    this.server = null;
  }
  start(callback) {
    this.server = net.createServer((socket) => {
      socket.setEncoding('utf8');

      socket.on('readable', () => { //TODO: Add error handlers
        let jsonLength = socket.read(2);
        if (jsonLength == null || 0) {
          socket.destroy();
        } else {
          let packet = socket.read(jsonLength);
          let response = handleRequest(packet, socket);
          if (response != "null" && response != undefined) {
            // Get length of JSON object in bytes
            let lengthBytes = Buffer.alloc(2);
            lengthBytes.writeInt16BE(Buffer.byteLength(response, 'utf8'));
            // Send the length and JSON object down the pipe
            socket.write(lengthBytes);
            socket.write(response);
          }
        }
      });

      socket.on('error', (error) => {
        console.error('Socket error:', error);
        socket.destroy();
      });

      socket.on('close', (err) => {
        console.log(err);
        for(var user of users){
          if (user._readableState.ended){
            console.log(user.player.name + " logged out");
            if(user.player.onTeam) leaveTeam(user);
            else leaveQueue(user);

          }
        }
        users = users.filter(user => !user._readableState.ended);
        displayQueues();
      });
    });

    this.server.listen(this.port, () => {
      callback();
    });
  }
  stop(callback) {
    if (this.server)
      this.server.close(callback());
  }
}
