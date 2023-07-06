module.exports = {
  function createNewUser(username,authpass, collection){ //Creates new user in web server and database
    return new Promise((fulfill,reject) => {
      var playerNumber = "";
      var newNumber = 0;
      collection.findOne({"playerNum":"true"}).then((data) => { //AuthID correlates to user id. This grabs the new player id from the database and makes it uniform to at least 4 digits
        console.log(data);
        for(var i = 0; i <= 4-data.num.length; i++){
          if(i != 4-data.num.length){
            playerNumber+="0";
          }else{
            playerNumber+=(parseInt(data.num)+1)
            newNumber = parseInt(data.num)+1;
          }
        }
        console.log(playerNumber);
        collection.updateOne({"playerNum":"true"}, {$set: {num: `${newNumber}`}}, {upsert:true}).then(() => { //Tells the database that we have one new user
          console.log("Successfully updated!");
          var token = Math.random().toString(36).slice(2,10);
          var playerFile = {
            user: {
              "TEGid": username.replace("%20"," ").replace(" ", ""),
              "dname": `${username.replace("%20"," ")}`,
              "authid": `${playerNumber}`,
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
            inventory: [],
            authToken: token,
            friends: []
          };
          collection.insertOne(playerFile).then(() => { //Creates new user in the db
            fulfill(playerFile.user);
          }).catch((err) => {
            reject(err);
          });
        }).catch(console.error);
      }).catch(console.error);
    });
  }
}