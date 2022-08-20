const http = require('http');
const fs = require('fs').promises;

const host = 'localhost';
const port = 8001;

const secrets = require('./mongosecrets.js');
const getRequest = require('./get-requests.js');
const postRequest = require('./post-requests.js');

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
          authToken: token
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

client.connect(err => {
  const requestListener = function(req,res) {
    const collection = client.db("openatbp").collection("players");
    //console.log(Object.keys(req));
    //console.log(res);
    res.writeHead(200, { //This is what requests are allowed to go through
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'OPTIONS, POST, GET',
      'Access-Control-Max-Age': 2592000
    });
    console.log(req.url + " " + req.method);
    if(req.method == "POST"){ //Handles new user request
      if(req.url.includes("/authenticate/user")){
        var userNameSplit = req.url.split("/");
        var userName = userNameSplit[userNameSplit.length-1];
        var body = "";
        req.on("data", (data) => {
          body += data;
        });

        req.on('end', () => { //When finished reading the data, it will create a new user.
          createNewUser(userName,JSON.parse(body).password).then((data) => {
            res.end(JSON.stringify(data));
          }).catch(console.error);
        });
      }else if(req.url.includes("/service/authenticate/login")){
        req.on("data", (data) => {
          body += data;
        });

        req.on('end', () => { //When finished reading the data, it will create a new user.
          console.log(body.replace("undefined",""));
          if(body.replace("undefined","") != ""){
            postRequest.handleLogin(JSON.parse(body.replace("undefined","")),collection).then((obj) => {
              res.end(obj);
            }).catch(console.error);
          }
        });
    }else if(req.url.includes('/service/presence/present')){
      req.on("data", (data) => {
        body += data;
      });

      req.on('end', () => { //When finished reading the data, it will create a new user.
        //console.log(body.replace("undefined",""));
        if(body.replace("undefined","") != ""){
          res.end(postRequest.handlePresent(body));
        }
      });
    }
  }else if(req.method == "GET"){ //Handles web request for user information when logging in
      if(req.url.includes("/authenticate/user")){
        var userNameSplit = req.url.split("/");
        var userName = userNameSplit[userNameSplit.length-1];
        getRequest.handleBrowserLogin(userName,collection).then((data) => {
          res.end(JSON.stringify(data));
        }).catch(console.error);
      }else if(req.url.includes("/crossdomain.xml")){
        res.end(getRequest.handleCrossDomain());
      }else if(req.url.includes("/service/presence/present")){
        res.end(getRequest.handlePresent());
      }else if(req.url.includes("/service/authenticate/whoami")){
        getRequest.handleWhoAmI(req.url,collection).then((data) => {
          res.end(data);
        }).catch(console.error);
      }else if(req.url.includes("/service/data/user/champions/tournament")){
        res.end(getRequest.handleTournamentData({}));
      }else if(req.url.includes("/service/shop/inventory")){
        getRequest.handleShop().then((data) => {
          res.end(data);
        }).catch(console.error);
      }else if(req.url.includes("/service/data/config/champions")){
        res.end(getRequest.handleChampConfig());
      }else if(req.url.includes("/service/data/user/champions/profile")){
        var tokenSplit = req.url.split("?"); //Definitely feel like there's a better way to get the authToken query
        var aToken = tokenSplit[tokenSplit.length-1].replace("authToken=","");
        getRequest.handlePlayerChampions(aToken,collection).then((data) => {
          res.end(data);
        }).catch(console.error);
      }else if(req.url.includes("/service/presence/roster/")){
        res.end(JSON.stringify({"roster":[]}));
      }
        /*

        */
    }

  }

  const server = http.createServer(requestListener)

  server.listen(port,host, () => {
    console.log("Server is running!");
  });
  /*

  */
});
