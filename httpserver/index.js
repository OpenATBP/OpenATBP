const http = require('http');
const fs = require('fs').promises;

const host = 'localhost';
const port = 8001;

const secrets = require('./mongosecrets.js');

const { MongoClient, ServerApiVersion } = require('mongodb');
const uri = secrets.uri;
console.log(uri);
const client = new MongoClient(uri, { useNewUrlParser: true, useUnifiedTopology: true, serverApi: ServerApiVersion.v1 });

function createNewUser(username,authpass){ //Creates new user in web server and database
  return new Promise((fulfill,reject) => {
    const collection = client.db("openatbp").collection("players");
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
        var playerFile = {
          user: {
            "TEGid": username.replace("%20"," ").replace(" ", ""),
            "dname": `${username.replace("%20"," ")}`,
            "authid": `${playerNumber}`,
            "authpass": `${authpass}`
          },
          player: {
            "disconnects": 0,
            "playsPVP": 0,
            "winsPVP": 0,
            "playsBots": 0,
            "winsBots": 0,
            "elo": 1.5,
            "tier": 1,
            "rank": 3.0,
            "points": 0,
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
            "scoreTotal": 0,
            "rankProgress": "0.5",
            "gameLog": []
          }
        };
        collection.insertOne(playerFile).then(() => { //Writes displayName file for the user
          fs.writeFile(`/Users/0lies/Desktop/Blank ATBP/ATBP-web/htdocs/service/authenticate/whoami`,JSON.stringify({"displayName":playerFile.user.dname})).then(() => {
            fulfill(playerFile.user);
          }).catch((er) => {
            reject(er);
          });
        }).catch((err) => {
          reject(err);
        });
      }).catch(console.error);
    }).catch(console.error);
  });
}

client.connect(err => {
  const requestListener = function(req,res) {
    //console.log(Object.keys(req));
    //console.log(res);
    res.writeHead(200, { //This is what requests are allowed to go through
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'OPTIONS, POST, GET',
      'Access-Control-Max-Age': 2592000
    });

    if(req.method == "POST"){ //Handles new user request
      if(req.url.includes("/authenticate/user")){
        var userNameSplit = req.url.split("/");
        var userName = userNameSplit[userNameSplit.length-1];
        var body = "";
        req.on("data", (data) => {
          body += data;
        });

        req.on('end', () => { //When finished reading the data, it will create a new user.
          console.log(JSON.parse(body));
          createNewUser(userName,JSON.parse(body).password).then((user) => {
            res.end(JSON.stringify(user));
          })
        });
      }else{ //DEPRECATED
        var body = "";
        req.on("data", (data) => {
          body += data;
        });

        req.on('end', () => { //When finished reading the data, it will write to the file listed by the POST request. This is to my PC but will eventually go to the server.
          console.log(JSON.parse(body));
          fs.writeFile(`/Users/0lies/Desktop/Blank ATBP/ATBP-web/htdocs${req.url}`,JSON.stringify(JSON.parse(body))).then(() => {
            res.end(JSON.stringify({"test": "Working!"}));
          }).catch((e) => {
            console.log(e);
          });
        });
    }
  }else if(req.method == "GET"){ //Handles web request for user information when logging in
      if(req.url.includes("/authenticate/user")){
        var userNameSplit = req.url.split("/");
        var userName = userNameSplit[userNameSplit.length-1];
      }
        //console.log(JSON.parse(body));
        const collection = client.db("openatbp").collection("players");
        var filter = {"user.TEGid": userName.replace("%20","")};
        collection.findOne(filter).then((data) => {
          if(data != null){ //Check to see if user exists in the database
            fs.writeFile(`/Users/0lies/Desktop/Blank ATBP/ATBP-web/htdocs/service/authenticate/whoami`,JSON.stringify({"displayName":data.user.dname})).then(() => {
              res.end(JSON.stringify(data.user));
            }).catch(console.error);
          }else{
            console.log("No user exists!");
            res.end(JSON.stringify({"user": "null"}));
          }
        }).catch(console.error);

    }
     //Doesn't seem to matter right now. Just resolves the request.
  }

  const server = http.createServer(requestListener)

  server.listen(port,host, () => {
    console.log("Server is running!");
  });
  /*

  */
});
