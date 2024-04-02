const crypto = require('crypto');
const fs = require('node:fs');

var newUserFunction = function(username,displayName,authpass, collection){ //Creates new user in web server and database
  return new Promise((fulfill,reject) => {
    var token = Math.random().toString(36).slice(2,10);
    var inventoryArray = [];
    fs.readFile('data/shop.json',(err,data) => {
      if(err) reject(err);
      else{
        console.log(JSON.parse(data));
        for(var item of JSON.parse(data)){
          console.log(item);
          inventoryArray.push(item.id);
        }
        var playerFile = {
          user: {
            "TEGid": `${crypto.randomUUID()}`,
            "dname": `${displayName}`,
            "authid": `${username}`,
            "authpass": `${authpass}`
          },
          player: {
            "playsPVP": 1.0,
            "tier": 0.0,
            "elo": 0.0,
            "disconnects": 0.0,
            "playsBots": 0.0,
            "rank" :1.0,
            "rankProgress": 0.0,
            "winsPVP": 0.0,
            "winsBots": 0.0,
            "points": 0.0,
            "coins": 500,
            "kills": 0,
            "deaths": 0,
            "assists": 0,
            "towers": 0,
            "minions": 0,
            "jungleMobs": 0,
            "altars": 0,
            "largestSpree": 0,
            "largestMulti": 0,
            "scoreHighest": 0,
            "scoreTotal": 0
          },
          inventory: inventoryArray,
          authToken: token,
          friends: []
        };
        const opt = {upsert: true};
        const update = { $set: playerFile};
        const filter = {"user.authid":username};
        collection.updateOne(filter, update, opt).then(() => { //Creates new user in the db
          fulfill(playerFile.user);
        }).catch((err) => {
          reject(err);
        });
      }
    });
  });
};

module.exports = {
  createNewUser: newUserFunction,
  findDiscordId: function(clientId,collection){
    return new Promise(function(resolve, reject) {
      console.log("Client_ID: " + clientId);
      collection.findOne({"user.authid":clientId}).then((res) => {
        if(res != null){
          if(res.player == undefined){
            if(res.user != undefined){
              console.log("Creating new user!");
              newUserFunction(clientId,res.user.dname,'discord',collection).then((user) => {
                resolve(user);
              }).catch((e) => {
                reject(e);
              });
            }else reject();
          }else{
            console.log("User exists!");
            resolve(res.user);
          }
        }
        else reject();
      }).catch((e) => {
        reject(e);
      });
    });
  }
}
