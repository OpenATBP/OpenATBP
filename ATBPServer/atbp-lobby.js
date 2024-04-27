const net = require('net');
const config = require('./config.js');
const matchmaking = require('./matchmaking.js');

var queues = [];
var users = [];
var teams = [];
var queueNum = 0;
var playerCollection = null;

const { MongoClient, ServerApiVersion } = require('mongodb');
const mongoClient = new MongoClient(config.httpserver.mongouri, {
  useNewUrlParser: true,
  useUnifiedTopology: true,
  serverApi: ServerApiVersion.v1,
});

var matchMakingUpdate = setInterval(() => {
  updateMatchmaking();
}, 2000);

function tryParseJSONObject(jsonString) {
  try {
    var obj = JSON.parse(jsonString);
    if (obj && typeof obj === 'object') {
      return obj;
    }
  } catch (err) {
    return false;
  }
}

function sendAll(sockets, response) {
  response = JSON.stringify(response);
  for (var socket of sockets) {
    //    console.log('Sending to ' + socket.player.name + '->', response);
    let lengthBytes = Buffer.alloc(2);
    lengthBytes.writeInt16BE(Buffer.byteLength(response, 'utf8'));
    socket.write(lengthBytes);
    socket.write(response);
  }
}

function safeSendAll(sockets, command, response) {
  return new Promise(function (resolve, reject) {
    if (sockets.length == 0) resolve(true);
    else {
      console.log(`Sending ${command} to ${sockets[0].player.name}`);
      sendCommand(sockets[0], command, response)
        .then(() => {
          sockets.shift();
          resolve(safeSendAll(sockets, command, response));
        })
        .catch(console.error);
    }
  });
}

function sendCommand(socket, command, response) {
  return new Promise(function (resolve, reject) {
    if (socket != undefined) {
      //  console.log(`Sending ${command} to ${socket.player.name}`);
      if (command == undefined && response == undefined) reject();
      var package = {
        cmd: command,
        payload: response,
      };
      console.log('Sent ', package);
      package = JSON.stringify(package);
      let lengthBytes = Buffer.alloc(2);
      lengthBytes.writeInt16BE(Buffer.byteLength(package, 'utf8'));
      socket.write(lengthBytes);
      socket.write(package, () => {
        //  console.log('Finished sending package to ', socket.player.name);
        resolve();
      });
    } else reject();
  });
}

function leaveQueue(socket,disconnected){
  if(socket != undefined && socket.player != undefined){
    console.log(`${socket.player.name} left queue!`);
  }else{
    console.log("SOCKET IS UNDEFINED AND TRIED TO LEAVE QUEUE");
    return;
  }
  switch(socket.player.stage){
    case 1: // IN QUEUE
      if(!config.lobbyserver.matchmakingEnabled){
        var usersInQueue = users.filter(u => u.player.teg_id != socket.player.teg_id && u.player.stage == 1 && u.player.queue.type == socket.player.queue.type);
        var size = usersInQueue.length > 3 ? 3 : usersInQueue.length;
        for(var u of usersInQueue){
          if(u.player.queue.visual != size){
            u.player.queue.visual = size;
            sendCommand(u,'queue_update',{'size':size}).catch(console.error);
          }
        }
      }
      break;
    case 2: // IN CHAMP SELECT
      var queue = queues.find(q => q.players.includes(socket.player.teg_id));
      if(queue != undefined) {
        for(var qp of queue.players){
          if(qp != socket.player.teg_id){
            var queuePlayer = users.find(u => u.player.teg_id == qp);
            if(queuePlayer != undefined){
              queuePlayer.player.queue = {
                type: null,
                started: -1,
                visual: 1
              };
              queuePlayer.player.stage = 0;
              sendCommand(queuePlayer,'team_disband',{'reason': 'error_lobby_playerLeftMatch'}).catch(console.error);
            }else console.log(`${qp} has no valid user object!`);
          }
        }
        queues.remove(queue);
      }
      break;
    case 3: // IN GAME
      var queue = queues.find(q => q.players.includes(socket.player.teg_id));
      if(queue != undefined){
        
      }
      break;
    default:
      console.log(`${socket.player.name} left queue while in lobby!`);
      break;
  }
  if(!disconnected){
    socket.player.queue = {
      type: null,
      started: -1,
      visual: 1,
    };
    socket.player.stage = 0;
  }
}

