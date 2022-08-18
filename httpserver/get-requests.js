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
  handleWhoAmI: function(data){ // /service/authenticate/whoami?authToken={data.token} RETURNS displayName
    return (JSON.stringify({"displayName": "Spooky Umbrella"}));
  },
  handleTournamentData: function(data) { // /service/data/user/champions/tournament?authToken={data.token} Useless unless we do a tournament
    return (JSON.stringify({
    	"tournamentData": {}
    }));
  },
  handleShop: function(){ // /service/shop/inventory RETURNS all inventory data
    return new Promise(function(resolve, reject) {
      fs.readFile("/Users/0lies/Desktop/Blank ATBP/ATBP-web/htdocs/service/shop/inventory").then((data) => {
        resolve(data);
      }).catch((err) => {
        reject(err);
      });
    });
  },
  handleChampConfig: function(){ // /service/data/config/champions/ Not sure if this is what it should be returning or not.
    return JSON.stringify({
    	"upperELO": 2000,
    	"eloTier": ["scrub", "gamer", "pro gamer"]
    });
  },
  handlePlayerInventory: function(){ // /service/shop/player?authToken={data.token} RETURNS player inventory from db
    return new Promise(function(resolve, reject) {

    });
  },
  handlePlayerChampions: function(){ // /service/data/user/champions/profile?authToken={data.token} Not sure what this should return
    return new Promise(function(resolve, reject) {

    });
  },
  handlePlayerFriends: function(){ // /service/presence/roster/{TEGiid} RETURNS friends list from db
    return new Promise(function(resolve, reject) {

    });
  }
};
