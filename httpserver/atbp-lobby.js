const net = require('net');
const config = require('./config.js');

var queues = [];
var users = [];
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
        if(type.includes('p') && type != 'practice') queueSize = Number(type.replace("p",""));
        if(queueSize == 3) queueSize = 2; //DEBUG
        for(var q of queues){
          if(q.type == type && q.players.length < q.max && !q.inGame){
            // Join Queue
            q.players.push(socket.player);
            socket.player.queueNum = q.queueNum;
            if(q.players.length < q.max){
            var size = q.players.length;
            if(size>3) size = 3;
              res = {
                'cmd': 'queue_update',
                'payload': {
                  'size': size
                }
              };
              sendAll(users.filter(user => (q.players.includes(user.player))), res);
            }else{
              safeSendAll(users.filter(user => q.players.includes(user.player)),'team_full',{'full':true}).then(()=> {
                  var purpleTeam = [];
                  var blueTeam = [];

                  for(var i = 0; i < q.players.length; i++){
                    var playerObj = {
                      'name': q.players[i].name,
                      'player': q.players[i].player,
                      'teg_id': `${q.players[i].teg_id}`,
                      'avatar': 'unassigned',
                      'is_ready': false
                    };
                    console.log(playerObj);
                    if(i % 2 == 0){
                      purpleTeam.push(playerObj);
                      q.players[i].team = 0;
                    }else{
                      blueTeam.push(playerObj);
                      q.players[i].team = 1;
                    }
                  }
                  q.purple = purpleTeam;
                  q.blue = blueTeam;
                  q.inGame = true;

                  for(var p of q.players){
                    p.inGame = true;
                  }

                  safeSendAll(users.filter(user => q.players.includes(user.player) && user.player.team == 0), 'game_ready',{
                    'countdown': 60,
                    'ip': config.lobbyserver.gameIp,
                    'port': config.lobbyserver.gamePort,
                    'policy_port': config.sockpol.port,
                    'room_id': `GAME${q.queueNum}_${q.type}`,
                    'password': '',
                    'team': 'PURPLE'
                  }).then(() => {
                    safeSendAll(users.filter(user => user.player.team == 0 && user.player.queueNum == q.queueNum),'team_update', {
                      'players': purpleTeam,
                      'team': `PURPLE`
                    });
                  }).catch(console.error);

                  safeSendAll(users.filter(user => q.players.includes(user.player) && user.player.team == 1), 'game_ready',{
                    'countdown': 60,
                    'ip': config.lobbyserver.gameIp,
                    'port': config.lobbyserver.gamePort,
                    'policy_port': config.sockpol.port,
                    'room_id': `GAME${q.queueNum}_${q.type}`,
                    'password': '',
                    'team': 'BLUE'
                  }).then(() => {
                    safeSendAll(users.filter(user => user.player.team == 1 && user.player.queueNum == q.queueNum),'team_update', {
                      'players': blueTeam,
                      'team': `BLUE`
                    });
                  }).catch(console.error);
              }).catch(console.error);
            }
            break;
          }else tries++;
        }
        if(tries == queues.length){
          console.log("New queue! ", queueSize);
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
              'inGame': true
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
              'inGame': false
            });
          }
          socket.player.queueNum = queueNum;
          queueNum++;
        }
      break;

      case 'leave_team':
        for(var q of queues){
          if(q.players.includes(socket.player)){
            q.players = q.players.filter(p => p != socket.player);
            var res = {};
            if(q.players.length == 0) break;
            if(socket.player.inGame){ //Probably will never be called, but it's already here
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
        response = {
          'cmd': 'queue_update',
          'payload': {
            'size': 0
          }
        };
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
          break;
        }
      }
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
       'team': -1
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
        //todo?
        console.log(err);
        for(var user of users){
          if (user._readableState.ended){
            console.log(user.player.name + " logged out");
            for(var q of queues){
              if(user.player.queueNum == -1 && !user.player.inGame) break;
              else if (q.queueNum == user.player.queueNum){
                q.players = q.players.filter(p => p != user.player);
                if(q.players.length == 0) console.log("Removed queue!");
                else{
                  var res = {};
                  if(q.players.length == 1){
                    res = {
                      'cmd': 'team_disband',
                      'payload': {
                        'reason': 'error_lobby_playerLeftMatch'
                      }
                    };
                  }else{
                    var size = q.players.length;
                    if(size > 3) size = 3;
                    res = {
                      'cmd': 'queue_update',
                      'payload': {
                        'size': size
                      }
                    };
                  }
                  sendAll(users.filter(user => q.players.includes(user.player)),res);
                }
              }
            }
            queues = queues.filter(q => q.players.length > 0);
          }
        }
        users = users.filter(user => !user._readableState.ended);
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