function leaveQueue(socket) {
  if (socket != undefined) console.log(socket.player.name + ' left queue');
  else console.log('Undefined socket left queue!');
  socket.player.queue.visual = 1;
  if (socket.player.queue.queueNum == -1) {
    console.log(`${socket.player.name} left matchmaking!`);
    //Not in a game/champ select
    var usersInQueue = users.filter(
      (user) =>
        user.player != socket.player &&
        user.player.queue.type != null &&
        user.player.queue.type == socket.player.queue.type &&
        user.player.queue.queueNum == -1
    );
    var size = usersInQueue.length;
    if (size > 3) size = 3;
    else
      safeSendAll(usersInQueue, 'queue_update', { size: size }).catch(
        console.error
      );
  } else {
    var queue = queues.find((q) => q.players.includes(socket.player));
    if (queue != undefined) {
      if (queue.inGame) {
        console.log(`${socket.player.name} left a game!`);
        queue.players = queue.players.filter((pl) => pl != socket.player);
        socket.player.team = -1;
        socket.player.queue.queueNum = -1;
      } else {
        console.log(`${socket.player.name} left champ select!`);
        safeSendAll(
          users.filter((u) => queue.players.includes(u.player) && u != socket),
          'team_disband',
          { reason: `error_lobby_playerLeftMatch` }
        )
          .then(() => {
            for (var p of queue.players) {
              p.queue.type = null;
              p.queue.started = -1;
              p.queue.queueNum = -1;
            }
            queue.players = [];
            queues = queues.filter((qu) => qu.players.length > 0);
          })
          .catch(console.error);
      }
    } else console.log('Left undefined queue!');
    queues = queues.filter((q) => q.players.length > 0);
  }
  socket.player.queue.type = null;
  socket.player.queue.started = -1;
  socket.player.queue.queueNum = -1;
  socket.player.team = -1;
  var invalidQueues = []; //Should clean up errors, if applicable, where queues contains incorrect players
  for (var q of queues) {
    var queuedUsers = users.filter(
      (u) => u.player.queue.queueNum == q.queueNum
    );
    if (queuedUsers.length == 0) {
      invalidQueues.push(q);
      console.log(`QUEUE ${q.queueNum} is invalid!`);
    }
  }
  queues = queues.filter((q) => !invalidQueues.includes(q));
}

function updateCustomGame(team) {
  if (team != undefined) {
    var dataToSend = {
      teamN: [],
      teamA: [],
      teamB: [],
      ready: team.players.length > 1,
    };
    for (var p of team.players) {
      var playerObj = {
        name: p.name,
        teg_id: p.teg_id,
      };
      if (p.team == 0) dataToSend.teamA.push(p);
      else if (p.team == 1) dataToSend.teamB.push(p);
      else dataToSend.teamN.push(p);
    }
    safeSendAll(
      users.filter((u) => team.players.includes(u.player)),
      'custom_game_update',
      dataToSend
    ).catch(console.error);
  }
}

function updateElo(socket) {
  if (playerCollection != null) {
    playerCollection
      .findOne({ 'user.TEGid': socket.player.teg_id })
      .then((res) => {
        if (res != null) {
          switch (res.player.elo + 1) {
            case 1:
            case 100:
            case 200:
            case 500:
              res.player.elo++;
              break;
          }
          socket.player.elo = res.player.elo;
        } else socket.close();
      })
      .catch((e) => {
        console.log(e);
        socket.close();
      });
  } else console.log('Player collection is not initialized!');
}

