const fs = require('fs').promises;

module.exports = {
  handleCrossDomain: function(){ // /crossdomain.xml
    return(`<?xml version="1.0" ?>
    <cross-domain-policy>
      <site-control permitted-cross-domain-policies="master-only"/>
      <allow-access-from domain="*"/>
      <allow-http-request-headers-from domain="*" headers="*"/>
    </cross-domain-policy>`);
  },
  handlePresent: function(){ // /service/presence/present
    return (JSON.stringify({}));
  },
  handleWhoAmI: function(data,collection){ // /service/authenticate/whoami?authToken={data.token} RETURNS displayName
    return new Promise(function(resolve, reject) {
      var authTokenSplit = data.split("=");
      var authToken = authTokenSplit[authTokenSplit.length-1];
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
      /*
      fs.readFile("/Users/0lies/Desktop/Blank ATBP/ATBP-web/htdocs/service/shop/inventory").then((data) => {
        resolve(data);
      }).catch((err) => {
        reject(err);
      });
      */
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
  handlePlayerFriends: function(){ // /service/presence/roster/{TEGiid} RETURNS friends list from db
    return new Promise(function(resolve, reject) {

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
