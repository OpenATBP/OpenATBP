const fs = require('fs');
const express = require('express');
const bodyParser = require('body-parser');
const app = express();

const database = require('./db-operations.js');
const getRequest = require('./get-requests.js');
const postRequest = require('./post-requests.js');

const ATBPLobbyServer = require('./atbp-lobby.js');
const SocketPolicyServer = require('./socket-policy.js');

const displayNames = require('./data/names.json');
const shopData = require('./data/shop.json');

//Added to remove duplicate friends if needed...
async function removeDuplicateFriends(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var newFriends = [];
      for (var friend of doc.friends) {
        if (!newFriends.includes(friend)) newFriends.push(friend);
      }
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = { $set: { friends: newFriends } };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

async function convertUserNames(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      var newId = `${Math.floor(Math.random() * 1000000000)}`;
      var q = { 'user.authpass': doc.user.authpass };
      var o = { upsert: true };
      var up = {
        $set: { 'user.authid': newId, 'user.TEGid': doc.user.authid },
      };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

//Added to make everyone in the database a beta tester
async function addBetaTesters(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = { $set: { betaTester: true } };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

async function resetElo(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = { $set: { 'player.elo': 1150.0, 'player.tier': 1.0 } };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

async function addRequests(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = { $set: { requests: [] } };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

function addChampData(collection) {
  var champs = [
    'billy',
    'bmo',
    'cinnamonbun',
    'finn',
    'fionna',
    'flame',
    'gunter',
    'hunson',
    'iceking',
    'jake',
    'lemongrab',
    'lich',
    'lsp',
    'magicman',
    'marceline',
    'neptr',
    'peppermintbutler',
    'princessbubblegum',
    'rattleballs',
  ];
  for (var c of champs) {
    var data = {
      champion: c,
      playsPVP: 0,
      winsPVP: 0,
      kills: 0,
      deaths: 0,
      assists: 0,
      damage: 0,
    };
    collection.insertOne(data).catch(console.error);
  }
}

async function cleanupUsers(collection) {
  try {
    var cursor = collection.find();
    var num = 0;
    for await (var doc of cursor) {
      if(doc.user.dname.includes("COOL GUY")){
        num++;
        var q = { 'user.TEGid': doc.user.TEGid };
        var o = { upsert: false };
        var up = { $set: { 'user.dname': `COOL GUY ${num}` } };
        var res = await collection.updateOne(q, up, o);
        console.log(doc.user.dname);
        console.log(res);
      }
      //var res = await collection.updateOne(q, up, o);
      //console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

async function clearPlayerData(playerCollection,champCollection){

  try {
    var cursor = playerCollection.find();
    for await (var doc of cursor) {
      var stats = ['playsPVP','disconnects','winsPVP','kills','deaths','assists','towers','minions','jungleMobs','altars','largestSpree','largestMulti','scoreHighest','scoreTotal'];
      var docPlayer = doc.player;
      for(var k of Object.keys(docPlayer)){
        if(stats.includes(k)) docPlayer[k] = 0;
      }
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: false };
      var up = { $set: { 'player': docPlayer, 'champion': {} } };
      var res = await playerCollection.updateOne(q, up, o);
      console.log(res);

    }
  } finally {
    console.log('Done!');
  }

  try{
    var cursor2 = champCollection.find();
    for await(var doc of cursor2){
      for(var k of Object.keys(doc)){
        if(k != 'champion' && k != '_id'){
          doc[k] = 0;
        }
      }
      var q = { 'champion': doc.champion };
      var o = { upsert: false };
      var up = { $set: doc };
      var res = await champCollection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log("Done 2");
  }
}

function getLowerCaseName(name) {
  var firstLetter = name.charAt(name).toUpperCase();
  var fullString = firstLetter;
  fullString += name.toLowerCase().substring(1, name.length);
  return fullString;
}

let config;
try {
  config = require('./config.js');
} catch (err) {
  if (err instanceof Error && err.code === 'MODULE_NOT_FOUND') {
    console.error(
      'FATAL: Could not find config.js. If this is your first time running the server,'
    );
    console.error(
      'copy config.js.example to config.js. You can then edit it to add your MongoDB URI'
    );
    console.error(
      'as well as customize other options. Once finished, restart the server.'
    );
  } else throw err;
  process.exit(1);
}

const { MongoClient, ServerApiVersion } = require('mongodb');
const mongoClient = new MongoClient(config.httpserver.mongouri, {
  useNewUrlParser: true,
  useUnifiedTopology: true,
  serverApi: ServerApiVersion.v1,
});

var onlinePlayers = [];

var onlineChecker = setInterval(() => {
  for (var p of onlinePlayers) {
    if (Date.now() - p.lastChecked > 10000) {
      onlinePlayers = onlinePlayers.filter((i) => {
        return i.username != p.username;
      });
    }
  }
}, 11000);

var playerList = setInterval(() => {
  for (var p of onlinePlayers) {
    console.log(p.name + ' is online!');
  }
}, 60000);

mongoClient.connect((err) => {
  if (err) {
    console.error('FATAL: MongoDB connect failed: ' + err);
    process.exit(1);
  }

  const playerCollection = mongoClient.db('openatbp').collection('users');
  const champCollection = mongoClient.db('openatbp').collection('champions');
  //removeDuplicateFriends(playerCollection).catch(console.dir);
  //addBetaTesters(playerCollection).catch(console.dir);
  //resetElo(playerCollection).catch(console.dir);
  //convertUserNames(playerCollection).catch(console.dir);
  //addRequests(playerCollection).catch(console.dir);
  //addChampData(champCollection);
  //cleanupUsers(playerCollection).catch(console.dir);
  clearPlayerData(playerCollection,champCollection).catch(console.dir);

  if (
    !fs.existsSync('static/crossdomain.xml') ||
    !fs.existsSync('static/config.xml')
  ) {
    console.info('Copying default crossdomain.xml/config.xml');
    fs.copyFileSync('crossdomain.xml.example', 'static/crossdomain.xml');
    fs.copyFileSync('config.xml.example', 'static/config.xml');
  }

  if (
    !fs.existsSync('static/CNChampions.unity3d') ||
    !fs.existsSync('static/assets')
  ) {
    console.warn(
      'WARN: Asset files missing from static folder - the game will not work properly.'
    );
    console.warn(
      "Please run 'npm run postinstall' to automatically download and extract them."
    );
  }

  app.set('view engine', 'ejs');
  app.use(express.static('static'));
  app.use(bodyParser.urlencoded({ extended: false }));
  app.use(bodyParser.json());

  app.get('/', (req, res) => {
    res.render('index');
  });

  app.get('/register', (req, res) => {
    res.render('register', {
      displayNames: JSON.stringify(displayNames),
    });
  });

  app.get('/forgot', (req, res) => {
    res.render('forgot');
  });

  app.get('/login', (req, res) => {
    res.render('login');
  });

  app.get('/friends', (req, res) => {
    var session_token = '';
    for (var h of req.rawHeaders) {
      if (h.includes('session_token')) {
        var cookies = h.split(';');
        for (var c of cookies) {
          if (c.includes('session_token')) {
            session_token = c
              .replace('session_token=', '')
              .replace(' ', '')
              .replace(';', '');
          }
        }
      }
    }
    getRequest
      .handleFriendRequest(session_token, playerCollection)
      .then((requests) => {
        res.render('friends', { requests: JSON.stringify(requests) });
      })
      .catch((err) => {
        console.log('Error', err);
        res.render('friends', { requests: JSON.stringify([]) });
      });
  });

  app.get('/data/users', (req, res) => {
    res.send(JSON.stringify({ users: onlinePlayers.length }));
  });

  app.post('/friend/accept/:friend', (req, res) => {
    var session_token = '';
    for (var h of req.rawHeaders) {
      if (h.includes('session_token')) {
        var cookies = h.split(';');
        for (var c of cookies) {
          if (c.includes('session_token')) {
            session_token = c
              .replace('session_token=', '')
              .replace(' ', '')
              .replace(';', '');
          }
        }
      }
    }
    postRequest
      .handleAcceptFriend(session_token, req.params.friend, playerCollection)
      .then((r) => {
        //console.log("Here");
        res.redirect(config.httpserver.url); //This doesn't work for some reason
      })
      .catch(console.error);
  });

  app.post('/friend/decline/:friend', (req, res) => {
    var session_token = '';
    for (var h of req.rawHeaders) {
      if (h.includes('session_token')) {
        var cookies = h.split(';');
        for (var c of cookies) {
          if (c.includes('session_token')) {
            session_token = c
              .replace('session_token=', '')
              .replace(' ', '')
              .replace(';', '');
          }
        }
      }
    }

    postRequest
      .handleDeclineFriend(session_token, req.params.friend, playerCollection)
      .then(() => {
        res.redirect(config.httpserver.url); //This doesn't work for some reason
      })
      .catch(console.error);
  });

  app.post('/friend/add', (req, res) => {
    var session_token = '';
    for (var h of req.rawHeaders) {
      if (h.includes('session_token')) {
        var cookies = h.split(';');
        for (var c of cookies) {
          if (c.includes('session_token')) {
            session_token = c
              .replace('session_token=', '')
              .replace(' ', '')
              .replace(';', '');
          }
        }
      }
    }
    playerCollection
      .findOne({ 'session.token': session_token })
      .then((u) => {
        if (u != null && u.user.TEGid != req.body.username) {
          playerCollection
            .findOne({ 'user.TEGid': req.body.username })
            .then((user) => {
              if (user != null) {
                postRequest
                  .handleFriendRequest(
                    u.user.TEGid,
                    user.user.TEGid,
                    playerCollection
                  )
                  .then(() => {
                    res.redirect('/friends?added=true');
                  })
                  .catch(console.error);
              } else res.redirect('/friends?added=false');
            })
            .catch(console.error);
        } else res.redirect('/friends?added=false');
      })
      .catch(console.error);
  });

  app.post('/auth/register', (req, res) => {
    var nameCount = 0;
    if (
      req.body.name1 != '' &&
      displayNames.list1.includes(getLowerCaseName(req.body.name1))
    )
      nameCount++;
    else if (req.body.name1 != '') nameCount = -100;
    if (
      req.body.name2 != '' &&
      displayNames.list2.includes(getLowerCaseName(req.body.name2))
    )
      nameCount++;
    else if (req.body.name2 != '') nameCount = -100;
    if (
      req.body.name3 != '' &&
      displayNames.list3.includes(getLowerCaseName(req.body.name3))
    )
      nameCount++;
    else if (req.body.name3 != '') nameCount = -100;

    if (
      req.body.username != '' &&
      req.body.password != '' &&
      nameCount > 1 &&
      req.body.password == req.body.confirm
    ) {
      var names = [req.body.name1, req.body.name2, req.body.name3];
      postRequest
        .handleRegister(
          req.body.username,
          req.body.password,
          names,
          req.body.forgot,
          playerCollection
        )
        .then((user) => {
          if (user == 'login') {
            res.redirect('/login?exists=true');
          } else {
            res.cookie('TEGid', user.user.TEGid);
            res.cookie('authid', user.user.authid);
            res.cookie('dname', user.user.dname);
            res.cookie('authpass', user.user.authpass);
            var date = Date.parse(user.session.expires_at);
            res.cookie('session_token', user.session.token, {
              maxAge: date.valueOf() - Date.now(),
            });
            res.cookie('logged', true);
            res.redirect(config.httpserver.url);
          }
        })
        .catch((e) => {
          console.log(e);
          res.redirect('/register?failed=true');
        });
    } else res.redirect('/register?failed=true');
  });

  app.post('/auth/forgot', (req, res) => {
    if (req.body.password == req.body.confirm) {
      postRequest
        .handleForgotPassword(
          req.body.username,
          req.body.forgot,
          req.body.password,
          playerCollection
        )
        .then((data) => {
          res.clearCookie('session_token');
          res.cookie('logged', false);
          res.redirect('/login');
        })
        .catch((e) => {
          console.log(e);
          res.redirect('/forgot?failed=true');
        });
    } else res.redirect('/forgot?failed=true');
  });

  app.get('/auth/login', (req, res) => {
    var session_token = '';
    for (var h of req.rawHeaders) {
      if (h.includes('session_token')) {
        var cookies = h.split(';');
        for (var c of cookies) {
          if (c.includes('session_token')) {
            session_token = c
              .replace('session_token=', '')
              .replace(' ', '')
              .replace(';', '');
          }
        }
      }
    }
    if (req.query.username != '' && req.query.password != '') {
      getRequest
        .handleLogin(
          req.query.username,
          req.query.password,
          session_token,
          playerCollection
        )
        .then((user) => {
          var date = Date.parse(user.session.expires_at);
          res.cookie('TEGid', user.user.TEGid, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.cookie('authid', user.user.authid, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.cookie('dname', user.user.dname, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.cookie('authpass', user.user.authpass, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.cookie('session_token', user.session.token, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.cookie('logged', true, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.redirect(config.httpserver.url);
        })
        .catch((e) => {
          console.log(e);
          res.redirect('/login?failed=true');
        });
    }
  });

  app.get('/service/presence/present', (req, res) => {
    res.send(getRequest.handlePresent());
  });

  app.get('/service/authenticate/whoami', (req, res) => {
    getRequest
      .handleWhoAmI(req.query.authToken, playerCollection)
      .then((data) => {
        res.send(data);
      })
      .catch(console.error);
  });

  app.get('/service/data/user/champions/tournament', (req, res) => {
    getRequest
      .handleTournamentData(req.query.authToken, playerCollection)
      .then((data) => {
        res.send(data);
      })
      .catch(console.error);
  });

  app.get('/service/shop/inventory', (req, res) => {
    res.send(getRequest.handleShop(shopData));
  });

  app.get('/service/data/config/champions/', (req, res) => {
    res.send(getRequest.handleChampConfig());
  });

  app.get('/service/shop/player', (req, res) => {
    getRequest
      .handlePlayerInventory(req.query.authToken, playerCollection)
      .then((data) => {
        res.send(data);
      })
      .catch(console.error);
  });

  app.get('/service/data/user/champions/profile', (req, res) => {
    getRequest
      .handlePlayerChampions(req.query.authToken, playerCollection)
      .then((data) => {
        res.send(data);
      })
      .catch(console.error);
  });

  app.get('/service/presence/roster/:username', (req, res) => {
    var friendsList = [];
    for (var p of onlinePlayers) {
      if (p.username == req.params.username) {
        friendsList = p.friends;
        break;
      }
    }
    getRequest
      .handlePlayerFriends(req.params.username, onlinePlayers, friendsList)
      .then((data) => {
        res.send(data);
      })
      .catch(console.error);
  });

  app.get('/service/authenticate/user/:username', (req, res) => {
    getRequest
      .handleBrowserLogin(req.params.username, playerCollection)
      .then((data) => {
        res.send(JSON.stringify(data));
      })
      .catch(console.error);
  });

  app.post('/service/authenticate/login', (req, res) => {
    var session_token = '';
    for (var h of req.rawHeaders) {
      if (h.includes('session_token')) {
        var cookies = h.split(';');
        for (var c of cookies) {
          if (c.includes('session_token')) {
            session_token = c
              .replace('session_token=', '')
              .replace(' ', '')
              .replace(';', '');
          }
        }
      }
    }
    postRequest
      .handleLogin(req.body, session_token, playerCollection)
      .then((data) => {
        res.send(data);
      })
      .catch((e) => {
        if (e) console.log(e);
        //Doesn't work lol
        res.redirect('/login?failed=true');
      });
  });

  app.post('/service/presence/present', (req, res) => {
    var test = 0;
    var options = JSON.parse(req.body.options);
    for (var p of onlinePlayers) {
      if (p.username != req.body.username) {
        test++;
      } else {
        p.lastChecked = Date.now();
        p.username = req.body.username;
        p.level = options.level;
        p.elo = options.elo;
        p.location = options.location;
        p.name = options.name;
        p.tier = options.tier;
        break;
      }
    }
    if (test == onlinePlayers.length) {
      var playerObj = {
        username: req.body.username,
        level: options.level,
        elo: options.elo,
        location: options.location,
        name: options.name,
        tier: options.tier,
        lastChecked: Date.now(),
        friends: [],
      };
      playerCollection
        .findOne({ 'user.TEGid': req.body.username })
        .then((data) => {
          if (data != null) {
            playerObj.friends = data.friends;
            onlinePlayers.push(playerObj);
            res.send(postRequest.handlePresent(req.body));
          }
        })
        .catch(console.error);
    } else res.send(postRequest.handlePresent(req.body));
  });

  app.post('/service/shop/purchase', (req, res) => {
    postRequest
      .handlePurchase(
        req.query.authToken,
        req.body.data.item,
        playerCollection,
        shopData
      )
      .then((data) => {
        res.send(data);
      })
      .catch(console.error);
  });

  app.post('/service/authenticate/user/:username', (req, res) => {
    database
      .createNewUser(
        req.params.username,
        req.body.password,
        'manual',
        playerCollection
      )
      .then((data) => {
        res.send(JSON.stringify(data));
      })
      .catch(console.error);
  });

  app.post('/service/friend/request', (req, res) => {
    playerCollection
      .findOne({ 'session.token': req.query.authToken })
      .then((u) => {
        if (u != null) {
          postRequest
            .handleFriendRequest(
              u.user.TEGid,
              req.body.toUserId,
              playerCollection
            )
            .then((dat) => {
              res.send(dat);
            })
            .catch(console.error);
        }
      })
      .catch(console.error);
  });

  app.listen(config.httpserver.port, () => {
    console.info(`Express server running on port ${config.httpserver.port}!`);
    if (config.lobbyserver.enable) {
      const lobbyServer = new ATBPLobbyServer(config.lobbyserver.port);
      lobbyServer.start(() => {
        console.info(
          `Lobby server running on port ${config.lobbyserver.port}!`
        );
      });
    }
    if (config.sockpol.enable) {
      const policyContent = fs.readFileSync(config.sockpol.file, 'utf8');
      const sockpol = new SocketPolicyServer(
        config.sockpol.port,
        policyContent
      );
      sockpol.start(() => {
        console.info(`Socket policy running on port ${config.sockpol.port}!`);
      });
    }
  });
});