function updateMatchmaking() {
  if (users.length < 12) return;
  var types = [];
  for (var u of users) {
    if (
      u.player.queue.type != null &&
      u.player.queue.queueNum == -1 &&
      !types.includes(u.player.queue.type)
    )
      types.push(u.player.queue.type);
  }
  for (var t of types) {
    var maxQueueSize = 0;
    var queueSize = 1;
    if (t.includes('p') && t != 'practice')
      queueSize = Number(t.replace('p', ''));
    if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
    var usersInQueue = users.filter(
      (u) => u.player.queue.type == t && u.player.queue.queueNum == -1
    );
    var timeSort = function (a, b) {
      if (a.player.queue.started < b.player.queue.started) return -1;
      if (a.player.queue.started > b.player.queue.started) return 1;
      return 0;
    };
    usersInQueue.sort(timeSort);
    for (var u of usersInQueue) {
      var visualQueue = usersInQueue.length;
      if (visualQueue > 3) visualQueue = 3;
      if (u.player.queue.visual != visualQueue) {
        u.player.queue.visual = visualQueue;
        sendCommand(u, 'queue_update', { size: visualQueue }).catch(
          console.error
        );
        if (visualQueue == 3 && u.player.onTeam)
          sendCommand(u, 'team_full', { full: true }).catch(console.error);
      }
      var validQueuePlayers = [u.player];
      for (var u2 of usersInQueue) {
        if (u != u2 && !validQueuePlayers.includes(u2.player)) {
          if (
            Math.abs(u.player.elo - u2.player.elo) <
            50 + ((Date.now() - u2.player.queue.started) / 1000) * 12
          ) {
            if (u2.player.onTeam) {
              var team = teams.find((t) => t.players.includes(u2.player));
              if (
                team != undefined &&
                validQueuePlayers.length + team.players.length <= queueSize
              ) {
                for (var tp of team.players) {
                  if (
                    tp.player.queue != undefined &&
                    Math.abs(u.player.elo - tp.player.elo) <
                      50 +
                        ((Date.now() - tp.player.queue.started) / 1000) * 12 &&
                    !validQueuePlayers.includes(tp)
                  )
                    validQueuePlayers.push(tp);
                }
              } else leaveTeam(u2);
            } else validQueuePlayers.push(u2.player);
          }
        }
      }
      if (validQueuePlayers.length == queueSize) {
        startGame(validQueuePlayers, t);
        break;
      } else {
        if (maxQueueSize < validQueuePlayers.length)
          maxQueueSize = validQueuePlayers.length;
      }
    }
    console.log(
      `Matchmaking for ${t} has ${usersInQueue.length} players but could only match ${maxQueueSize} players`
    );
  }
}

function startGame(players, type) { //Note, custom games do not use this.
  var queueSize = 1;
  if (type.includes('p') && type != 'practice')
    queueSize = Number(type.replace('p', ''));
  if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
  var allTeams = matchmaking.getTeams(players,teams,queueSize/2);
  if(allTeams == undefined) return;
  var blue = allTeams.blue;
  var purple = allTeams.purple;
  var failed = false;
  var playerIds = [];
  for(var p of players){
    playerIds.push(p.teg_id);
  }

  var queueObj = {
    type: type,
    players: playerIds,
    queueNum: -1,
    blue: [],
    purple: [],
    ready: 0,
    max: queueSize,
    inGame: false,
  };

  for(var bp of blue){
    var user = users.find(u => u.player.teg_id == bp.teg_id);
    if(user == undefined){
      failed = true;
      console.log("GAME FAILED TO START DUE TO BLUE TEAM MEMBER " + bp);
      break;
    }else{
      var playerObj = {
        name: user.player.name,
        player: user.player.player,
        teg_id: `${user.player.teg_id}`,
        avatar: 'unassigned',
        is_ready: false,
      };
      queueObj.blue.push(playerObj);
    }
  }

  if(!failed){
    for(var pp of purple){
      var user = users.find(u => u.player.teg_id == pp.teg_id);
      if(user == undefined){
        console.log("GAME FAILED TO START DUE TO PURPLE TEAM MEMBER " + pp);
        return;
      }else{
        var playerObj = {
          name: user.player.name,
          player: user.player.player,
          teg_id: `${user.player.teg_id}`,
          avatar: 'unassigned',
          is_ready: false,
        };
        queueObj.purple.push(playerObj);
      }
    }
    queueObj.queueNum = queueNum;
    queueNum++;
    queues.push(queueObj);
    for(var p of players){
      var user = users.find(u => u.player.teg_id == p.teg_id);
      if(user != undefined){
        user.player.stage = 2;
      }
      var teamNum = queueObj.blue.find(bp => bp.teg_id == user.player.teg_id) == undefined ? 0 : 1;
      var gameData = {
        countdown: 60,
        ip: config.lobbyserver.gameIp,
        port: config.lobbyserver.gamePort,
        policy_port: config.sockpol.port,
        room_id: `GAME${queueObj.queueNum}_${type}`,
        password: '',
        team: teamNum == 0 ? "PURPLE" : "BLUE",
      };
      sendCommand(user,'game_ready',gameData).then(() => {
        var teamPackage = {
          'team': teamNum == 0 ? "PURPLE" : "BLUE",
          'players': teamNum == 0 ? queueObj.purple : queueObj.blue
        };
        sendCommand(user,'team_update',teamPackage).catch(console.error); //TODO: Fail the game for everyone if it catches.
      }).catch(console.error); //TODO: Fail the game for everyone if it catches.
    }
  }
}

