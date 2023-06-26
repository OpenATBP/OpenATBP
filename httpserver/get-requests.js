const fs = require('fs').promises;

module.exports = {
  handlePresent: function(){ // /service/presence/present
    return (JSON.stringify({}));
  },
  handleWhoAmI: function(data,collection){ // /service/authenticate/whoami?authToken={data.token} RETURNS displayName
    return new Promise(function(resolve, reject) {
      var authTokenSplit = data.split("=");
      var authToken = data;
      console.log("AuthToken: " + authToken);
      collection.findOne({"authToken":authToken}).then((res) => {
        if(res != null) resolve(JSON.stringify({"displayName":res.user.dname}));
        else reject();
      }).catch((e) => {
        reject(e);
      });
    });
  },
  handleTournamentData: function(data) { // /service/data/user/champions/tournament?authToken={data.token} Useless unless we do a tournament
    return (JSON.stringify({
    	"tournamentData": {}
    }));
  },
  handleShop: function(collection){ // /service/shop/inventory RETURNS all inventory data
    return new Promise(function(resolve, reject) {
      collection.find({}).toArray((e,res) => {
        if(e) reject(e);
        resolve(JSON.stringify(res));
      });
    });
  },
  handleChampConfig: function(){ // /service/data/config/champions/ Not sure if this is what it should be returning or not.
    return JSON.stringify({"upperELO": 2000.0,
    "eloTier": ["1", "100", "200", "500"] //This changes the tiers that change your icon based on elo. 500 marks bronze and 2000 is burple
    });
  },
  handlePlayerInventory: function(token,collection){ // /service/shop/player?authToken={data.token} RETURNS player inventory from db
    return new Promise(function(resolve, reject) {
      collection.findOne({"authToken":token}).then((data) => {
        if(data != null) resolve(JSON.stringify(data.inventory));
      }).catch((err) => {
        reject(err);
      });
    });
  },
  handlePlayerChampions: function(data,collection){ // /service/data/user/champions/profile?authToken={data} RETURNS player info from db
    return new Promise(function(resolve, reject) {
      collection.findOne({"authToken":data}).then((dat) => {
        if(dat != null) resolve(JSON.stringify(dat.player));
      }).catch((err) => {
        reject(err);
      });
    });
  },
  handlePlayerFriends: function(username, onlinePlayers, friendsList){ // /service/presence/roster/{TEGiid} RETURNS friends list from db
    return new Promise(function(resolve, reject) {
      var friends = []
        for(var name of friendsList){
          for(var p of onlinePlayers){
            if(p.username == name){
              friends.push({
                user_id: name,
                name: p.name,
                avatar: "lich.png",
                options: {
                  location: p.location,
                  game: "ATBP",
                  tier: p.tier,
                  level: p.level,
                  elo: p.elo
                }
              });
              break;
            }
          }
        }
        resolve(JSON.stringify({"roster":friends}));
    });
  },
  handleBrowserLogin: function(username,collection){ // /authenticate/user/{username} RETURNS username from database
    return new Promise(function(resolve, reject) {
      collection.findOne({"user.TEGid": username}).then((data) => {
        resolve(data);
      }).catch((err) => {
        reject(err);
      });
    });
  }
};
