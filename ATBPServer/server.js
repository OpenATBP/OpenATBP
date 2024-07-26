const fs = require('fs');
const express = require('express');
const bodyParser = require('body-parser');
const app = express();
const { request } = require('undici');

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
  //removeDuplicateFriends(playerCollection).catch(console.dir);
  //addBetaTesters(playerCollection).catch(console.dir);
  //resetElo(playerCollection).catch(console.dir);

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
    res.render('index', {
      client_id: config.discord.client_id,
      redirect: config.discord.redirect_url,
      discord_enabled: config.discord.enable,
    });
  });

  app.get('/register', (req, res) => {
    res.render('register', {
      displayNames: JSON.stringify(displayNames),
    });
  });

  app.get('/login', (req, res) => {
    res.render('login');
  });

  app.post('/auth/register', (req, res) => {
    if (
      req.body.username != '' &&
      req.body.password != '' &&
      req.body.name1 != '' &&
      req.body.name3 != '' &&
      req.body.password == req.body.confirm
    ) {
      var names = [req.body.name1, req.body.name2, req.body.name3];
      postRequest
        .handleRegister(
          req.body.username,
          req.body.password,
          names,
          playerCollection
        )
        .then((user) => {
          if (user == 'login') {
            res.redirect('/login');
          } else {
            res.cookie('TEGid', user.user.TEGid);
            res.cookie('authid', user.user.authid);
            res.cookie('dname', user.user.dname);
            res.cookie('authpass', user.user.authpass);
            var date = Date.parse(user.session.expires_at);
            console.log(date.valueOf());
            res.cookie('session_token', user.session.token, {
              maxAge: date.valueOf() - Date.now(),
            });
            res.cookie('logged', true);
            res.redirect(config.httpserver.url);
          }
        })
        .catch(console.error);
    }
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
    console.log(session_token);
    if (req.query.username != '' && req.query.password != '') {
      getRequest
        .handleLogin(
          req.query.username,
          req.query.password,
          session_token,
          playerCollection
        )
        .then((user) => {
          res.cookie('TEGid', user.user.TEGid);
          res.cookie('authid', user.user.authid);
          res.cookie('dname', user.user.dname);
          res.cookie('authpass', user.user.authpass);
          var date = Date.parse(user.session.expires_at);
          console.log(date.valueOf());
          res.cookie('session_token', user.session.token, {
            maxAge: date.valueOf() - Date.now(),
          });
          res.cookie('logged', true);
          res.redirect(config.httpserver.url);
        })
        .catch(console.error);
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

  app.get('/backup/url/:code', (req, res) => {
    res.send(
      `Open this in Pale Moon or Waterfox Classic ${config.httpserver.url}/backup/${req.params.code}`
    );
  });

  app.get('/backup/:code', async (req, res) => {
    var code = req.params.code;
    console.log('Code ' + code);
    if (code != undefined) {
      const token = await request('https://discord.com/api/oauth2/token', {
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
      if (oauthData != undefined) {
        const userResult = await request('https://discord.com/api/users/@me', {
          headers: {
            authorization: `${oauthData.token_type} ${oauthData.access_token}`,
          },
        });
        var userInfo = await userResult.body.json();
        if (userInfo != undefined) {
          database
            .findDiscordId(userInfo.id, playerCollection)
            .then((data) => {
              res.cookie('TEGid', data.TEGid);
              res.cookie('authid', data.authid);
              res.cookie('dname', data.dname);
              res.cookie('authpass', data.authpass);
              res.cookie('logged', true);
              res.redirect(config.httpserver.url);
            })
            .catch((err) => {
              console.log(err);
              res.redirect(config.httpserver.url);
            });
        }
      }
    }
  });

  app.get('/auth', async (req, res) => {
    if (config.discord.backup) {
      res.redirect(`${config.httpserver.url}/backup/url/${req.query.code}`);
      return;
    }
    var code = req.query.code;
    if (code != undefined) {
      const token = await request('https://discord.com/api/oauth2/token', {
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
      if (oauthData != undefined) {
        const userResult = await request('https://discord.com/api/users/@me', {
          headers: {
            authorization: `${oauthData.token_type} ${oauthData.access_token}`,
          },
        });
        var userInfo = await userResult.body.json();
        if (userInfo != undefined) {
          database
            .findDiscordId(userInfo.id, playerCollection)
            .then((data) => {
              res.cookie('TEGid', data.TEGid);
              res.cookie('authid', data.authid);
              res.cookie('dname', data.dname);
              res.cookie('authpass', data.authpass);
              res.cookie('logged', true);
              res.redirect(config.httpserver.url);
            })
            .catch((err) => {
              console.log(err);
              res.redirect(config.httpserver.url);
            });
        }
      }
    }
  });

  app.post('/service/authenticate/login', (req, res) => {
    postRequest
      .handleLogin(req.body, playerCollection)
      .then((data) => {
        console.log(data);
        res.send(data);
      })
      .catch(console.error);
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
      console.log('Pushed!');
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
          playerObj.friends = data.friends;
          onlinePlayers.push(playerObj);
        })
        .catch(console.error);
    }
    res.send(postRequest.handlePresent(req.body));
  });

  app.post('/service/shop/purchase', (req, res) => {
    console.log(req.body);
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
    console.log(req.body);
    postRequest
      .handleFriendRequest(
        req.query.authToken,
        req.body.toUserId,
        playerCollection
      )
      .then((dat) => {
        res.send(dat);
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