function leaveTeam(socket) {
  console.log(socket.player.name + ' is leaving their team.');
  socket.player.onTeam = false;
  socket.player.team = -1;
  if (socket.player.queue.queueNum != -1 || socket.player.queue.started != -1)
    leaveQueue(socket);
  var team = teams.find((t) => t.players.includes(socket.player));
  if (team != undefined) {
    team.players = team.players.filter((p) => p.name != socket.player.name);
    if (socket.player == team.teamLeader) {
      safeSendAll(
        users.filter((u) => team.players.includes(u.player)),
        'team_disband',
        { reason: 'error_lobby_teamLeader' }
      ).catch(console.error);
      for (var user of users.filter((u) => team.players.includes(u.player))) {
        leaveTeam(user);
      }
      team.teamLeader = undefined;
    } else {
      if (team.type == 'custom') {
        updateCustomGame(team);
        socket.player.team = -1;
      } else {
        var teamObj = [];
        for (var p of team.players) {
          var playerObj = {
            name: p.name,
            teg_id: p.teg_id,
            player: p.player,
          };
          teamObj.push(playerObj);
        }
        safeSendAll(
          users.filter((u) => team.players.includes(u.player)),
          'team_update',
          { players: teamObj, team: team.team }
        ).catch(console.error);
        sendCommand(
          users.find((u) => u.player == team.teamLeader),
          'invite_declined',
          { player: socket.player.teg_id }
        ).catch(console.error);
      }
    }
    if (team.queueNum != -1) {
      //Should kick everyone out of queue
      leaveQueue(socket);
      team.queueNum = -1;
    }
    teams = teams.filter((t) => t.teamLeader != undefined);
  } else {
    leaveQueue(socket);
    console.log('No team found when leaving!');
  }
}

function joinQueue(sockets, type) {
  var queueSize = 1;
  if (type.includes('p') && type != 'practice')
    queueSize = Number(type.replace('p', ''));
  if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
  for(var s of sockets){
    s.player.stage = 1; //STAGE IS IN QUEUE
    s.player.queue.started = Date.now();
    s.player.queue.type = type;
  }
  if(!config.lobbyserver.matchmakingEnabled || queueSize == 1 || users.length < 18){
    /*
    var fakeUser1 = matchmaking.createFakeUser(true);
    var fakeUser2 = matchmaking.createFakeUser(true);
    users.push(fakeUser1);
    users.push(fakeUser2);
    teams.push(matchmaking.createFakeTeam([fakeUser1.player.teg_id,fakeUser2.player.teg_id]));
    users.push(matchmaking.createFakeUser(false));
    users.push(matchmaking.createFakeUser(false));
    users.push(matchmaking.createFakeUser(false));
    */
    var currentQueue = matchmaking.searchForFullGame(users.filter(u => u.player.queue.type == type && u.player.stage == 1),teams,queueSize);
    console.log(currentQueue);
    if(currentQueue.length == queueSize){
      startGame(currentQueue,type);
    }else{
      for(var u of users.filter(u => u.player.queue.type == type && u.player.stage == 1)){
        var updateNum = currentQueue.length;
        if(updateNum > 3) updateNum = 3;
        if(u.player.queue.visual != updateNum){
          u.player.queue.visual = updateNum;
          sendCommand(u,'queue_update',{size:updateNum}).catch(console.error);
        }
      }
    }
  }
}

function displayQueues() {
  console.log(':::QUEUES:::');
  for (var q of queues) {
    var blue = 0;
    var purple = 0;
    for (var p of q.players) {
      if (p.team == 0) purple++;
      else if (p.team == 1) blue++;
    }
    console.log(
      `QUEUE ${q.queueNum}: ${q.players.length} players, Type ${q.type}, In-Game ${q.inGame}, Blue: ${blue}, Purple: ${purple}`
    );
  }
  if (queues.length == 0) console.log('NO QUEUES');
}

