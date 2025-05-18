function generateRandomToken() {
  return Math.random().toString(36).slice(2, 10);
}

const bcrypt = require('bcrypt');
const dbOp = require('./db-operations.js');
const crypto = require('crypto');
const config = require('./config.js');

module.exports = {
  handleRegister: function (username, password, names, forgot, collection) {
    return new Promise(function (resolve, reject) {
      bcrypt.hash(password, 10, (err, hash) => {
        var name = '';
        for (var i in names) {
          name += names[i];
          if (i != names.length - 1 && names[i] != '') name += ' ';
        }
        collection
          .findOne({
            $or: [
              { 'user.TEGid': { $regex: new RegExp(`^${username}$`, 'i') } },
              {
                'user.dname': name
                  .replace('[DEV] ', '')
                  .replace('[ATBPDEV] ', ''),
              },
            ],
          })
          .then((user) => {
            if (user != null) {
              resolve('login');
            } else {
              bcrypt.hash(forgot, 10, (er, has) => {
                dbOp
                  .createNewUser(username, name, hash, has, collection)
                  .then((u) => {
                    resolve(u);
                  })
                  .catch(console.error);
              });
            }
          })
          .catch(console.error);
      });
    });
  },

  handleLogin: function (data, token, collection) {
    // /service/authenticate/login PROVIDES authToken [pid,TEGid,authid,authpass] RETURNS authToken.text={authToken}
    return new Promise(function (resolve, reject) {
      collection
        .findOne({
          'user.authid': `${data.authToken.authid}`,
          'user.authpass': `${decodeURIComponent(data.authToken.authpass)}`,
          'user.TEGid': `${data.authToken.TEGid}`,
          'session.token': token,
        })
        .then((user) => {
          if (user != null) {
            //User exists
            if (
              (config.lobbyserver.earlyAccessOnly && user.earlyAccess) ||
              !config.lobbyserver.earlyAccessOnly
            )
              resolve(
                JSON.stringify({ authToken: { text: user.session.token } })
              );
            else reject();
          } else {
            //User does not exist
            reject();
          }
        })
        .catch((err) => {
          reject(err);
        });
    });
  },
  handlePresent: function (data) {
    // /service/presence/present PROVIDES username, property, level, elo, location, displayName, game, and tier
    //console.log(data);
    return JSON.stringify({});
  },
  handlePurchase: function (token, itemToPurchase, collection, shopData) {
    // /service/shop/purchase?authToken={token} PROVIDES authToken RETURNS success object
    return new Promise(function (resolve, reject) {
      try {
        const foundItem = shopData.find((item) => item.id === itemToPurchase);
        if (foundItem) {
          //TODO: This could be simplified
          collection
            .updateOne(
              { 'session.token': token },
              { $inc: { 'player.coins': foundItem.cost * -1 } }
            )
            .then(() => {
              //Subtracts the coins from the player
              collection
                .updateOne(
                  { 'session.token': token },
                  { $push: { inventory: itemToPurchase } }
                )
                .then((r) => {
                  if (r.modifiedCount == 0) {
                    resolve(JSON.stringify({ success: 'false' }));
                  } else {
                    resolve(JSON.stringify({ success: 'true' }));
                  }
                });
            });
        } else {
          reject(new Error('Item not found'));
        }
      } catch (err) {
        reject(err);
      }
    });
  },
  handleFriendRequest: function (username, newFriend, collection) {
    return new Promise(function (resolve, reject) {
      collection
        .updateOne(
          { 'user.TEGid': { $regex: new RegExp(`^${newFriend}$`, 'i') } },
          { $addToSet: { requests: username } }
        )
        .then(() => {
          resolve(JSON.stringify({}));
        })
        .catch((err) => {
          reject(err);
        });
    });
  },
  handleForgotPassword: function (username, forgot, password, collection) {
    return new Promise(function (resolve, reject) {
      collection
        .findOne({ 'user.TEGid': { $regex: new RegExp(`^${username}$`, 'i') } })
        .then((u) => {
          if (u != null) {
            bcrypt.compare(forgot, u.forgot, (err, res) => {
              if (res) {
                bcrypt.hash(password, 10, (err, hash) => {
                  var today = new Date();
                  today.setDate(today.getDate() + 1);
                  var newSession = {
                    token: `${crypto.randomUUID()}`,
                    expires_at: today,
                    renewable: false,
                  };
                  collection
                    .updateOne(
                      {
                        'user.TEGid': {
                          $regex: new RegExp(`^${username}$`, 'i'),
                        },
                      },
                      {
                        $set: {
                          session: newSession,
                          'user.authpass': hash,
                        },
                      }
                    )
                    .then((r) => {
                      resolve(u);
                    })
                    .catch((e) => {
                      reject(e);
                    });
                });
              } else reject('No user found');
            });
          } else reject('Null');
        })
        .catch((e) => {
          console.log(e);
          reject();
        });
    });
  },
  handleAcceptFriend: function (token, friend, collection) {
    return new Promise(function (resolve, reject) {
      collection.findOne({ 'session.token': token }).then((u) => {
        if (u != null) {
          var requests = u.requests;
          if (requests != undefined) {
            if (requests.includes(friend)) {
              collection
                .updateOne(
                  { 'session.token': token },
                  {
                    $addToSet: { friends: friend },
                    $pull: { requests: friend },
                  }
                )
                .then((res) => {
                  collection
                    .updateOne(
                      { 'user.TEGid': friend },
                      {
                        $addToSet: { friends: u.user.TEGid },
                        $pull: { requests: u.user.TEGid },
                      }
                    )
                    .then((r) => {
                      resolve(r);
                    })
                    .catch((e) => {
                      console.log(e);
                      reject();
                    });
                })
                .catch((e) => {
                  console.log(e);
                  reject();
                });
            }
          }
        } else reject();
      });
    });
  },
  handleDeclineFriend: function (token, friend, collection) {
    return new Promise(function (resolve, reject) {
      collection
        .updateOne({ 'session.token': token }, { $pull: { requests: friend } })
        .then(() => {
          resolve();
        })
        .catch((e) => {
          reject();
        });
    });
  },
};
