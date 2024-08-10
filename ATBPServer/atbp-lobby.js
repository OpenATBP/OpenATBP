const net = require('net');
const config = require('./config.js');
const matchmaking = require('./matchmaking.js');

var queues = [];
var users = [];
var teams = [];
var pendingInvites = [];
var queueNum = 0;
var playerCollection = null;

const { MongoClient, ServerApiVersion } = require('mongodb');
const mongoClient = new MongoClient(config.httpserver.mongouri, {
  useNewUrlParser: true,
  useUnifiedTopology: true,
  serverApi: ServerApiVersion.v1,
});

var matchMakingUpdate = setInterval(() => {
  handleSkilledMatchmaking();
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
      //console.log(`Sending ${command} to ${sockets[0].player.name}`);
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
    if (socket != undefined && socket.player.fake == undefined) {
      if (command == undefined && response == undefined) reject();
      var package = {
        cmd: command,
        payload: response,
      };
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

function removeDuplicateQueues(ids, queueNum) {
  for (var id of ids) {
    var qs = queues.filter((q) => q.players.includes(id));
    for (var q of qs) {
      if (q.queueNum != queueNum) q.players = q.players.filter((qp) => q != id);
    }
  }

  queues = queues.filter((q) => q.players.length > 0);
}

function removeTeam(team) {
  //console.log(`Removing team ${team.team}`);
  for (var tp of team.players) {
    var teamPlayer = users.find((u) => u.player.teg_id == tp);
    if (teamPlayer != undefined) {
      teamPlayer.player.onTeam = false;
    }
  }
  teams = teams.filter((t) => t.team != team.team);
}

function leaveQueue(socket, disconnected) {
  if (socket == undefined || socket.player == undefined) {
    console.log('SOCKET IS UNDEFINED AND TRIED TO LEAVE QUEUE');
    return;
  }
  switch (socket.player.stage) {
    case 1: // IN QUEUE
      if (!config.lobbyserver.matchmakingEnabled || users.length < 24) {
        var usersInQueue = users.filter(
          (u) =>
            u.player.teg_id != socket.player.teg_id &&
            u.player.stage == 1 &&
            u.player.queue.type == socket.player.queue.type
        );
        var size = usersInQueue.length > 3 ? 3 : usersInQueue.length;
        for (var u of usersInQueue) {
          if (u.player.queue.visual != size) {
            u.player.queue.visual = size;
            sendCommand(u, 'queue_update', { size: size }).catch(console.error);
          }
        }
      }
      break;
    case 2: // IN CHAMP SELECT
      var queue = queues.find((q) => q.players.includes(socket.player.teg_id));
      if (queue != undefined) {
        for (var qp of queue.players) {
          if (qp != socket.player.teg_id) {
            var queuePlayer = users.find((u) => u.player.teg_id == qp);
            if (queuePlayer != undefined) {
              queuePlayer.player.queue = {
                type: null,
                started: -1,
                visual: 1,
              };
              queuePlayer.player.stage = 0;
              sendCommand(queuePlayer, 'team_disband', {
                reason: 'error_lobby_playerLeftMatch',
              }).catch(console.error);
            }
          }
        }
        queue.players = [];
        queues = queues.filter((q) => q.players.length > 0);
      }
      break;
    case 3: // IN GAME
      var queue = queues.find((q) => q.players.includes(socket.player.teg_id));
      if (queue != undefined) {
        queue.players = queue.players.filter((p) => p != socket.player.teg_id);
      }
      queues = queues.filter((q) => q.players.length > 0);
      break;
    default:
      console.log(`${socket.player.name} left queue while in lobby!`);
      break;
  }
  if (!disconnected) {
    socket.player.queue = {
      type: null,
      started: -1,
      visual: 1,
    };
    socket.player.stage = 0;
  }
}

function updateCustomGame(team, user, teamJoined) {
  //TODO: Make code NOT written by a three year old :(
  if (team != undefined) {
    var dataToSend = {
      teamN: [],
      teamA: [],
      teamB: [],
      ready: team.players.length > 1,
    };
    if (teamJoined == 0) {
      if (team.custom.teamA.length == 3) return;
    } else if (teamJoined == 1) {
      if (team.custom.teamB.length == 3) return;
    }
    var blankTeamN = [];
    var blankTeamA = [];
    var blankTeamB = [];
    for (var p of team.custom.teamN) {
      if (p != user.player.teg_id) blankTeamN.push(p);
    }
    for (var p of team.custom.teamA) {
      if (p != user.player.teg_id) blankTeamA.push(p);
    }
    for (var p of team.custom.teamB) {
      if (p != user.player.teg_id) blankTeamB.push(p);
    }
    if (teamJoined == 0) blankTeamA.push(user.player.teg_id);
    else if (teamJoined == 1) blankTeamB.push(user.player.teg_id);
    else if (teamJoined == -1) blankTeamN.push(user.player.teg_id);
    team.custom.teamA = blankTeamA;
    team.custom.teamB = blankTeamB;
    team.custom.teamN = blankTeamN;

    for (var p of blankTeamA) {
      var teamPlayer = users.find((u) => u.player.teg_id == p);
      if (teamPlayer != undefined) {
        var playerObj = {
          name: teamPlayer.player.name,
          teg_id: teamPlayer.player.teg_id,
        };
        dataToSend.teamA.push(playerObj);
      }
    }
    for (var p of blankTeamB) {
      var teamPlayer = users.find((u) => u.player.teg_id == p);
      if (teamPlayer != undefined) {
        var playerObj = {
          name: teamPlayer.player.name,
          teg_id: teamPlayer.player.teg_id,
        };
        dataToSend.teamB.push(playerObj);
      }
    }
    for (var p of blankTeamN) {
      var teamPlayer = users.find((u) => u.player.teg_id == p);
      if (teamPlayer != undefined) {
        var playerObj = {
          name: teamPlayer.player.name,
          teg_id: teamPlayer.player.teg_id,
        };
        dataToSend.teamN.push(playerObj);
      }
    }
    safeSendAll(
      users.filter((u) => team.players.includes(u.player.teg_id)),
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
            case 0:
            case 1149:
            case 1350:
            case 1602:
              res.player.elo++;
              break;
          }
          socket.player.elo = res.player.elo;
        } else socket.end();
      })
      .catch((e) => {
        console.log(e);
        socket.end();
      });
  } else console.log('Player collection is not initialized!');
}

