const net = require('net');
const config = require('./config.js');

var queues = [];
var users = [];
var teams = [];
var queueNum = 0;

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
    console.log('Sending to ' + socket.player.name + '->', response);
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
  if (socket == undefined) return;
  console.log(`Sending ${command} to ${socket.player.name}`);
  return new Promise(function (resolve, reject) {
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
      console.log('Finished sending package to ', socket.player.name);
      resolve();
    });
  });
}

function leaveQueue(socket) {
  if (socket.player.queue.queueNum == -1) {
    //Not in a game/champ select
    socket.player.queue.type = null;
    socket.player.queue.started = -1;
    socket.player.queue.queueNum = -1;
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
        queue.players = queue.players.filter((pl) => pl != socket.player);
        socket.player.team = -1;
        socket.player.queue.queueNum = -1;
      } else {
        safeSendAll(
          users.filter((u) => queue.players.includes(u.player) && u != socket),
          'team_disband',
          { reason: 'error_lobby_playerLeftMatch' }
        )
          .then(() => {
            for (var u of users.filter((u) =>
              queue.players.includes(u.player)
            )) {
              u.player.team = -1;
              u.player.queue.type = null;
              u.player.queue.started = -1;
              u.player.queue.queueNum = -1;
            }
            queues = queues.filter((qu) => qu != queue);
          })
          .catch(console.error);
      }
    } else console.log('Left undefined queue!');
    queues = queues.filter((q) => q.players.length > 0);
  }
  socket.player.queue.type = null;
  socket.player.queue.started = -1;
  var invalidQueues = []; //Should clean up errors, if applicable, where queues contains incorrect players
  for (var q of queues) {
    var queuedUsers = users.filter(
      (u) => u.player.queue.queueNum == q.queueNum
    );
    console.log(`QUEUE ${q.queueNum} is invalid!`);
    if (queuedUsers.length == 0) invalidQueues.push(q);
  }
  queues = queues.filter((q) => !invalidQueues.includes(q));
  displayPlayers();
  displayQueues();
}