function displayTeams() {
  console.log(':::TEAMS:::');
  for (var t of teams) {
    console.log(
      `${t.teamLeader.name}'s Team: ${t.players.length} members, In Queue: ${t.queueNum != -1}'`
    );
  }
  if (teams.length == 0) console.log('NO TEAMS');
}

function displayPlayers() {
  console.log(':::PLAYERS:::');
  for (var u of users) {
    console.log(
      `${u.player.name} in game: ${u.player.queue.queueNum != -1} | searching: ${u.player.queue.type != null} | onTeam: ${u.player.onTeam}`,
      u.player.queue
    );
  }
}

function cleanUpPlayers() {
  /*
  for (var t of teams) {
    var invalidTeamPlayers = [];
    for (var tp of t.players) {
      if (!tp.onTeam || users.find((u) => u.player == tp) == undefined) {
        invalidTeamPlayers.push(tp);
        console.log(`${tp.name} is an invalid team member!`);
      }
    }
    t.players = t.players.filter((tp) => !invalidTeamPlayers.includes(tp));
  }

  for (var q of queues) {
    var invalidQueuePlayers = [];
    for (var qp of q.players) {
      if (
        qp.queue.queueNum != q.queueNum ||
        users.find((u) => u.player == qp) == undefined
      ) {
        invalidQueuePlayers.push(qp);
        console.log(`${qp.name} is an invalid queue member!`);
      }
    }
    q.players = q.players.filter((qp) => !invalidQueuePlayers.includes(qp));
  }
  */
}

setInterval(() => {
  cleanUpPlayers();
  teams = teams.filter((t) => t.players.length > 0);
  queues = queues.filter((q) => q.players.length > 0);
  displayTeams();
  displayQueues();
  displayPlayers();
}, 30000);