function handleSkilledMatchmaking() {
  if (
    !config.lobbyserver.matchmakingEnabled ||
    users.length < config.lobbyserver.matchmakingMin
  )
    return;
  var types = [];
  for (var u of users) {
    if (u.player.queue.type != null && !types.includes(u.player.queue.type))
      types.push(u.player.queue.type);
  }
  for (var t of types) {
    var maxQueueSize = 0;
    var queueSize = 1;
    if (t.includes('p') && t != 'practice')
      queueSize = Number(t.replace('p', ''));
    if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
    var usersInQueue = users.filter(
      (u) => u.player.queue.type == t && u.player.stage == 1
    );
    console.log('USERS IN QUEUE: ' + usersInQueue.length);
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
      var validQueuePlayers = [u];
      if (u.player.onTeam) {
        var team = teams.find((t) => t.players.includes(u.player.teg_id));
        if (team != undefined) {
          for (var tp of team.players) {
            if (tp != u.player.teg_id) {
              var tu = users.find((userObj) => userObj.player.teg_id == tp);
              if (tu != undefined) validQueuePlayers.push(tu);
            }
          }
        }
      }
      var totalElo = 0;
      for (var qp of validQueuePlayers) {
        totalElo += qp.player.elo;
      }
      var averageElo = totalElo / validQueuePlayers.length;
      for (var u2 of usersInQueue) {
        if (!validQueuePlayers.includes(u2)) {
          var additionalUsers = [u2];
          if (u2.player.onTeam) {
            var team = teams.find((t) => t.players.includes(u2.player.teg_id));
            if (team != undefined) {
              for (var tp of team.players) {
                if (tp != u2.player.teg_id) {
                  var tu = users.find((userObj) => userObj.player.teg_id == tp);
                  if (tu != undefined && !validQueuePlayers.includes(tu))
                    additionalUsers.push(tu);
                }
              }
            }
          }
          var totalMoreElo = 0;
          for (var qp of additionalUsers) {
            totalMoreElo += qp.player.elo;
          }
          var averageMoreElo = totalMoreElo / additionalUsers.length;
          if (
            Math.abs(averageElo - averageMoreElo) <
              100 + ((Date.now() - u2.player.queue.started) / 1000) * 12 &&
            validQueuePlayers.length + additionalUsers.length <= queueSize
          ) {
            for (var au of additionalUsers) {
              validQueuePlayers.push(au);
            }
            totalElo = 0;
            for (var qp of validQueuePlayers) {
              totalElo += qp.player.elo;
            }
            averageElo = totalElo / validQueuePlayers.length;
            if (validQueuePlayers.length == queueSize) {
              var minElo = 10000;
              var maxElo = 0;
              var totalTotalElo = 0;
              for (var testPlayer of validQueuePlayers) {
                if (testPlayer.player.elo > maxElo)
                  maxElo = testPlayer.player.elo;
                if (testPlayer.player.elo < minElo)
                  minElo = testPlayer.player.elo;
                totalTotalElo += testPlayer.player.elo;
              }
              console.log('ELO DIFF ', Math.abs(averageElo - averageMoreElo));
              console.log(
                'ALLOWED DIFF ',
                100 + ((Date.now() - u2.player.queue.started) / 1000) * 12
              );
              console.log('MIN ELO ', minElo);
              console.log('MAX ELO ', maxElo);
              console.log(
                'AVERAGE ELO ',
                totalTotalElo / validQueuePlayers.length
              );
              startGame(validQueuePlayers, t); // TODO: Does not work right now because we are passing in users, not the user.player obj
              return;
            }
          }
        }
      }
    }
  }
}