function leaveTeam(socket) {
  socket.player.onTeam = false;
  var team = teams.find((t) => t.players.includes(socket.player));
  if (team != undefined) {
    team.players = team.players.filter((p) => p.name != socket.player.name);
    console.log(team.players);
    if (socket.player == team.teamLeader) {
      safeSendAll(
        users.filter((u) => team.players.includes(u.player)),
        'team_disband',
        { reason: 'error_lobby_teamLeader' }
      ).catch(console.error);
      for (var p of team.players) {
        p.onTeam = false;
      }
      team.teamLeader = undefined;
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
  displayTeams();
  displayPlayers();
}

function joinQueue(sockets, type) {
  var queueSize = 1;
  if (type.includes('p') && type != 'practice')
    queueSize = Number(type.replace('p', ''));
  if (queueSize == 3) queueSize = 2; //Turns bots to 1v1
  for (var i = 0; i < sockets.length; i++) {
    var socket = sockets[i];
    socket.player.queue.type = type;
    socket.player.queue.started = Date.now();
    if (i + 1 == sockets.length) {
      var usersInQueue = users.filter(
        (u) =>
          u.player.queue.queueNum == -1 &&
          u.player.queue.type == type &&
          u.player.queue.started != -1
      );
      var timeSort = function (a, b) {
        if (a.started < b.started) return -1;
        if (a.started > b.started) return 1;
        return 0;
      };
      usersInQueue = usersInQueue.sort(timeSort); //Sorts by who has been in the queue longest
      if (usersInQueue.length >= queueSize) {
        //Runs if there's enough players queued up to maybe fill a game
        var players = [];
        for (var u of usersInQueue) {
          if (u.player.onTeam && !players.includes(u.player)) {
            //If player is on a team, make sure their teammates come with them
            var team = teams.find((t) => t.players.includes(u.player));
            if (team != undefined) {
              if (players.length + team.players.length <= queueSize) {
                for (var tp of team.players) {
                  if (!players.includes(tp)) players.push(tp);
                  else console.log(`${tp.name} is already in the game!`);
                }
              } else console.log('Too many players on team to fill');
            } else console.log('No team found!');
          } else if (!u.player.onTeam) {
            players.push(u.player);
          }
          if (players.length == queueSize) break;
        }
        if (players.length == queueSize) {
          for (var p of players) {
            console.log(`QUEUE POPPED. ${p.name} JOINED!`);
          }
          var blue = [];
          var purple = [];
          for (var p of players) {
            if (p.onTeam && p.team == -1) {
              var team = teams.find((t) => t.players.includes(p));
              if (team != undefined) {
                team.queueNum = queueNum;
                var teamToJoin = -1;
                if (purple.length + team.players.length <= queueSize / 2)
                  teamToJoin = 0;
                else if (blue.length + team.players.length <= queueSize / 2)
                  teamToJoin = 1;
                if (teamToJoin != -1) {
                  for (var pt of team.players) {
                    var playerObj = {
                      name: pt.name,
                      player: pt.player,
                      teg_id: `${pt.teg_id}`,
                      avatar: 'unassigned',
                      is_ready: false,
                    };
                    if (teamToJoin == 0) purple.push(playerObj);
                    else if (teamToJoin == 1) blue.push(playerObj);
                    players.find((pl) => pl == pt).team = teamToJoin;
                    console.log(pt.name + ' set to ' + teamToJoin);
                  }
                } else console.log("Team players can't join team!");
              }
            }
          }
          console.log(`BLUE TEAM: `, blue);
          console.log(`PURPLE TEAM: `, purple);
          for (var p of players.filter((pl) => pl.team == -1)) {
            console.log(`Putting ${p.name} onto a team...`);
            var playerObj = {
              name: p.name,
              player: p.player,
              teg_id: `${p.teg_id}`,
              avatar: 'unassigned',
              is_ready: false,
            };
            if (purple.length < queueSize / 2) {
              purple.push(playerObj);
              p.team = 0;
            } else if (blue.length < queueSize / 2) {
              blue.push(playerObj);
              p.team = 1;
            } else console.log("Can't place on team!");
          }
          var queueObj = {
            type: type,
            players: players,
            queueNum: queueNum,
            blue: blue,
            purple: purple,
            ready: 0,
            max: queueSize,
            inGame: false,
          };
          for (var p of players) {
            p.queue.queueNum = queueNum;
          }
          queueNum++;
          queues.push(queueObj);
          var gameData = {
            countdown: 60,
            ip: config.lobbyserver.gameIp,
            port: config.lobbyserver.gamePort,
            policy_port: config.sockpol.port,
            room_id: `GAME${queueObj.queueNum}_${type}`,
            password: '',
            team: 'PURPLE',
          };
          safeSendAll(
            users.filter(
              (u) => players.includes(u.player) && u.player.team == 0
            ),
            'game_ready',
            gameData
          )
            .then(() => {
              safeSendAll(
                users.filter(
                  (u) => players.includes(u.player) && u.player.team == 0
                ),
                'team_update',
                { players: queueObj.purple, team: 'PURPLE' }
              ).catch(console.error);
            })
            .catch(console.error);
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
              (u) => players.includes(u.player) && u.player.team == 1
            ),
            'game_ready',
            gameDataBlue
          )
            .then(() => {
              safeSendAll(
                users.filter(
                  (u) => players.includes(u.player) && u.player.team == 1
                ),
                'team_update',
                { players: queueObj.blue, team: 'BLUE' }
              ).catch(console.error);
            })
            .catch(console.error);
        } else console.log('Invalid queue size!');
      } else {
        var size = usersInQueue.length;
        if (size > 3) {
          size = 3;
          safeSendAll(
            usersInQueue.filter((u) => u.onTeam),
            'team_full',
            { full: true }
          ).catch(console.error);
        }
        safeSendAll(usersInQueue, 'queue_update', { size: size }).catch(
          console.error
        );
      }
    }
  }
  displayQueues();
  displayPlayers();
  displayTeams();
}

