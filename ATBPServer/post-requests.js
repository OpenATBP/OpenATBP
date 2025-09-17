function generateRandomToken() {
  return Math.random().toString(36).slice(2, 10);
}

const bcrypt = require('bcrypt');
const dbOp = require('./db-operations.js');
const crypto = require('crypto');
const config = require('./config.js');

function escapeRegex(string) {
  return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

module.exports = {
  handleRegister: function (
    username,
    password,
    selectedNameParts,
    forgot,
    collection
  ) {
    return new Promise(function (resolve, reject) {
      bcrypt.hash(password, 10, (err, hash) => {
        if (err) {
          console.error('Bcrypt password hashing error:', err);
          return reject(new Error('Password hashing failed'));
        }

        let validNameParts = selectedNameParts.filter(
          (n) => n && n.trim() !== ''
        );
        const finalDisplayNameForStorage = validNameParts.join(' ');

        if (validNameParts.length < 2) {
          return reject(new Error('Insufficient display name parts selected.'));
        }

        let baseDisplayNameForLookup = finalDisplayNameForStorage;

        const prefixRegex = /^(?:\[DEV\]\s*|\[ATBPDEV\]\s*)/i;
        baseDisplayNameForLookup = baseDisplayNameForLookup
          .replace(prefixRegex, '')
          .trim();

        if (!baseDisplayNameForLookup) {
          return reject(new Error('A valid core display name is required.'));
        }

        const escapedUsername = escapeRegex(username);
        const escapedBaseDisplayName = escapeRegex(baseDisplayNameForLookup);

        const displayNameConflictRegex = new RegExp(
          `^(?:${prefixRegex.source})?${escapedBaseDisplayName}$`,
          'i'
        );

        collection
          .findOne({
            $or: [
              {
                'user.TEGid': {
                  $regex: new RegExp(`^${escapedUsername}$`, 'i'),
                },
              },
              { 'user.dname': displayNameConflictRegex },
            ],
          })
          .then((existingUser) => {
            if (existingUser != null) {
              if (
                existingUser.user.TEGid.toLowerCase() === username.toLowerCase()
              ) {
                resolve('usernameTaken');
              } else {
                // If the username didn't match, the displayNameConflictRegex must have.
                resolve('displayNameTaken');
              }
            } else {
              bcrypt.hash(forgot, 10, (er, forgotHash) => {
                if (er) {
                  console.error('Bcrypt secret phrase hashing error:', er);
                  return reject(new Error('Secret phrase hashing failed'));
                }

                dbOp
                  .createNewUser(
                    username,
                    finalDisplayNameForStorage,
                    hash,
                    forgotHash,
                    collection
                  )
                  .then((u) => {
                    resolve(u);
                  })
                  .catch((dbErr) => {
                    console.error('Error in createNewUser:', dbErr);
                    reject(dbErr);
                  });
              });
            }
          })
          .catch((queryErr) => {
            console.error('Error in findOne user check:', queryErr);
            reject(queryErr);
          });
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
          collection
            .updateOne(
              {
                'session.token': token,
                'player.coins': { $gte: Number(foundItem.cost) },
                inventory: { $ne: itemToPurchase },
              },
              {
                $inc: { 'player.coins': foundItem.cost * -1 },
                $push: { inventory: itemToPurchase },
              }
            )
            .then((r) => {
              if (r.modifiedCount == 0) {
                resolve(JSON.stringify({ success: 'false' }));
              } else {
                resolve(JSON.stringify({ success: 'true' }));
              }
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
      const trimmedUsername = username ? username.trim() : '';
      if (!trimmedUsername) {
        return reject(new Error('invalidCredentials'));
      }
      const escapedUsername = escapeRegex(trimmedUsername);

      collection
        .findOne({
          'user.TEGid': { $regex: new RegExp(`^${escapedUsername}$`, 'i') },
        })
        .then((u) => {
          if (u != null && u.forgot) {
            bcrypt.compare(forgot, u.forgot, (err, secretPhraseMatch) => {
              if (err) {
                console.error('Bcrypt compare error for secret phrase:', err);
                return reject(new Error('serverError'));
              }
              if (secretPhraseMatch) {
                // Secret phrase matches
                bcrypt.hash(password, 10, (bcryptErr, newPasswordHash) => {
                  if (bcryptErr) {
                    console.error(
                      'Bcrypt hash error for new password:',
                      bcryptErr
                    );
                    return reject(new Error('serverError'));
                  }
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
                          $regex: new RegExp(`^${escapedUsername}$`, 'i'),
                        },
                      },
                      {
                        $set: {
                          session: newSession, // Resetting session on password change
                          'user.authpass': newPasswordHash,
                        },
                      }
                    )
                    .then((r) => {
                      if (
                        r.modifiedCount > 0 ||
                        (r.matchedCount > 0 && r.upsertedCount === 0)
                      ) {
                        resolve(u);
                      } else {
                        console.error(
                          'Password reset: User found but update failed for',
                          trimmedUsername
                        );
                        reject(new Error('serverError'));
                      }
                    })
                    .catch((dbUpdateErr) => {
                      console.error(
                        'DB update error during password reset:',
                        dbUpdateErr
                      );
                      reject(new Error('serverError'));
                    });
                });
              } else {
                // Secret phrase does not match
                reject(new Error('invalidCredentials'));
              }
            });
          } else {
            // Username isn't found or the user has no 'forgot' field (treat as invalid)
            reject(new Error('invalidCredentials'));
          }
        })
        .catch((findErr) => {
          console.error('DB findOne error during password reset:', findErr);
          reject(new Error('serverError'));
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