function updateMatchmaking() {
  if (!config.lobbyserver.matchmakingEnabled) return;
  var types = [];
  for (var u of users) {
    if (u.player.queue.type != null && !types.includes(u.player.queue.type))
      types.push(u.player.queue.type);
  }
  for (var t of types) {
    var maxQueueSize = 0;
    var queueSize = 1;
    if (t.includes('p') && t != 'practice')
      queueSize = Number(t.replace('p', ''));
    if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
    var usersInQueue = users.filter(
      (u) => u.player.queue.type == t && u.player.stage == 1
    );
    console.log('USERS IN QUEUE: ' + usersInQueue.length);
    //console.log(usersInQueue);
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
              } else leaveTeam(u2, false);
            } else validQueuePlayers.push(u2.player);
          }
        }
      }
      if (validQueuePlayers.length == queueSize) {
        var minElo = 10000;
        var maxElo = 0;
        var totalElo = 0;
        for (var testPlayer of validQueuePlayers) {
          if (testPlayer.elo > maxElo) maxElo = testPlayer.elo;
          if (testPlayer.elo < minElo) minElo = testPlayer.elo;
          totalElo += testPlayer.elo;
        }
        console.log(validQueuePlayers);
        console.log('MIN ELO ', minElo);
        console.log('MAX ELO ', maxElo);
        console.log('AVERAGE ELO ', totalElo / validQueuePlayers.length);
        startGame(validQueuePlayers, t);
        break;
      } else {
        if (maxQueueSize < validQueuePlayers.length)
          maxQueueSize = validQueuePlayers.length;
      }
    }
  }
}

