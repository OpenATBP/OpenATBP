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
    return (JSON.stringify({}));
  },
  handleNewUser: function(username,password,collection){
    return new Promise(function(resolve, reject) {
      
    });
  }
};
