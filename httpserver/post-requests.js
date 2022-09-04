const fs = require('fs').promises;

function generateRandomToken(){
  return(Math.random().toString(36).slice(2,10));
}

module.exports = {
  handleLogin: function(data,collection){ // /service/authenticate/login PROVIDES authToken [pid,TEGid,authid,authpass] RETURNS authToken.text={authToken}
    return new Promise(function(resolve, reject) {
      console.log("Auth ID: " + data.authToken.authid);
      collection.findOne({"user.authid": `${data.authToken.authid}`}).then((user) => {
        if(user != null){ //User exists
          resolve(JSON.stringify({"authToken": {"text": user.authToken}}));
        }else{ //User does not exist
          reject();
        }
      }).catch((err) => {
        reject(err);
      })
    });
  },
  handlePresent: function(data){ // /service/presence/present PROVIDES username, property, level, elo, location, displayName, game, and tier
    //console.log(data);
    return (JSON.stringify({}));
  },
  handleNewUser: function(username,password,collection){ //DEPRECATED
    return new Promise(function(resolve, reject) {

    });
  },
  handlePurchase: function(token,data,collection,shopCollection){ // /service.shop/purchase?authToken={token} PROVIDES authToken RETURNS success object
    return new Promise(function(resolve, reject) {
      shopCollection.findOne({"id":data}).then((itemInfo) => { //Finds the cost for the purchased item
        if(itemInfo != null){
          collection.updateOne({"authToken":token},{$inc: {"player.coins": itemInfo.cost*-1}}).then(() => { //Subtracts the coins from the player
            collection.updateOne({"authToken":token},{ $push: {inventory: data}}).then(() => { //Adds the item to the inventory item
              resolve(JSON.stringify({"success":"true"}));
            }).catch((err) => {
              reject(err);
            });
          }).catch((e) => {
            reject(e);
          });
        }
      }).catch((er) => {
        reject(er);
      });
    });
  }
};
