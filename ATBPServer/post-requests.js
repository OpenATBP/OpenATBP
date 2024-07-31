function generateRandomToken() {
  return Math.random().toString(36).slice(2, 10);
}

const bcrypt = require('bcrypt');
const dbOp = require('./db-operations.js');
const crypto = require('crypto');

module.exports = {
  handleRegister: function (username, password, names, collection) {
    return new Promise(function (resolve, reject) {
      bcrypt.hash(password, 10, (err, hash) => {
        var name = '';
        for (var i in names) {
          name += names[i];
          if (i != names.length - 1 && names[i] != '') name += ' ';
        }
        collection
          .findOne({
            $or: [{ 'user.authid': username }, { 'user.dname': name }],
          })
          .then((user) => {
            if (user != null) {
              resolve('login');
            } else {
              dbOp
                .createNewUser(username, name, hash, collection)
                .then((u) => {
                  resolve(u);
                })
                .catch(console.error);
            }
          })
          .catch(console.error);
      });
    });
  },

  handleLogin: function (data, collection) {
    // /service/authenticate/login PROVIDES authToken [pid,TEGid,authid,authpass] RETURNS authToken.text={authToken}
    return new Promise(function (resolve, reject) {
      console.log('Auth ID: ' + data.authToken.authid);
      console.log('Auth Pass: ' + decodeURIComponent(data.authToken.authpass));
      console.log('TEGID: ' + data.authToken.TEGid);
      collection
        .findOne({
          'user.authid': `${data.authToken.authid}`,
          'user.authpass': `${decodeURIComponent(data.authToken.authpass)}`,
          'user.TEGid': `${data.authToken.TEGid}`,
        })
        .then((user) => {
          if (user != null) {
            //User exists
            resolve(
              JSON.stringify({ authToken: { text: user.session.token } })
            );
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
              { authToken: token },
              { $inc: { 'player.coins': foundItem.cost * -1 } }
            )
            .then(() => {
              //Subtracts the coins from the player
              collection
                .updateOne(
                  { authToken: token },
                  { $push: { inventory: itemToPurchase } }
                )
                .then(() => {
                  //Adds the item to the inventory item
                  resolve(JSON.stringify({ success: 'true' }));
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
          { authToken: username },
          { $addToSet: { friends: newFriend } }
        )
        .then(() => {
          resolve(JSON.stringify({}));
        })
        .catch((err) => {
          reject(err);
        });
    });
  },
  handleForgotPassword: function(username,dname,password,collection){
    return new Promise(function(resolve, reject) {
      collection.findOne({"user.authid":username,"user.dname":dname}).then((u) => {
        if(u != null){
          bcrypt.hash()
        }
      })
    });
  }
};
