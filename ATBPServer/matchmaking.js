var getSortedQueueMembers = function (players) {
  var timeSort = function (a, b) {
    if (a.player.queue.started < b.player.queue.started) return -1;
    if (a.player.queue.started > b.player.queue.started) return 1;
    return 0;
  };
  return players.sort(timeSort);
};

module.exports = {
  getSortedQueueMembers: getSortedQueueMembers,
  searchForFullGame: function (players, teams, queueSize) {
    if (players.length == 0) return [];
    var sortedPlayers = getSortedQueueMembers(players);
    var prioritizedPlayer = sortedPlayers[0];
    var team = 0;
    var playerTeam = teams.find((t) =>
      t.players.includes(prioritizedPlayer.player.teg_id)
    );
    var validPlayers = [];
    if (playerTeam != undefined) {
      for (var p of playerTeam.players) {
        //If player has a team, make sure they all get added to the game.
        var teamUser = players.find((u) => u.player.teg_id == p);
        if (teamUser != undefined) {
          var queueSearchObj = {
            teg_id: p,
            elo: teamUser.player.elo,
            team: 0,
          };
          validPlayers.push(queueSearchObj);
        }
      }
      team++;
    } else {
      var queueSearchObj = {
        teg_id: prioritizedPlayer.player.teg_id,
        elo: prioritizedPlayer.player.elo,
        team: -1,
      };
      validPlayers.push(queueSearchObj);
    }
    var failedTeam = undefined;
    for (var p of sortedPlayers) {
      //Search through all players searching for game type.
      if (
        validPlayers.find((vp) => vp.teg_id == p.player.teg_id) == undefined
      ) {
        //Make sure that the player is not already added to the game.
        var searchTeam = teams.find((t) => t.players.includes(p.player.teg_id));
        if (searchTeam != undefined && team < 2) {
          //Runs if the player is part of a team and two teams are not already in the game.
          if (searchTeam.length + validPlayers.length <= queueSize) {
            // Checks to see if the entire team can be added to the game.
            for (var tp of searchTeam.players) {
              //Adds all team members to the queue.
              var teamUser = players.find((u) => u.player.teg_id == p);
              if (teamUser != undefined) {
                var queueSearchObj = {
                  teg_id: tp,
                  elo: teamUser.player.elo,
                  team: team,
                };
                validPlayers.push(queueSearchObj);
              }
            }
            team++;
          } else if (failedTeam == undefined) failedTeam = searchTeam; //If there's no many, it'll mark the first team as a failed team and try to readd if all other attempts fail.
        } else if (searchTeam == undefined) {
          //Runs if the player does not have a team.
          var queueSearchObj = {
            teg_id: p.player.teg_id,
            elo: p.player.elo,
            team: -1,
          };
          validPlayers.push(queueSearchObj);
        }
      }
      if (validPlayers.length == queueSize) return validPlayers; //If there is a full team, return.
    }
    if (validPlayers.length < queueSize && failedTeam != undefined) {
      //If there is not enough players, but there's a failed team, try adding the two and seeing if it works out.
      var newPlayers = [validPlayers[0]];
      for (var p of validPlayers) {
        if (
          p != validPlayers[0] &&
          p.team != -1 &&
          p.team == validPlayers[0].team
        )
          newPlayers.push(p);
      }
      for (var p of failedTeam.players) {
        var teamUser = players.find((u) => u.player.teg_id == p);
        if (teamUser != undefined) {
          var queueSearchObj = {
            teg_id: p,
            elo: teamUser.player.elo,
            team: team,
          };
          validPlayers.push(queueSearchObj);
        }
      }
      for (var p of sortedPlayers) {
        if (
          !p.player.onTeam &&
          validPlayers.find((vp) => vp.teg_id == p.player.teg_id) == undefined
        ) {
          var queueSearchObj = {
            teg_id: p.player.teg_id,
            elo: p.player.elo,
            team: -1,
          };
          validPlayers.push(queueSearchObj);
        }
      }
    }
    return validPlayers;
  },
  getTeams: function (players, teams, teamSize) {
    var returnVal = {
      purple: [],
      blue: [],
    };
    if (players.length == 1) {
      returnVal.purple.push(players[0]);
      return returnVal;
    }
    for (var p of players) {
      if (p.team == 0) returnVal.purple.push(p);
      else if (p.team == 1) returnVal.blue.push(p);
    }
    var unassignedPlayers = players.filter((p) => p.team == -1);
    var eloSort = function (a, b) {
      if (a.elo > b.elo) return -1;
      if (a.elo < b.elo) return 1;
      return 0;
    };
    unassignedPlayers = unassignedPlayers.sort(eloSort);
    var minDiff = Infinity;
    var closestTeams = [];
    function findTeams(team1, team2, index) {
      if (index === unassignedPlayers.length) {
        // Calculate average Elo for each team
        if (team1.length === 0 || team2.length === 0) return;
        const avgElo1 =
          team1.reduce((sum, player) => sum + player.elo, 0) / teamSize;
        const avgElo2 =
          team2.reduce((sum, player) => sum + player.elo, 0) / teamSize;

        // Calculate difference in average Elo between teams
        const diff = Math.abs(avgElo1 - avgElo2);

        // Update closestTeams if this combination has a smaller difference
        if (diff < minDiff) {
          minDiff = diff;
          closestTeams = [team1.slice(), team2.slice()];
        }
        return;
      }
      // Try adding current player to team 1
      if (team1.length < teamSize) {
        findTeams([...team1, unassignedPlayers[index]], team2, index + 1);
      }

      // Try adding current player to team 2
      if (team2.length < teamSize) {
        findTeams(team1, [...team2, unassignedPlayers[index]], index + 1);
      }
    }
    findTeams([...returnVal.purple], [...returnVal.blue], 0);
    returnVal.purple = closestTeams[0] != undefined ? closestTeams[0] : [];
    returnVal.blue = closestTeams[1] != undefined ? closestTeams[1] : [];
    return returnVal;
  },
  createFakeUser: function (onTeam) {
    return {
      player: {
        name: `Fake User ${Math.random() * 100}`,
        teg_id: `fake-user-${Math.random() * 100}`,
        player: Math.floor(Math.random() * 10000),
        queue: {
          type: '6p',
          started: Date.now(),
          visual: 1,
        },
        onTeam: onTeam,
        elo: Math.floor(Math.random() * 1500),
        stage: 1,
        fake: true,
      },
    };
  },
  createFakeTeam: function (players) {
    return {
      players: players,
    };
  },
  getPlayerObject: function(user, team){
    return {
      teg_id: user.player.teg_id,
      elo: user.player.elo,
      team: team,
    };
  }
};
