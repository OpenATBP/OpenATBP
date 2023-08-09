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
  handlePurchase: function(token,itemToPurchase,collection,shopData){ // /service/shop/purchase?authToken={token} PROVIDES authToken RETURNS success object
    return new Promise(function(resolve, reject) {
      try {
        const foundItem = shopData.find(item=>item.id === itemToPurchase);
        if (foundItem) {
          collection.updateOne({"authToken":token},{$inc: {"player.coins": foundItem.cost*-1}}).then(() => { //Subtracts the coins from the player
            collection.updateOne({"authToken":token},{ $push: {inventory: itemToPurchase}}).then(() => { //Adds the item to the inventory item
              resolve(JSON.stringify({"success":"true"}));
            })
          })
        } else {
          reject(new Error("Item not found"));
        }
      } 
      catch(err) {
        reject(err);
      }
    });    
  },
  handleFriendRequest: function(username, newFriend, collection){
    return new Promise(function(resolve, reject) {
      collection.updateOne({"authToken":username},{ $push: {friends: newFriend}}).then(() => {
        resolve(JSON.stringify({}));
      }).catch((err) => {
        reject(err);
      });
    });
  }
};