// TODO: move out to separate file
function handleRequest(jsonString, socket) {
  let jsonObject = tryParseJSONObject(jsonString);
  if (!jsonObject) {
    return;
  }

  let response = null;
  let unhandled = false;
  //if (socket.player != undefined) console.log('!', socket.player.name);
  //console.log('<-', jsonObject['req'], jsonObject['payload']);

  switch (jsonObject['req']) {
    case 'handshake':
      response = {
        cmd: 'handshake',
        payload: {
          result: true,
        },
      };
      break;

    case 'login':
      response = {
        cmd: 'login',
        payload: {
          name: decodeURI(jsonObject['payload'].name),
          player: Number(jsonObject['payload'].auth_id),
          teg_id: jsonObject['payload'].teg_id,
        },
      };
      break;

    case 'auto_join':
      var act = jsonObject['payload'].act.split('_');
      var type = act[act.length - 1];
      joinQueue([socket], type);
      break;

    case 'leave_team':
      if (socket.player.onTeam) {
        leaveTeam(socket);
      } else {
        leaveQueue(socket);
      }
      break;

    case 'set_avatar':
      var queue = queues.find(q => q.players.includes(socket.player.teg_id));
      if(queue != undefined){
        if(queue.ready == queue.max) return;
        var blueMember = queue.blue.find(bp => bp.teg_id == socket.player.teg_id);
        var purpleMember = queue.purple.find(bp => bp.teg_id == socket.player.teg_id);
        var teamNum = purpleMember != undefined ? 0 : 1;
        var myTeam = teamNum == 0 ? queue.purple : queue.blue;
        var myUser = teamNum == 0 ? purpleMember : blueMember; // This sucks lmao
        if(myUser.is_ready) return;
        var sameCharacter = myTeam.find(tp => tp.avatar == jsonObject['payload'].name);
        if(sameCharacter == undefined){
          myUser.avatar = jsonObject['payload'].name;
          var teamPackage = {
            'team': teamNum == 0 ? "PURPLE" : "BLUE",
            'players': myTeam
          };
          safeSendAll(users.filter(u => myTeam.find(tp => tp.teg_id == u.player.teg_id) != undefined),'team_update',teamPackage).catch(console.error);
        }
      }else console.log(`${socket.player.name} has an undefined queue and tried to set avatar!`);
      break;

    case 'set_ready':
      var queue = queues.find(q => q.players.includes(socket.player.teg_id));
      if(queue != undefined){
        if(queue.ready == queue.max) return;
        var blueMember = queue.blue.find(bp => bp.teg_id == socket.player.teg_id);
        var purpleMember = queue.purple.find(bp => bp.teg_id == socket.player.teg_id);
        var teamNum = purpleMember != undefined ? 0 : 1;
        var myTeam = teamNum == 0 ? queue.purple : queue.blue;
        var myUser = teamNum == 0 ? purpleMember : blueMember; // This sucks lmao
        if(myUser.is_ready) return;
        myUser.is_ready = true;
        queue.ready++;
        var teamPackage = {
          'team': teamNum == 0 ? "PURPLE" : "BLUE",
          'players': myTeam
        };
        safeSendAll(users.filter(u => myTeam.find(tp => tp.teg_id == u.player.teg_id) != undefined),'team_update',teamPackage).catch(console.error);
        if(queue.ready == queue.max){
          queue.inGame = true;
          for(var qp of queue.players){
            var queueUser = users.find(u => u.player.teg_id == qp.teg_id);
            if(queueUser != undefined) queueUser.player.stage = 3;
          }
        }
      }else console.log(`${socket.player.name} has an undefined queue and tried to set ready!`);
      break;

    case 'chat_message':
      var user = users.find(
        (u) => u.player.teg_id == jsonObject['payload'].teg_id
      );
      if (user != undefined) {
        var player = user.player;
        var team = teams.find((t) => t.players.includes(player.teg_id));
        if (team != undefined && team.queueNum == -1) {
          safeSendAll(
            users.filter((u) => team.players.includes(u.player.teg_id)),
            'chat_message',
            {
              name: player.name,
              teg_id: player.teg_id,
              message_id: Number(jsonObject['payload'].message_id),
            }
          ).catch(console.error);
        } else {
          //console.log(`${player.name} is not on a team!`);
          var queue = queues.find(q => q.players.includes(player.teg_id));
          if (queue != undefined) {
            var myTeam = queue.purple.find(pp => pp.teg_id == player.teg_id) != undefined ? queue.purple : queue.blue;
            safeSendAll(users.filter(u => myTeam.find(tp => tp.teg_id == u.player.teg_id) != undefined),'chat_message', {
              name: player.name,
              teg_id: player.teg_id,
              message_id: Number(jsonObject['payload'].message_id)
            }).catch(console.error);
          }
        }
      } else console.log('User not found!');
      break;

    case 'custom_game_chat_message':
      var team = teams.find((t) => t.players.includes(socket.player));
      if (team != undefined) {
        safeSendAll(
          users.filter((u) => team.players.includes(u.player)),
          'custom_game_chat_message',
          {
            name: socket.player.name,
            teg_id: socket.player.teg_id,
            message_id: Number(jsonObject['payload'].message_id),
          }
        ).catch(console.error);
      }
      break;

    case 'create_team':
      var playerObj = {
        name: socket.player.name,
        teg_id: socket.player.teg_id,
        player: socket.player.player,
      };
      var teamObj = {
        teamLeader: socket.player,
        players: [socket.player],
        team: socket.player.teg_id,
        queueNum: -1,
        type: jsonObject['payload'].act,
      };
      response = {
        cmd: 'team_update',
        payload: {
          players: [playerObj],
          team: teamObj.team,
        },
      };
      socket.player.onTeam = true;
      teams = teams.filter((t) => t.teamLeader != socket.player);
      teams.push(teamObj);
      break;

    case 'send_invite':
      var invitedPlayer = users.find(
        (user) => user.player.teg_id == jsonObject['payload'].player
      );
      if (invitedPlayer != undefined) {
        var team = teams.find((t) => t.teamLeader == socket.player);
        if (team != undefined) {
          sendCommand(invitedPlayer, 'receive_invite', {
            name: socket.player.name,
            player: socket.player.player,
            act: team.type,
            vs: true,
            team: socket.player.teg_id,
          }).catch(console.error);
        } else console.log('Team Leader invalid!');
      } else console.log("Can't find player!");
      break;

    case 'join_team':
      var team = teams.find(
        (t) => t.teamLeader.teg_id == jsonObject['payload'].name
      );
      if (team != undefined && team.players.length < 3 && team.queueNum == -1) {
        socket.player.onTeam = true;

        team.players.push(socket.player);
        var teamObjs = [];
        for (var p of team.players) {
          var playerObj = {
            name: p.name,
            teg_id: p.teg_id,
            player: p.player,
          };
          teamObjs.push(playerObj);
        }
        response = {
          cmd: 'invite_verified',
          payload: {
            result: 'success',
          },
        };
        safeSendAll(
          users.filter((u) => team.players.includes(u.player)),
          'team_update',
          { players: teamObjs, team: team.team }
        )
          .then(() => {
            if (team.players.length == 3) {
              var act = team.type.split('_');
              var type = act[act.length - 1];
              if (team != undefined) {
                joinQueue(
                  users.filter((u) => team.players.includes(u.player)),
                  type
                );
              } else console.log("Can't unlock undefined team!");
            }
          })
          .catch(console.error);
      } else {
        response = {
          cmd: 'invite_verified',
          payload: {
            result: 'failed',
          },
        };
      }
      break;

    case 'decline_invite':
    case 'custom_game_decline_invite':
      var partyLeader = jsonObject['payload'].party_leader;
      if (partyLeader != undefined) {
        var teamLeader = users.find(
          (user) =>
            Math.abs(jsonObject['payload'].party_leader - user.player.player) <=
            500
        );
      }
      if (teamLeader != undefined) {
        var command = 'invite_declined';
        if (jsonObject['req'].includes('custom'))
          command = 'custom_game_' + command;
        sendCommand(teamLeader, command, {
          player: socket.player.teg_id,
        });
      } else {
        console.log('No leader found!');
      }
      break;

    case 'unlock_team':
      var team = teams.find((t) => t.team == jsonObject['payload'].team);
      var act = team.type.split('_');
      var type = act[act.length - 1];
      if (team != undefined) {
        joinQueue(
          users.filter((u) => team.players.includes(u.player)),
          type
        );
      } else console.log("Can't unlock undefined team!");
      break;

    case 'custom_game_create':
      var teamObj = {
        teamLeader: socket.player,
        players: [socket.player],
        team: socket.player.teg_id,
        queueNum: -1,
        type: 'custom',
      };
      teams = teams.filter((t) => t.teamLeader != socket.player);
      teams.push(teamObj);
      socket.player.onTeam = true;
      break;

    case 'custom_game_join_side':
      var team = teams.find(
        (t) => t.players.includes(socket.player) && t.type == 'custom'
      );
      var teamToJoin = -1;
      if (jsonObject['payload'].team == 'teamA') teamToJoin = 0;
      else if (jsonObject['payload'].team == 'teamB') teamToJoin = 1;
      socket.player.team = teamToJoin;
      updateCustomGame(team);
      break;

    case 'custom_game_send_invite':
      var invitedUser = users.find(
        (u) => u.player.teg_id == jsonObject['payload'].player
      );
      if (invitedUser != undefined) {
        var inviteObj = {
          name: socket.player.name,
          player: socket.player.player,
          act: 'm_moba_sports_6p_custom',
          vs: true,
          customGame: socket.player.teg_id,
        };
        sendCommand(invitedUser, 'custom_game_receive_invite', inviteObj).catch(
          console.error
        );
      } else console.log(`Could not find user to send custom game invite to.`);
      break;

    case 'custom_game_join':
      var team = teams.find((t) => t.team == jsonObject['payload'].name);
      if (team != undefined && team.players.length < 6 && team.queueNum == -1) {
        socket.player.onTeam = true;
        team.players.push(socket.player);
        sendCommand(socket, 'custom_game_invite_verified', {
          result: 'success',
        }).catch(console.error);
      } else
        response = {
          cmd: 'invite_verified',
          payload: {
            result: 'failed',
          },
        };
      break;

    case 'custom_game_start':
      var team = teams.find((t) => t.players.includes(socket.player));
      var blue = [];
      var purple = [];
      for (var p of team.players) {
        var playerObj = {
          name: p.name,
          player: p.player,
          teg_id: `${p.teg_id}`,
          avatar: 'unassigned',
          is_ready: false,
        };
        if (p.team == 0) purple.push(playerObj);
        else if (p.team == 1) blue.push(playerObj);
        else {
          p.onTeam = false;
          sendCommand(
            users.find((u) => u.player == p),
            'team_disband',
            { reason: 'error_send_room_fail' }
          ).catch(console.error);
        }
      }
      team.players = team.players.filter((p) => p.team != -1);
      var queueObj = {
        type: `custom_${team.players.length}p`,
        players: team.players,
        queueNum: queueNum,
        blue: blue,
        purple: purple,
        ready: 0,
        max: 6,
        inGame: false,
      };
      for (var p of team.players) {
        p.queue.queueNum = queueNum;
      }
      queueNum++;
      queues.push(queueObj);
      var gameDataPurple = {
        countdown: 60,
        ip: config.lobbyserver.gameIp,
        port: config.lobbyserver.gamePort,
        policy_port: config.sockpol.port,
        room_id: `GAME${queueObj.queueNum}_${queueObj.type}`,
        password: '',
        team: 'PURPLE',
      };
      var gameDataBlue = {
        countdown: 60,
        ip: config.lobbyserver.gameIp,
        port: config.lobbyserver.gamePort,
        policy_port: config.sockpol.port,
        room_id: `GAME${queueObj.queueNum}_${queueObj.type}`,
        password: '',
        team: 'BLUE',
      };
      safeSendAll(
        users.filter(
          (u) => team.players.includes(u.player) && u.player.team == 0
        ),
        'game_ready',
        gameDataPurple
      )
        .then(() => {
          safeSendAll(
            users.filter(
              (u) => team.players.includes(u.player) && u.player.team == 0
            ),
            'team_update',
            { players: queueObj.purple, team: 'PURPLE' }
          ).catch(console.error);
        })
        .catch(console.error);
      safeSendAll(
        users.filter(
          (u) => team.players.includes(u.player) && u.player.team == 1
        ),
        'game_ready',
        gameDataBlue
      )
        .then(() => {
          safeSendAll(
            users.filter(
              (u) => team.players.includes(u.player) && u.player.team == 1
            ),
            'team_update',
            { players: queueObj.blue, team: 'BLUE' }
          ).catch(console.error);
        })
        .catch(console.error);
      break;

    default:
      unhandled = true;
  }

  if (response) {
    //console.log('->', response['cmd'], response['payload']);
    if (response['cmd'] == 'login') {
      var existingUser = users.find(
        (u) => u.player.name == response['payload'].name
      );
      if (existingUser != undefined)
        users = users.filter((u) => u != existingUser);
      socket.player = {
        name: response['payload'].name,
        teg_id: response['payload'].teg_id,
        player: response['payload'].player,
        queue: {
          type: null,
          started: -1,
          visual: 1,
        },
        onTeam: false,
        elo: 0,
        stage: 0 //0 = IN LOBBY, 1 = SEARCHING FOR GAME, 2 = CHAMP SELECT, 3 = IN GAME
      };
      users.push(socket);
      updateElo(socket);
      console.log('Logged In ->', response['payload'].name);
    }
  }
  if (unhandled) {
    console.log('Unhandled request', jsonObject['req']);
  }

  return JSON.stringify(response);
}

