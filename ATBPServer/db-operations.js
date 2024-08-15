const crypto = require('crypto');
const fs = require('node:fs');

var newUserFunction = function (
  username,
  displayName,
  authpass,
  forgot,
  collection
) {
  //Creates new user in web server and database
  return new Promise((fulfill, reject) => {
    var inventoryArray = [];
    fs.readFile('data/shop.json', (err, data) => {
      if (err) reject(err);
      else {
        var today = new Date();
        today.setDate(today.getDate() + 1);
        for (var item of JSON.parse(data)) {
          if (item.type == 'BACKPACK') inventoryArray.push(item.id);
        }
        if (displayName.charAt(displayName.length - 1) == ' ')
          displayName = displayName.substring(0, displayName.length - 1);
        var playerFile = {
          user: {
            TEGid: `${username.toLowerCase()}`,
            dname: `${displayName}`,
            authid: `${Math.floor(Math.random() * 1000000000)}`,
            authpass: `${authpass}`,
          },
          session: {
            token: `${crypto.randomUUID()}`,
            expires_at: today,
            renewable: false,
          },
          player: {
            playsPVP: 1.0,
            tier: 1.0,
            elo: 1150.0,
            disconnects: 0.0,
            playsBots: 0.0,
            rank: 1.0,
            rankProgress: 0.0,
            winsPVP: 0.0,
            winsBots: 0.0,
            points: 0.0,
            coins: 500,
            kills: 0,
            deaths: 0,
            assists: 0,
            towers: 0,
            minions: 0,
            jungleMobs: 0,
            altars: 0,
            largestSpree: 0,
            largestMulti: 0,
            scoreHighest: 0,
            scoreTotal: 0,
          },
          inventory: inventoryArray,
          friends: [],
          betaTester: false, //TODO: Remove when open beta starts
          forgot: forgot,
          requests: [],
        };
        const opt = { upsert: true };
        const update = { $set: playerFile };
        const filter = { 'user.TEGid': username };
        collection
          .updateOne(filter, update, opt)
          .then(() => {
            //Creates new user in the db
            fulfill(playerFile);
          })
          .catch((err) => {
            reject(err);
          });
      }
    });
  });
};

module.exports = {
  createNewUser: newUserFunction,
};