function displayQueues() {
  console.log(':::QUEUES:::');
  for (var q of queues) {
    var blue = 0;
    var purple = 0;
    for (var p of q.players) {
      if (p.team == 0) purple++;
      else blue++;
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

// TODO: move out to separate file
function handleRequest(jsonString, socket) {
  let jsonObject = tryParseJSONObject(jsonString);
  if (!jsonObject) {
    return;
  }

  let response = null;
  let unhandled = false;
  console.log('<-', jsonObject['req'], jsonObject['payload']);

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
      for (var q of queues) {
        if (q.queueNum == socket.player.queue.queueNum) {
          if (q.ready == q.max) break;
          if (socket.player.team == 0) {
            for (var pUser of q.purple) {
              if (pUser.name == socket.player.name) {
                if (pUser.is_ready) shouldSend = false;
                pUser.avatar = jsonObject['payload'].name;
                break;
              }
            }
            sendAll(
              users.filter(
                (user) =>
                  user.player.team == 0 &&
                  user.player.queue.queueNum == q.queueNum
              ),
              {
                cmd: 'team_update',
                payload: {
                  players: q.purple,
                  team: `PURPLE`,
                },
              }
            );
          } else {
            for (var pUser of q.blue) {
              if (pUser.name == socket.player.name) {
                if (pUser.is_ready) shouldSend = false;
                pUser.avatar = jsonObject['payload'].name;
                break;
              }
            }
            sendAll(
              users.filter(
                (user) =>
                  user.player.team == 1 &&
                  user.player.queue.queueNum == q.queueNum
              ),
              {
                cmd: 'team_update',
                payload: {
                  players: q.blue,
                  team: `BLUE`,
                },
              }
            );
          }
          break;
        }
      }
      break;

    case 'set_ready':
      for (var q of queues) {
        if (q.queueNum == socket.player.queue.queueNum) {
          var blueTrigger = false;
          var purpleTrigger = false;
          for (var pUser of q.purple) {
            if (pUser.name == socket.player.name) {
              pUser.is_ready = true;
              purpleTrigger = true;
              q.ready++;
              break;
            }
          }
          for (var pUser of q.blue) {
            if (pUser.name == socket.player.name) {
              pUser.is_ready = true;
              blueTrigger = true;
              q.ready++;
              break;
            }
          }
          if (purpleTrigger) {
            sendAll(
              users.filter(
                (user) =>
                  user.player.team == 0 &&
                  user.player.queue.queueNum == q.queueNum
              ),
              {
                cmd: 'team_update',
                payload: {
                  players: q.purple,
                  team: `PURPLE`,
                },
              }
            );
          } else if (blueTrigger) {
            sendAll(
              users.filter(
                (user) =>
                  user.player.team == 1 &&
                  user.player.queue.queueNum == q.queueNum
              ),
              {
                cmd: 'team_update',
                payload: {
                  players: q.blue,
                  team: `BLUE`,
                },
              }
            );
          }
          if (q.ready == q.max) {
            q.inGame = true;
            displayQueues();
            displayPlayers();
          }
          break;
        }
      }
      break;
    case 'chat_message':
      for (var q of queues) {
        var player = q.players.find(
          (p) => p.teg_id == jsonObject['payload'].teg_id
        );
        if (player != undefined) {
          for (var p of q.players) {
            var sock = users.find(
              (u) => u.player == p && u.player.team == player.team
            );
            if (sock != undefined)
              sendCommand(sock, 'chat_message', {
                name: player.name,
                teg_id: player.teg_id,
                message_id: Number(jsonObject['payload'].message_id),
              }).catch(console.error);
            else console.log('No socket found!');
          }
        }
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
      teams.push(teamObj);
      displayTeams();
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
      displayTeams();
      break;

    case 'decline_invite':
      var partyLeader = jsonObject['payload'].party_leader;
      if (partyLeader != undefined) {
        var teamLeader = users.find(
          (user) =>
            Math.abs(jsonObject['payload'].party_leader - user.player.player) <=
            500
        );
      } else console.log(jsonObject['payload']);
      if (teamLeader != undefined)
        sendCommand(teamLeader, 'invite_declined', {
          player: socket.player.teg_id,
        });
      else {
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
    default:
      unhandled = true;
  }

  if (response) {
    console.log('->', response['cmd'], response['payload']);
    if (response['cmd'] == 'login') {
      var existingUser = users.find(
        (u) => u.player.name == response['payload'].name
      );
      if (existingUser != undefined)
        users = users.filter((u) => u != existingUser);
      users.push(socket);
      socket.player = {
        name: response['payload'].name,
        teg_id: response['payload'].teg_id,
        player: response['payload'].player,
        queue: {
          type: null,
          queueNum: -1,
          started: -1,
        },
        team: -1,
        onTeam: false,
      };
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
    this.server = net.createServer((socket) => {
      socket.setEncoding('utf8');

      socket.on('readable', () => {
        //TODO: Add error handlers
        let jsonLength = socket.read(2);
        if (jsonLength == null || 0) {
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
        socket.destroy();
      });

      socket.on('close', (err) => {
        console.log(err);
        for (var user of users) {
          if (user._readableState.ended) {
            console.log(user.player.name + ' logged out');
            if (user.player.onTeam) leaveTeam(user);
            else leaveQueue(user);
          }
        }
        users = users.filter((user) => !user._readableState.ended);
        displayQueues();
      });
    });

    this.server.listen(this.port, () => {
      callback();
    });
  }
  stop(callback) {
    if (this.server) this.server.close(callback());
  }
};