module.exports = class ATBPLobbyServer {
  constructor(port) {
    this.port = port;
    this.server = null;
  }
  start(callback) {
    mongoClient.connect((mongoError) => {
      if (mongoError) {
        console.error('FATAL: MongoDB connect failed: ' + mongoError);
        process.exit(1);
      }
      playerCollection = mongoClient.db('openatbp').collection('players');
      this.server = net.createServer((socket) => {
        socket.setEncoding('utf8');

        socket.on('readable', () => {
          //TODO: Add error handlers
          let jsonLength = socket.read(2);
          if (jsonLength == null || 0) {
            if (socket.player != undefined) {
              console.log(
                `${socket.player.name} has had their socket destroyed due to jsonLength 0.`
              );
            }
            socket.destroy();
          } else {
            let packet = socket.read(jsonLength);
            let response = handleRequest(packet, socket);
            if (response != 'null' && response != undefined) {
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
          if (socket.player != undefined) {
            console.log(socket.player.name + ' had an error.');
            if (socket.player.onTeam) leaveTeam(socket);
            else leaveQueue(socket);
          }
          socket.destroy();
        });

        socket.on('close', (err) => {
          console.log(err);
          var userExists = false;
          for (var user of users) {
            if (user._readableState.ended || user == socket) {
              userExists = true;
              console.log(user.player.name + ' logged out');
              if (user.player.onTeam) leaveTeam(user);
              else leaveQueue(user);
            }
          }
          if (!userExists) console.log('Socket left with no player: ', socket);
          users = users.filter(
            (user) => !user._readableState.ended && user != socket
          );
        });
      });

      this.server.listen(this.port, () => {
        callback();
      });
    });
  }
  stop(callback) {
    if (this.server) this.server.close(callback());
  }
};
