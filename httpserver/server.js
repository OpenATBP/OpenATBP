const fs = require('fs');
const express = require('express');
const bodyParser = require('body-parser');
const app = express();
const { request } = require('undici');

const database = require('./db-operations.js');
const getRequest = require('./get-requests.js');
const postRequest = require('./post-requests.js');
const SocketPolicyServer = require('./socket-policy.js');

const displayNames = require('./data/names.json');
const shopData = require('./data/shop.json');

let config;
try {
  config = require('./config.js');
} catch(e) {
  if (e instanceof Error && e.code === "MODULE_NOT_FOUND") {
    console.error("FATAL: Could not find config.js. If this is your first time running the server,");
    console.error("copy config.js.example to config.js. You can then edit it to add your MongoDB URI");
    console.error("as well as customize other options. Once finished, restart the server.");
  }
  else
    throw e;
  process.exit(1);
}

const { MongoClient, ServerApiVersion } = require('mongodb');
const mongoClient = new MongoClient(config.httpserver.mongouri, { useNewUrlParser: true, useUnifiedTopology: true, serverApi: ServerApiVersion.v1 });

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

mongoClient.connect(err => {
  if(err){
    console.error("FATAL: MongoDB connect failed: " + err);
    process.exit(1);
  }

  const playerCollection = mongoClient.db("openatbp").collection("players");

  if(!fs.existsSync("static/crossdomain.xml") || !fs.existsSync("static/config.xml")) {
    console.info("Copying default crossdomain.xml/config.xml");
    fs.copyFileSync("crossdomain.xml.example", "static/crossdomain.xml");
    fs.copyFileSync("config.xml.example", "static/config.xml");
  }

  if(!fs.existsSync("static/CNChampions.unity3d") || !fs.existsSync("static/assets")) {
    console.warn("WARN: Asset files missing from static folder - the game will not work properly.");
    console.warn("Please run 'npm run postinstall' to automatically download and extract them.")
  }

  app.set('view engine', 'ejs');
  app.use(express.static('static'));
  app.use(bodyParser.urlencoded({extended:false}));
  app.use(bodyParser.json());

  app.get('/', (req, res) => {
    res.render('index', {
      client_id: config.discord.client_id,
      redirect: config.discord.redirect_url
    });
  });

  app.get('/service/presence/present', (req, res) => {
    res.send(getRequest.handlePresent());
  });

  app.get('/service/authenticate/whoami', (req, res) => {
    getRequest.handleWhoAmI(req.query.authToken,playerCollection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/data/user/champions/tournament', (req,res) => {
    res.send(getRequest.handleTournamentData({}));
  });

  app.get('/service/shop/inventory', (req,res) => {
    res.send(getRequest.handleShop(shopData));
  });

  app.get('/service/data/config/champions/',(req,res) => {
    res.send(getRequest.handleChampConfig());
  });

  app.get('/service/shop/player', (req,res) => {
    getRequest.handlePlayerInventory(req.query.authToken,playerCollection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/data/user/champions/profile',(req,res) => {
    getRequest.handlePlayerChampions(req.query.authToken,playerCollection).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/presence/roster/:username', (req,res) => {
    var friendsList = [];
    for(var p of onlinePlayers){
      if(p.username == req.params.username){
        friendsList = p.friends;
        break;
      }
    }
    getRequest.handlePlayerFriends(req.params.username,onlinePlayers,friendsList).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.get('/service/authenticate/user/:username', (req,res) => {
    getRequest.handleBrowserLogin(req.params.username,playerCollection).then((data) => {
      res.send(JSON.stringify(data));
    }).catch(console.error);
  });

  app.get('/auth', async(req,res) => {
    console.log(req.query.code);
    var code = req.query.code;
    if(code != undefined){
      const token = await request('https://discord.com/api/oauth2/token',{
        method: 'POST',
        body: new URLSearchParams({
          client_id: config.discord.client_id,
          client_secret: config.discord.client_secret,
          code,
          grant_type: 'authorization_code',
          redirect_uri: `${config.httpserver.url}/auth/`,
          scope: 'identify',
        }).toString(),
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      });
      const oauthData = await token.body.json();
      console.log(oauthData);
      if(oauthData != undefined){
        const userResult = await request('https://discord.com/api/users/@me',{
          headers: {
            authorization: `${oauthData.token_type} ${oauthData.access_token}`
          }
        });
        var userInfo = await userResult.body.json();
        if(userInfo != undefined){
          database.findDiscordId(userInfo.id,playerCollection).then((data) => {
            res.cookie('TEGid',data.TEGid);
            res.cookie('authid',data.authid);
            res.cookie('dname',data.dname);
            res.cookie('authpass',data.authpass);
            res.cookie('logged',true);
            res.redirect(config.httpserver.url);
          }).catch((err)=>{
            console.log(err);
            res.redirect(config.httpserver.url);
          });
        }
      }
    }
  });

  app.post('/service/authenticate/login', (req,res) => {
    postRequest.handleLogin(req.body,playerCollection).then((data) => {
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
      playerCollection.findOne({"user.TEGid": req.body.username}).then((data) => {
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
    postRequest.handlePurchase(req.query.authToken,req.body.data.item, playerCollection, shopData).then((data) => {
      res.send(data);
    }).catch(console.error);
  });

  app.post('/service/authenticate/user/:username', (req,res) => {
    database.createNewUser(req.params.username,req.body.password, playerCollection).then((data) => {
      res.send(JSON.stringify(data));
    }).catch(console.error);
  });

  app.post('/service/friend/request', (req,res) => {
    console.log(req.body);
    postRequest.handleFriendRequest(req.query.authToken,req.body.toUserId,playerCollection).then((dat) => {
      res.send(dat);
    }).catch(console.error);
  });

  app.listen(config.httpserver.port, () => {
    console.info(`App running on port ${config.httpserver.port}!`);
    if (config.sockpol.enable) {
      const policyContent = fs.readFileSync(config.sockpol.file, 'utf8');
      const sockpol = new SocketPolicyServer(config.sockpol.port, policyContent);
      sockpol.start(() => {
        console.info(`Socket policy running on port ${config.sockpol.port}!`);
      });
    }
  });
});
