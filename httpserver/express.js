const express = require('express');
const bodyParser = require('body-parser');
const app = express();

const config = require('./config.js');
const secrets = require('./mongosecrets.js');
const getRequest = require('./get-requests.js');
const postRequest = require('./post-requests.js');

console.log("Using MongoDB URI: " + secrets.uri);
const { MongoClient, ServerApiVersion } = require('mongodb');
const client = new MongoClient(secrets.uri, { useNewUrlParser: true, useUnifiedTopology: true, serverApi: ServerApiVersion.v1 });

var onlinePlayers = [];

var onlineChecker = setInterval(() => {
  console.log("Checking online users!");
  for(var p of onlinePlayers){
    if(Date.now()-p.lastChecked > 10000){
      console.log(p.username + " offline!");
      onlinePlayers = onlinePlayers.filter((i) => {
        return i.username != p.username;
      });
    //  console.log(onlinePlayers);
    }else{
      console.log(p.username + " online!");
      //console.log(onlinePlayers);
    }
  }
}, 11000);

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

client.connect(err => {
  if(err){
    console.log("MongoDB connect failed:" + err);
    process.exit(1);
  }
  const collection = client.db("openatbp").collection("players");
  const shopCollection = client.db("openatbp").collection("shop");

  app.use(express.static('static'));
  app.use(bodyParser.urlencoded({extended:false}));
  app.use(bodyParser.json());

  app.get('/service/presence/present', (req, res) => {
    res.send(getRequest.handlePresent());
  });

  app.get('/service/authenticate/whoami', (req, res) => {
    getRequest.handleWhoAmI(req.query.authToken,collection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/data/user/champions/tournament', (req,res) => {
    res.send(getRequest.handleTournamentData({}));
  });

  app.get('/service/shop/inventory', (req,res) => {
    getRequest.handleShop(shopCollection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/data/config/champions/',(req,res) => {
    res.send(getRequest.handleChampConfig());
  });

  app.get('/service/shop/player', (req,res) => {
    getRequest.handlePlayerInventory(req.query.authToken,collection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/data/user/champions/profile',(req,res) => {
    getRequest.handlePlayerChampions(req.query.authToken,collection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/presence/roster/*', (req,res) => {
    var userNameSplit = req.url.split("/");
    var userName = userNameSplit[userNameSplit.length-1];
    var friendsList = [];
    for(var p of onlinePlayers){
      if(p.username == userName){
        friendsList = p.friends;
        break;
      }
    }
    getRequest.handlePlayerFriends(userName,onlinePlayers,friendsList).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/authenticate/user/*', (req,res) => {
    var userNameSplit = req.url.split("/");
    var userName = userNameSplit[userNameSplit.length-1];
    getRequest.handleBrowserLogin(userName,collection).then((data) => {
      res.send(JSON.stringify(data));
    }).catch(console.error);
  });

  app.post('/service/authenticate/login', (req,res) => {
    postRequest.handleLogin(req.body,collection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.post('/service/presence/present', (req,res) => {
    console.log(req.body);
    var test = 0;
    var options = JSON.parse(req.body.options);
    for(var p of onlinePlayers){
      if(p.username != req.body.username){
        test++;
      }else{
        console.log(Date.now()-p.lastChecked);
        p.lastChecked = Date.now();
        p.username = req.body.username;
        p.level = options.level;
        p.elo = options.level;
        p.location = options.location;
        p.name = options.name;
        p.tier = options.tier;
        break;
      }
    }
    if(test == onlinePlayers.length){
      console.log("Pushed!");
      var playerObj = {
        username: req.body.username,
        level: options.level,
        elo: options.level,
        location: options.location,
        name: options.name,
        tier: options.tier,
        lastChecked: Date.now(),
        friends: []
      };
      collection.findOne({"user.TEGid": req.body.username}).then((data) => {
        playerObj.friends = data.friends;
        onlinePlayers.push(playerObj);
      }).catch(console.error);
    }else{
      console.log("Players: " + onlinePlayers.length + " vs " + test);
    }
    res.send(postRequest.handlePresent(req.body));
  });

  app.post('/service/shop/purchase', (req,res) => {
    console.log(req.body);
    postRequest.handlePurchase(req.query.authToken,req.body.data.item,collection,shopCollection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.post('/service/authenticate/user/*', (req,res) => {
    var userNameSplit = req.url.split("/");
    var userName = userNameSplit[userNameSplit.length-1];
    createNewUser(userName,req.body.password).then((data) => {
      res.send(JSON.stringify(data));
    }).catch(console.error);
  });


  const server = app.listen(config.httpserver.port, () => {
    console.log(`App running on port ${config.httpserver.port}!`);
    if (config.sockpol.enable) {
      const sockpol = require('./socketpolicy.js');
    }
  });
});