function startGame(players, type) {
  //Note, custom games do not use this.
  var queueSize = 1;
  if (type.includes('p') && type != 'practice')
    queueSize = Number(type.replace('p', ''));
  if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
  var allTeams = matchmaking.getTeams(players, teams, queueSize / 2);
  if (allTeams == undefined) return;
  var blue = allTeams.blue;
  var purple = allTeams.purple;
  var failed = false;
  var playerIds = [];
  for (var p of players) {
    playerIds.push(p.teg_id);
    var user = users.find((u) => u.player.teg_id == p.teg_id);
    if (user != undefined) {
      user.player.stage = 2;
      var t = teams.find((tm) => tm.players.includes(user.player.teg_id));
      if (t != undefined) removeTeam(t);
    }
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

  for (var bp of blue) {
    var user = users.find((u) => u.player.teg_id == bp.teg_id);
    if (user == undefined) {
      failed = true;
      break;
    } else {
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

  if (!failed) {
    for (var pp of purple) {
      var user = users.find((u) => u.player.teg_id == pp.teg_id);
      if (user == undefined) {
        return;
      } else {
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
    removeDuplicateQueues(playerIds, queueNum);
    queueNum++;
    queues.push(queueObj);
    var gameDataPurple = {
      countdown: 60,
      ip: config.lobbyserver.gameIp,
      port: config.lobbyserver.gamePort,
      policy_port: config.sockpol.port,
      room_id: `GAME${queueObj.queueNum}_${type}`,
      password: '',
      team: 'PURPLE',
    };
    var gameDataBlue = {
      countdown: 60,
      ip: config.lobbyserver.gameIp,
      port: config.lobbyserver.gamePort,
      policy_port: config.sockpol.port,
      room_id: `GAME${queueObj.queueNum}_${type}`,
      password: '',
      team: 'BLUE',
    };
    safeSendAll(
      users.filter(
        (u) => purple.find((p) => p.teg_id == u.player.teg_id) != undefined
      ),
      'game_ready',
      gameDataPurple
    )
      .then(() => {
        var teamPackagePurple = {
          team: 'PURPLE',
          players: queueObj.purple,
        };
        safeSendAll(
          users.filter(
            (u) => purple.find((p) => p.teg_id == u.player.teg_id) != undefined
          ),
          'team_update',
          teamPackagePurple
        ).catch(console.error);
      })
      .catch(console.error);

    safeSendAll(
      users.filter(
        (u) => blue.find((p) => p.teg_id == u.player.teg_id) != undefined
      ),
      'game_ready',
      gameDataBlue
    )
      .then(() => {
        var teamPackageBlue = {
          team: 'BLUE',
          players: queueObj.blue,
        };
        safeSendAll(
          users.filter(
            (u) => blue.find((p) => p.teg_id == u.player.teg_id) != undefined
          ),
          'team_update',
          teamPackageBlue
        ).catch(console.error);
      })
      .catch(console.error);
  }
}

function leaveTeam(socket, disconnected) {
  socket.player.onTeam = false;
  var team = teams.find((t) => t.players.includes(socket.player.teg_id));
  if (team != undefined) {
    declineInvite(team.team, socket.player.teg_id, team.type == 'custom');
    if (team.stage != 0) leaveQueue(socket, false);
    team.players = team.players.filter((tp) => tp != socket.player.teg_id);
    if (team.team == socket.player.teg_id && team.stage == 0) {
      safeSendAll(
        users.filter((u) => team.players.includes(u.player.teg_id)),
        'team_disband',
        { reason: 'error_lobby_teamLeader' }
      ).catch(console.error);
      teams = teams.filter((t) => t.team != socket.player.teg_id);
    } else if (team.team != socket.player.teg_id) {
      if (team.type == 'custom') {
        updateCustomGame(team, socket, -2);
      } else {
        var teamObj = [];
        for (var p of team.players) {
          var teamPlayer = users.find((u) => u.player.teg_id == p);
          var playerObj = {
            name: teamPlayer.name,
            teg_id: teamPlayer.teg_id,
            player: teamPlayer.player,
          };
          teamObj.push(playerObj);
        }
        safeSendAll(
          users.filter((u) => team.players.includes(u.player.teg_id)),
          'team_update',
          { players: teamObj, team: team.team }
        ).catch(console.error);
        sendCommand(
          users.find((u) => u.player.teg_id == team.team),
          'invite_declined',
          { player: socket.player.teg_id }
        ).catch(console.error);
      }
    }
  }
}

function joinQueue(sockets, type) {
  var queueSize = 1;
  if (type.includes('p') && type != 'practice')
    queueSize = Number(type.replace('p', ''));
  if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
  for (var s of sockets) {
    s.player.stage = 1; //STAGE IS IN QUEUE
    s.player.queue.started = Date.now();
    s.player.queue.type = type;
  }
  if (
    !config.lobbyserver.matchmakingEnabled ||
    queueSize == 1 ||
    users.length < config.lobbyserver.matchmakingMin
  ) {
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
    var currentQueue = matchmaking.searchForFullGame(
      users.filter((u) => u.player.queue.type == type && u.player.stage == 1),
      teams,
      queueSize
    );
    if (currentQueue.length == queueSize) {
      startGame(currentQueue, type);
    } else {
      for (var u of users.filter(
        (u) => u.player.queue.type == type && u.player.stage == 1
      )) {
        var updateNum = currentQueue.length;
        if (updateNum > 3) updateNum = 3;
        if (u.player.queue.visual != updateNum) {
          u.player.queue.visual = updateNum;
          sendCommand(u, 'queue_update', { size: updateNum }).catch(
            console.error
          );
        }
      }
    }
  }
}

function displayQueues() {
  console.log(':::QUEUES:::');
  for (var q of queues) {
    console.log(
      `QUEUE ${q.queueNum}: ${q.players.length} players, Type ${q.type}, In-Game ${q.inGame}`
    );
  }
  if (queues.length == 0) console.log('NO QUEUES');
}

function displayTeams() {
  console.log(':::TEAMS:::');
  for (var t of teams) {
    console.log(
      `${t.team}'s Team: ${t.players.length} members, Stage: ${t.stage}'`
    );
  }
  if (teams.length == 0) console.log('NO TEAMS');
}

function displayPlayers() {
  console.log(':::PLAYERS:::');
  for (var u of users) {
    console.log(
      `${u.player.name} stage: ${u.player.stage} | in game: ${u.player.stage == 3} | searching: ${u.player.queue.type != null} | onTeam: ${u.player.onTeam}`,
      u.player.queue
    );
  }
}

function cleanUpPlayers() {
  for (var t of teams) {
    var invalidTeamPlayers = [];
    for (var tp of t.players) {
      if (users.find((u) => u.player.teg_id == tp) == undefined) {
        invalidTeamPlayers.push(tp);
      }
    }
    t.players = t.players.filter((tp) => !invalidTeamPlayers.includes(tp));
  }

  for (var q of queues) {
    var invalidQueuePlayers = [];
    for (var qp of q.players) {
      var user = users.find((u) => u.player.teg_id == qp);
      if (user == undefined || (!user.player.inGame && user.player.stage < 2)) {
        invalidQueuePlayers.push(qp);
      }
    }
    q.players = q.players.filter((qp) => !invalidQueuePlayers.includes(qp));
  }
  teams = teams.filter((t) => t.players.length > 0);
  queues = queues.filter((q) => q.players.length > 0);
}

function sendInvite(sender, recipient, custom) {
  var senderUser = users.find((u) => u.player.teg_id == sender);
  var pendingInvite = pendingInvites.find(
    (i) => i.sender == sender && i.recipient == recipient
  );
  if (pendingInvite != undefined) declineInvite(sender, recipient, custom);
  var invitedPlayer = users.find((u) => u.player.teg_id == recipient);
  var team = teams.find((t) => t.team == sender);
  if (
    senderUser != undefined &&
    invitedPlayer != undefined &&
    team != undefined
  ) {
    if (!custom) {
      sendCommand(invitedPlayer, 'receive_invite', {
        name: senderUser.player.name,
        player: senderUser.player.player,
        act: team.type,
        vs: true,
        team: senderUser.player.teg_id,
      })
        .then(() => {
          pendingInvites.push({
            sender: sender,
            recipient: recipient,
            custom: custom,
          });
        })
        .catch(console.error);
    } else {
      var inviteObj = {
        name: senderUser.player.name,
        player: senderUser.player.player,
        act: 'm_moba_sports_6p_custom',
        vs: true,
        customGame: senderUser.player.teg_id,
      };
      sendCommand(invitedPlayer, 'custom_game_receive_invite', inviteObj)
        .then(() => {
          pendingInvites.push({
            sender: sender,
            recipient: recipient,
            custom: custom,
          });
        })
        .catch(console.error);
    }
  } else if (senderUser != undefined) {
    sendCommand(
      senderUser,
      custom ? 'custom_game_invite_declined' : 'invite_declined',
      { player: recipient }
    ).catch(console.error);
  }
}

function declineInvite(sender, recipient, custom) {
  var senderUser = users.find((u) => u.player.teg_id == sender);
  var recipientUser = users.find((u) => u.player.teg_id == recipient);
  pendingInvites = pendingInvites.filter(
    (i) => i.sender != sender || i.recipient != recipient
  );
  var command = custom ? 'custom_game_invite_declined' : 'invite_declined';
  if (senderUser != undefined) {
    sendCommand(senderUser, command, { player: recipient }).catch(
      console.error
    );
  }
}

function acceptInvite(sender, recipient, custom) {
  var pendingInvite = pendingInvites.find(
    (i) => i.sender == sender && i.recipient == recipient
  );
  if (pendingInvite != undefined) {
    var senderUser = users.find((u) => u.player.teg_id == sender);
    var recipientUser = users.find((u) => u.player.teg_id == recipient);
    if (senderUser != undefined && recipientUser != undefined) {
      var team = teams.find((t) => t.team == sender);
      if (team != undefined) {
        pendingInvites = pendingInvites.filter(
          (i) => i.recipient != recipient || i.sender != sender
        );
        declineAllInvites(recipient);
        if (!custom) {
          if (team.players.length < 3 && team.stage == 0) {
            recipientUser.player.onTeam = true;
            team.players.push(recipient);
            var teamObjs = [];
            for (var p of team.players) {
              var teamPlayer = users.find((u) => u.player.teg_id == p);
              if (teamPlayer != undefined) {
                var playerObj = {
                  name: teamPlayer.player.name,
                  teg_id: teamPlayer.player.teg_id,
                  player: teamPlayer.player.player,
                };
                teamObjs.push(playerObj);
              }
            }
            sendCommand(recipientUser, 'invite_verified', { result: 'success' })
              .then(() => {
                safeSendAll(
                  users.filter((u) => team.players.includes(u.player.teg_id)),
                  'team_update',
                  { players: teamObjs, team: team.team }
                )
                  .then(() => {
                    if (team.players.length == 3) {
                      var act = team.type.split('_');
                      var type = act[act.length - 1];
                      joinQueue(
                        users.filter((u) =>
                          team.players.includes(u.player.teg_id)
                        ),
                        type
                      );
                      cancelAllInvites(senderUser.player.teg_id);
                    }
                  })
                  .catch(console.error);
              })
              .catch(console.error);
          } else
            sendCommand(recipientUser, 'invite_verified', {
              result: 'failed',
            }).catch(console.error);
        } else {
          if (team.players.length < 6 && team.stage == 0) {
            recipientUser.player.onTeam = true;
            team.players.push(recipient);
            sendCommand(recipientUser, 'custom_game_invite_verified', {
              result: 'success',
            })
              .then(() => {
                updateCustomGame(team, recipientUser, -1);
              })
              .catch(console.error);
          } else {
            sendCommand(recipientUser, 'invite_verified', {
              result: 'failed',
            }).catch(console.error);
          }
        }
      }
    }
  } else declineInvite(sender, recipient, custom);
}

function declineAllInvites(recipient) {
  for (var i of pendingInvites.filter((inv) => inv.recipient == recipient)) {
    declineInvite(i.sender, recipient, i.custom);
  }
}

function cancelAllInvites(sender) {
  for (var i of pendingInvites.filter((inv) => inv.sender == sender)) {
    declineInvite(sender, i.recipient, i.custom);
  }
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
          name: decodeURIComponent(jsonObject['payload'].name),
          player: Number(jsonObject['payload'].auth_id),
          teg_id: jsonObject['payload'].teg_id,
        },
      };
      break;

    case 'auto_join':
      /*
      var fakeUsers = [];
      for(var i = 0; i < 18; i++){
        var fake = matchmaking.createFakeUser(false);
        users.push(fake);
        fakeUsers.push(fake);
      }
      fakeUsers[0].player.onTeam = true;
      fakeUsers[1].player.onTeam = true;
      fakeUsers[2].player.onTeam = true;
      fakeUsers[3].player.onTeam = true;
      fakeUsers[4].player.onTeam = true;
      var fakeTeam1 = [fakeUsers[0].player.teg_id,fakeUsers[1].player.teg_id,fakeUsers[2].player.teg_id];
      var fakeTeam2 = [fakeUsers[3].player.teg_id,fakeUsers[4].player.teg_id];
      teams.push(matchmaking.createFakeTeam(fakeTeam1));
      teams.push(matchmaking.createFakeTeam(fakeTeam2));
      */
      var act = jsonObject['payload'].act.split('_');
      var type = act[act.length - 1];
      for (var q of queues.filter((qu) =>
        qu.players.includes(socket.player.teg_id)
      )) {
        q.players = q.players.filter((qp) => qp != socket.player.teg_id);
      }
      queues = queues.filter((q) => q.players.length > 0);
      joinQueue([socket], type);
      declineAllInvites(socket.player.teg_id);
      break;

    case 'leave_team':
      if (socket.player.onTeam) {
        leaveTeam(socket, false);
      } else {
        leaveQueue(socket, false);
      }
      break;

    case 'set_avatar':
      var queue = queues.find((q) => q.players.includes(socket.player.teg_id));
      if (queue != undefined) {
        if (queue.ready == queue.max) return;
        var blueMember = queue.blue.find(
          (bp) => bp.teg_id == socket.player.teg_id
        );
        var purpleMember = queue.purple.find(
          (bp) => bp.teg_id == socket.player.teg_id
        );
        var teamNum = purpleMember != undefined ? 0 : 1;
        var myTeam = teamNum == 0 ? queue.purple : queue.blue;
        var myUser = teamNum == 0 ? purpleMember : blueMember; // This sucks lmao
        if (myUser.is_ready) return;
        var sameCharacter = myTeam.find(
          (tp) => tp.avatar == jsonObject['payload'].name
        );
        if (sameCharacter == undefined) {
          myUser.avatar = jsonObject['payload'].name;
          var teamPackage = {
            team: teamNum == 0 ? 'PURPLE' : 'BLUE',
            players: myTeam,
          };
          safeSendAll(
            users.filter(
              (u) =>
                myTeam.find((tp) => tp.teg_id == u.player.teg_id) != undefined
            ),
            'team_update',
            teamPackage
          ).catch(console.error);
        }
      } else
        console.log(
          `${socket.player.name} has an undefined queue and tried to set avatar!`
        );
      break;

    case 'set_ready':
      var queue = queues.find((q) => q.players.includes(socket.player.teg_id));
      if (queue != undefined) {
        if (queue.ready == queue.max) return;
        var blueMember = queue.blue.find(
          (bp) => bp.teg_id == socket.player.teg_id
        );
        var purpleMember = queue.purple.find(
          (bp) => bp.teg_id == socket.player.teg_id
        );
        var teamNum = purpleMember != undefined ? 0 : 1;
        var myTeam = teamNum == 0 ? queue.purple : queue.blue;
        var myUser = teamNum == 0 ? purpleMember : blueMember; // This sucks lmao
        if (myUser.is_ready) return;
        myUser.is_ready = true;
        queue.ready++;
        var teamPackage = {
          team: teamNum == 0 ? 'PURPLE' : 'BLUE',
          players: myTeam,
        };
        safeSendAll(
          users.filter(
            (u) =>
              myTeam.find((tp) => tp.teg_id == u.player.teg_id) != undefined
          ),
          'team_update',
          teamPackage
        ).catch(console.error);
        if (queue.ready == queue.max) {
          queue.inGame = true;
          for (var qp of queue.players) {
            var queueUser = users.find((u) => u.player.teg_id == qp);
            if (queueUser != undefined) queueUser.player.stage = 3;
          }
        }
      } else
        console.log(
          `${socket.player.name} has an undefined queue and tried to set ready!`
        );
      break;

    case 'chat_message':
      var user = users.find(
        (u) => u.player.teg_id == jsonObject['payload'].teg_id
      );
      if (user != undefined) {
        var player = user.player;
        var team = teams.find((t) => t.players.includes(player.teg_id));
        if (team != undefined && team.stage < 2) {
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
          var queue = queues.find((q) => q.players.includes(player.teg_id));
          if (queue != undefined) {
            var myTeam =
              queue.purple.find((pp) => pp.teg_id == player.teg_id) != undefined
                ? queue.purple
                : queue.blue;
            safeSendAll(
              users.filter(
                (u) =>
                  myTeam.find((tp) => tp.teg_id == u.player.teg_id) != undefined
              ),
              'chat_message',
              {
                name: player.name,
                teg_id: player.teg_id,
                message_id: Number(jsonObject['payload'].message_id),
              }
            ).catch(console.error);
          }
        }
      }
      break;

    case 'custom_game_chat_message':
      var team = teams.find((t) => t.players.includes(socket.player.teg_id));
      if (team != undefined) {
        safeSendAll(
          users.filter((u) => team.players.includes(u.player.teg_id)),
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
        team: socket.player.teg_id,
        players: [socket.player.teg_id],
        stage: 0, //STAGES: 0 IN TEAM BUILD, 1 IN QUEUE, 2 IN CHAMP SELECT, 3 IN GAME
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
      teams = teams.filter((t) => t.team != socket.player.teg_id);
      teams.push(teamObj);
      break;

    case 'send_invite':
      sendInvite(socket.player.teg_id, jsonObject['payload'].player, false);
      break;

    case 'join_team':
      acceptInvite(jsonObject['payload'].name, socket.player.teg_id, false);
      break;

    case 'decline_invite':
      var partyLeader = jsonObject['payload'].party_leader;
      if (partyLeader != undefined) {
        var teamLeader = users.find(
          (user) =>
            Math.abs(jsonObject['payload'].party_leader - user.player.player) <=
            500
        );
        if (teamLeader != undefined) {
          declineInvite(teamLeader.player.teg_id, socket.player.teg_id, false);
        }
      }
      break;
    case 'custom_game_decline_invite': //Redundant but oh well
      var partyLeader = jsonObject['payload'].party_leader;
      if (partyLeader != undefined) {
        var teamLeader = users.find(
          (user) =>
            Math.abs(jsonObject['payload'].party_leader - user.player.player) <=
            500
        );
        if (teamLeader != undefined) {
          declineInvite(teamLeader.player.teg_id, socket.player.teg_id, true);
        }
      }
      break;

    case 'unlock_team':
      var team = teams.find((t) => t.team == jsonObject['payload'].team);
      var act = team.type.split('_');
      var type = act[act.length - 1];
      if (team != undefined) {
        joinQueue(
          users.filter((u) => team.players.includes(u.player.teg_id)),
          type
        );
        cancelAllInvites(socket.player.teg_id);
        team.stage = 1;
      }
      break;

    case 'custom_game_create':
      var teamObj = {
        players: [socket.player.teg_id],
        team: socket.player.teg_id,
        type: 'custom',
        stage: 0,
        custom: {
          teamA: [],
          teamB: [],
          teamN: [],
        },
      };
      teams = teams.filter((t) => t.team != socket.player.teg_id);
      teams.push(teamObj);
      socket.player.onTeam = true;
      break;

    case 'custom_game_join_side':
      var team = teams.find(
        (t) => t.players.includes(socket.player.teg_id) && t.type == 'custom'
      );
      var teamToJoin = -1;
      if (jsonObject['payload'].team == 'teamA') teamToJoin = 0;
      else if (jsonObject['payload'].team == 'teamB') teamToJoin = 1;
      updateCustomGame(team, socket, teamToJoin);
      break;

    case 'custom_game_send_invite':
      sendInvite(socket.player.teg_id, jsonObject['payload'].player, true);
      break;

    case 'custom_game_join':
      acceptInvite(jsonObject['payload'].name, socket.player.teg_id, true);
      break;

    case 'custom_game_start':
      var team = teams.find((t) => t.players.includes(socket.player.teg_id));
      var blue = [];
      var purple = [];
      if (team != undefined) {
        cancelAllInvites(socket.player.teg_id);
        team.stage = 2;
        for (var pp of team.custom.teamA) {
          var teamUser = users.find((u) => u.player.teg_id == pp);
          if (teamUser != undefined) {
            var playerObj = {
              name: teamUser.player.name,
              player: teamUser.player.player,
              teg_id: `${teamUser.player.teg_id}`,
              avatar: 'unassigned',
              is_ready: false,
            };
            purple.push(playerObj);
          }
        }
        for (var bp of team.custom.teamB) {
          var teamUser = users.find((u) => u.player.teg_id == bp);
          if (teamUser != undefined) {
            var playerObj = {
              name: teamUser.player.name,
              player: teamUser.player.player,
              teg_id: `${teamUser.player.teg_id}`,
              avatar: 'unassigned',
              is_ready: false,
            };
            blue.push(playerObj);
          }
        }
        team.players = team.players.filter(
          (tp) => !team.custom.teamN.includes(tp)
        );
        var queueObj = {
          type: `custom_${team.players.length}p`,
          players: team.players,
          queueNum: queueNum,
          blue: blue,
          purple: purple,
          ready: 0,
          max: team.players.length,
          inGame: false,
        };
        queueNum++;
        removeDuplicateQueues(team.players, queueNum);
        queues.push(queueObj);
        for (var p of team.players) {
          var teamPlayer = users.find((u) => u.player.teg_id == p);
          if (teamPlayer != undefined) teamPlayer.player.stage = 2;
        }
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
        var purpleUsers = users.filter(
          (u) =>
            team.players.includes(u.player.teg_id) &&
            team.custom.teamA.includes(u.player.teg_id)
        );
        var blueUsers = users.filter(
          (u) =>
            team.players.includes(u.player.teg_id) &&
            team.custom.teamB.includes(u.player.teg_id)
        );
        safeSendAll(purpleUsers, 'game_ready', gameDataPurple)
          .then(() => {
            safeSendAll(
              users.filter(
                (u) =>
                  team.players.includes(u.player.teg_id) &&
                  team.custom.teamA.includes(u.player.teg_id)
              ),
              'team_update',
              { team: 'PURPLE', players: purple }
            ).catch(console.error);
          })
          .catch(console.error);
        safeSendAll(blueUsers, 'game_ready', gameDataBlue)
          .then(() => {
            safeSendAll(
              users.filter(
                (u) =>
                  team.players.includes(u.player.teg_id) &&
                  team.custom.teamB.includes(u.player.teg_id)
              ),
              'team_update',
              { team: 'BLUE', players: blue }
            ).catch(console.error);
          })
          .catch(console.error);
        removeTeam(team);
      }
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
        stage: 0, //0 = IN LOBBY, 1 = SEARCHING FOR GAME, 2 = CHAMP SELECT, 3 = IN GAME
      };
      users.push(socket);
      updateElo(socket);
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
      playerCollection = mongoClient.db('openatbp').collection('users');
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
            if (socket.player.onTeam) leaveTeam(socket, true);
            else leaveQueue(socket, true);
          }
          socket.destroy();
        });

        socket.on('close', (err) => {
          console.log(err);
          var userExists = false;
          for (var user of users) {
            if (user._readableState.ended || user == socket) {
              userExists = true;
              if (user.player.onTeam) leaveTeam(user, true);
              else leaveQueue(user, true);
            }
          }
          if (!userExists) console.log('Socket left with no player: ', socket);
          users = users.filter(
            (user) => !user._readableState.ended && user != socket
          );
        });

        socket.on('end', (err) => {
          console.log(err);
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
