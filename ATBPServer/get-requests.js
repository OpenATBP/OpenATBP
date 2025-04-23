const bcrypt = require('bcrypt');
const crypto = require('crypto');

module.exports = {
  handlePresent: function () {
    // /service/presence/present
    return JSON.stringify({});
  },
  handleWhoAmI: function (data, collection) {
    // /service/authenticate/whoami?authToken={data.token} RETURNS displayName
    return new Promise(function (resolve, reject) {
      var authTokenSplit = data.split('=');
      var authToken = data;
      collection
        .findOne({ 'session.token': authToken })
        .then((res) => {
          if (res != null)
            resolve(JSON.stringify({ displayName: res.user.dname }));
          else reject();
        })
        .catch((e) => {
          reject(e);
        });
    });
  },
  handleTournamentData: function (data, collection) {
    // /service/data/user/champions/tournament?authToken={data.token} Useless unless we do a tournament
    return new Promise(function (resolve, reject) {
      var authToken = data;
      collection
        .findOne({ 'session.token': authToken })
        .then((res) => {
          if (res != null && res.betaTester != undefined) {
            resolve(JSON.stringify({ eligible: res.betaTester }));
          } else if (res != null) resolve(JSON.stringify({ eligible: false }));
          else reject();
        })
        .catch((e) => {
          reject(e);
        });
    });
    return JSON.stringify({ eligible: true });
  },
  handleShop: function (shopData) {
    // /service/shop/inventory RETURNS all inventory data
    return JSON.stringify(shopData);
  },
  handleChampConfig: function () {
    // /service/data/config/champions/ Not sure if this is what it should be returning or not.
    return JSON.stringify({
      upperELO: 500.0,
      eloTier: ['0', '25', '75', '200'], //This changes the tiers that change your icon based on elo. 500 marks bronze and 2000 is burple
    });
  },
  handlePlayerInventory: function (token, collection) {
    // /service/shop/player?authToken={data.token} RETURNS player inventory from db
    return new Promise(function (resolve, reject) {
      collection
        .findOne({ 'session.token': token })
        .then((data) => {
          if (data != null) resolve(JSON.stringify(data.inventory));
        })
        .catch((err) => {
          reject(err);
        });
    });
  },
  handlePlayerChampions: function (data, collection) {
    // /service/data/user/champions/profile?authToken={data} RETURNS player info from db
    return new Promise(function (resolve, reject) {
      collection
        .findOne({ 'session.token': data })
        .then((dat) => {
          if (dat != null) {
            switch (dat.player.elo + 1) {
              case 0: //Should never happen
              case 25:
              case 75:
              case 200:
                dat.player.elo++;
                break;
              case 74:
                dat.player.elo += 2;
                break;
            }
            resolve(JSON.stringify(dat.player));
          }
        })
        .catch((err) => {
          reject(err);
        });
    });
  },
  handlePlayerFriends: function (username, onlinePlayers, friendsList) {
    // /service/presence/roster/{TEGiid} RETURNS friends list from db
    return new Promise(function (resolve, reject) {
      let friends = [];
      for (let p of onlinePlayers) {
        if (p.username != username) {
          let pfp = p.pfp || 'Default';
          let pfpPath = `assets/pfp/${pfp}.jpg`;
          friends.push({
            user_id: p.username,
            name: p.name,
            avatar: pfpPath,
            options: {
              location: p.location,
              game: 'ATBP',
              tier: p.tier,
              level: p.level,
              elo: p.elo,
            },
          });
        }
      }
      /*
      for (var name of friendsList) {
        for (var p of onlinePlayers) {
          if (p.username == name) {
            friends.push({
              user_id: name,
              name: p.name,
              avatar: 'assets/pfp/default.jpg',
              options: {
                location: p.location,
                game: 'ATBP',
                tier: p.tier,
                level: p.level,
                elo: p.elo,
              },
            });
            break;
          }
        }
      }
      */
      resolve(JSON.stringify({ roster: friends }));
    });
  },
  handleBrowserLogin: function (username, collection) {
    // /authenticate/user/{username} RETURNS username from database
    return new Promise(function (resolve, reject) {
      collection
        .findOne({ 'user.TEGid': { $regex: new RegExp(`^${username}$`, 'i') } })
        .then((data) => {
          resolve(data);
        })
        .catch((err) => {
          reject(err);
        });
    });
  },

  handleLogin: function (username, password, token, collection) {
    return new Promise(function (resolve, reject) {
      collection
        .findOne({ 'user.TEGid': { $regex: new RegExp(`^${username}$`, 'i') } })
        .then((user) => {
          if (user != null) {
            bcrypt.compare(password, user.user.authpass, (err, res) => {
              if (res) {
                var expireDate = Date.parse(user.session.expires_at);
                if (token != '' && Date.now() < expireDate.valueOf()) {
                  if (user.session.token == token) {
                    resolve(user);
                  } else reject();
                } else {
                  var newToken = `${crypto.randomUUID()}`;
                  var today = new Date();
                  today.setDate(today.getDate() + 1);
                  var newSession = {
                    token: newToken,
                    expires_at: today,
                    renewable: false,
                  };
                  const options = { upset: false };
                  const update = { $set: { session: newSession } };
                  user.session = newSession;
                  collection
                    .updateOne(
                      {
                        'user.TEGid': {
                          $regex: new RegExp(`^${username}$`, 'i'),
                        },
                      },
                      update,
                      options
                    )
                    .then((d) => {
                      resolve(user);
                    })
                    .catch(console.error);
                }
              } else {
                reject();
              }
            });
          } else reject('Null user');
        })
        .catch(console.error);
    });
  },
  handleFriendRequest: function (token, collection) {
    return new Promise(function (resolve, reject) {
      collection
        .findOne({ 'session.token': token })
        .then((u) => {
          if (u != null) {
            var openRequests = u.requests;
            if (openRequests != undefined) {
              var names = [];
              var errors = 0;
              for (var n of openRequests) {
                collection
                  .findOne({ 'user.TEGid': n })
                  .then((user) => {
                    if (user != null) {
                      names.push({
                        dname: user.user.dname,
                        username: user.user.TEGid,
                      });
                    } else errors++;
                    if (names.length + errors == openRequests.length) {
                      resolve(names);
                    }
                  })
                  .catch((err) => {
                    console.log(err);
                    errors++;
                  });
              }
              if (openRequests.length == 0) resolve([]);
            } else resolve([]);
          } else reject();
        })
        .catch((e) => {
          reject(e);
        });
    });
  },
};
