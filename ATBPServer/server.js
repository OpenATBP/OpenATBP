const fs = require('fs');
const express = require('express');
const bodyParser = require('body-parser');
const app = express();
const cors = require('cors');
const cookieParser = require('cookie-parser');

const database = require('./db-operations.js');
const getRequest = require('./get-requests.js');
const postRequest = require('./post-requests.js');

const ATBPLobbyServer = require('./atbp-lobby.js');
const SocketPolicyServer = require('./socket-policy.js');
var lobbyServer;

const displayNames = require('./data/names.json');
const shopData = require('./data/shop.json');
const path = require('path');

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

async function resetElo(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = { $set: { 'player.elo': 1.0, 'player.tier': 1.0 } };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

async function addQueueData(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = {
        $set: {
          queue: {
            lastDodge: -1,
            queueBan: -1,
            dodgeCount: 0,
            timesOffended: 0,
          },
        },
      };

      var res = await collection.updateOne(q, up, o);
      console.log(res);
    }
  } finally {
    console.log('Done!');
  }
}

async function addCustomBags(collection) {
  try {
    var cursor = collection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      var q = { 'user.TEGid': doc.user.TEGid };
      var o = { upsert: true };
      var up = {
        $addToSet: {
          inventory: {
            $each: [
              'belt_beta_assassin',
              'belt_beta_tank',
              'belt_beta_rng',
              'belt_beta_support',
              'belt_beta_jungle',
              'belt_beta_laner',
              'belt_beta_risk',
              'belt_beta_adc',
              'belt_beta_power',
              'belt_beta_warlock',
              'belt_beta_bruiser',
              'belt_beta_anti_tank',
              'belt_beta_anti_mage',
              'belt_beta_hybrid',
              'belt_beta_vamp',
            ],
          },
        },
      };

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

async function wipePlayerData(playerCollection) {
  try {
    var cursor = playerCollection.find();
    for await (var doc of cursor) {
      //console.log(doc.friends);
      if (doc != null && doc.player != undefined) {
        var q = { 'user.TEGid': doc.user.TEGid };
        var o = { upsert: true };
        var up = {
          $set: {
            player: {
              playsPVP: 1,
              tier: 1.0,
              elo: 1,
              disconnects: 0,
              playsBots: doc.player.playsBots,
              rank: doc.player.rank,
              rankProgress: doc.player.rankProgress,
              winsPVP: 1,
              winsBots: doc.player.winsBots,
              points: 0,
              coins: doc.player.coins,
              kills: 0,
              deaths: 0,
              assists: 0,
              towers: 0,
              minions: 0,
              jungleMobs: 0,
              altars: 0,
              largestSpree: 0,
              largestMulti: 0,
              scoreHighest: 0,
              scoreTotal: 0,
            },
            champion: {},
          },
        };
        var res = await playerCollection.updateOne(q, up, o);
        console.log(res);
      }
    }
  } finally {
    console.log('Done');
  }
}

function getLowerCaseName(name) {
  var firstLetter = name.charAt(name).toUpperCase();
  var fullString = firstLetter;
  fullString += name.toLowerCase().substring(1, name.length);
  return fullString;
}

async function getTopPlayers(players) {
  const TOP_PLAYER_LIMIT = 500;
  try {
    const projection = {
      'user.dname': 1,
      'user.pfp': 1,
      'player.elo': 1,
    };

    const query = { 'player.elo': { $exists: true } };

    return await players
      .find(query, { projection })
      .sort({ 'player.elo': -1 })
      .limit(TOP_PLAYER_LIMIT)
      .toArray();
  } catch (err) {
    console.log(err);
    return [];
  }
}

async function getGlobalChampionStats(champions) {
  try {
    let globalStats = await champions.find({}).toArray();
    return globalStats;
  } catch (err) {
    console.log(err);
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

const { MongoClient, ServerApiVersion, ObjectId } = require('mongodb');
const { join, parse, extname } = require('node:path');
const { COMMON_ICON_COST } = require('./icon_metadata');
const mongoClient = new MongoClient(config.httpserver.mongouri, {
  useNewUrlParser: true,
  useUnifiedTopology: true,
  serverApi: ServerApiVersion.v1,
});

var onlinePlayers = [];

let ICONS = [];

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

  loadIcons();

  const playerCollection = mongoClient.db('openatbp').collection('users');
  const champCollection = mongoClient.db('openatbp').collection('champions');
  //TODO: Put all the testing commands into a separate file

  //addQueueData(playerCollection);
  //curveElo(playerCollection);
  //addCustomBags(playerCollection);
  //addChampData(champCollection);
  //wipePlayerData(playerCollection);

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

  const { ALL_ICONS_METADATA } = require('./icon_metadata');

  app.set('view engine', 'ejs');
  app.use(express.static('static'));
  app.use(bodyParser.urlencoded({ extended: false }));
  app.use(bodyParser.json());
  app.use(cors());
  app.use(cookieParser());
  app.use(express.json());
  // app.use('/icon_shop', express.static(path.join(__dirname, 'icon_shop')));

  app.get('/icons', async (req, res) => {
    const session_token = req.cookies?.session_token || req.query.session_token;

    if (!session_token) {
      console.log('No session token found, redirecting to login page');
      return res.redirect('/login');
    }

    try {
      const userProfile = await playerCollection.findOne({
        'session.token': session_token,
      });

      if (!userProfile) {
        console.log(
          'No user profile found for session token, redirecting to login page'
        );
        return res.redirect('/login');
      }

      const userId = userProfile._id;

      let defaultsToSet = {};
      let needsDBUpdateForDefaults = false;

      if (!userProfile.user) userProfile.user = {};
      if (!userProfile.player) userProfile.player = {};
      if (!userProfile.icons) userProfile.icons = [];

      if (!userProfile.user?.pfp) {
        defaultsToSet['user.pfp'] = 'Default';
        userProfile.user.pfp = 'Default';
        needsDBUpdateForDefaults = true;
      }

      if (needsDBUpdateForDefaults) {
        console.log(
          `Applying default fields for user ${userId}:`,
          defaultsToSet
        );
        try {
          await playerCollection.updateOne(
            { _id: userId },
            { $set: defaultsToSet }
          );
        } catch (err) {
          console.error(
            `Database error setting default fields for user ${userId}:`,
            err
          );
        }
      }

      const ownedIcons = userProfile.icons;
      const playerStats = userProfile.player;
      const newlyUnlockedIcons = [];

      for (const iconName in ALL_ICONS_METADATA) {
        const meta = ALL_ICONS_METADATA[iconName];
        let isOwned = ownedIcons.includes(iconName);

        if (!isOwned && meta.unlock) {
          const condition = meta.unlock;
          const userStat = playerStats[condition.stat] || 0;

          if (userStat >= condition.threshold) {
            newlyUnlockedIcons.push(iconName);
            userProfile.icons.push(iconName);
          }
        }
      }

      if (newlyUnlockedIcons.length > 0) {
        try {
          const updateResult = await playerCollection.updateOne(
            { _id: userId },
            { $addToSet: { icons: { $each: newlyUnlockedIcons } } }
          );
        } catch (err) {
          console.error(
            `Database error updating unlocked icons for user ${userId}:`,
            err
          );
        }
      }

      const finalUserInfo = {
        _id: userId.toString(),
        user: {
          dname: userProfile.user?.dname,
          pfp: userProfile.user?.pfp,
        },
        player: {
          playsPVP: userProfile.player?.playsPVP,
          tier: userProfile.player?.tier,
          elo: userProfile.player?.elo,
          rank: userProfile.player?.rank,
          coins: userProfile.player?.coins,
          kills: userProfile.player?.kills,
          largestSpree: userProfile.player?.largestSpree,
          largestMulti: userProfile.player?.largestMulti,
        },
        icons: userProfile.icons,
      };

      const finalAllIconsData = {};
      const finalOwnedIcons = finalUserInfo.icons;

      for (const iconName in ALL_ICONS_METADATA) {
        const meta = ALL_ICONS_METADATA[iconName];
        const isOwned = finalOwnedIcons.includes(iconName);

        finalAllIconsData[iconName] = {
          src: meta.src,
          rarity: meta.rarity,
          owned: isOwned,
          cost: !isOwned && meta.cost !== undefined ? meta.cost : undefined,
          unlock:
            !isOwned && meta.unlock !== undefined
              ? meta.unlock?.text
              : undefined,
        };
      }

      res.render('icons', {
        userInfo: finalUserInfo,
        allIconsData: finalAllIconsData,
      });
    } catch (err) {
      console.log(`Error processing /icons route: ${err}`);
      res.status(500).send('Server error');
    }
  });

  function loadIcons() {
    const iconsPath = path.join(
      __dirname,
      '..',
      'ATBPServer',
      'static',
      'assets',
      'pfp'
    );

    try {
      const files = fs.readdirSync(iconsPath);
      files.forEach((file) => {
        const iconName = path.parse(file).name;
        if (
          path.extname(file).toLowerCase() === '.jpg' &&
          !ICONS.includes(iconName)
        ) {
          ICONS.push(iconName);
        }
      });
      console.log(`Loaded ${ICONS.length} icons from ${iconsPath}`);
    } catch (error) {
      console.error('Error loading icons:', error);
    }
  }

  app.post('/icons/buy', async (req, res) => {
    if (!playerCollection) {
      return res.status(503).json({ message: 'Database not connected' });
    }

    try {
      const { userId, iconName } = req.body;

      if (!userId || !iconName) {
        return res
          .status(400)
          .json({ message: 'Missing userId or iconName in request body' });
      }
      if (!ObjectId.isValid(userId)) {
        return res.status(400).json({ message: 'Invalid User ID format' });
      }

      const iconDataFromServer = ALL_ICONS_METADATA[iconName];

      if (!iconDataFromServer) {
        return res
          .status(404)
          .json({ message: `Icon '${iconName}' is not a valid icon name.` });
      }

      if (!iconDataFromServer.cost === undefined) {
        return res
          .status(400)
          .json({ message: `Icon '${iconName}' does not have a cost.` });
      }

      const query = { _id: new ObjectId(userId) };

      const projection = { projection: { 'player.coins': 1, icons: 1 } };
      const user = await playerCollection.findOne(query, projection);

      if (!user) {
        return res.status(404).json({ message: 'User not found' });
      }

      const currentCoins = user.player?.coins || 0;
      const ownedIcons = user.icons || [];

      if (ownedIcons.includes(iconName)) {
        return res.status(400).json({ message: 'You already own this icon' });
      }

      if (currentCoins < COMMON_ICON_COST) {
        return res.status(400).json({
          message: `Not enough coins. Need ${COMMON_ICON_COST}, have ${currentCoins}`,
        });
      }

      const newCoinTotal = currentCoins - COMMON_ICON_COST;

      const updateOperation = {
        $set: {
          'player.coins': newCoinTotal,
        },
        $push: {
          icons: iconName,
        },
      };

      const updateResult = await playerCollection.updateOne(
        query,
        updateOperation
      );

      if (updateResult.modifiedCount === 1) {
        res.status(200).json({
          message: 'Purchase successful!',
          iconName: iconName,
          newCoinTotal: newCoinTotal,
        });
      } else {
        console.error(
          `Failed to update database for user ${userId} buying ${iconName}. Result:`,
          updateResult
        );
        return res
          .status(500)
          .json({ message: 'Database update failed after checks' });
      }
    } catch (error) {
      console.error('Error buying icon:', error);
      res.status(500).json({
        message: 'Server error during purchase',
        error: error.message,
      });
    }
  });

  app.post('/icons/use', async (req, res) => {
    if (!playerCollection) {
      return res.status(503).json({ message: 'Database not connected' });
    }

    try {
      const { userId, iconName } = req.body;

      if (!userId || !iconName) {
        return res.status(400).json({ message: 'Missing userId or iconName' });
      }
      if (!ObjectId.isValid(userId)) {
        return res.status(400).json({ message: 'Invalid User ID format' });
      }

      if (!ALL_ICONS_METADATA[iconName] && iconName !== 'Default') {
        return res
          .status(404)
          .json({ message: `Icon '${iconName}' is not a valid icon.` });
      }

      const query = { _id: new ObjectId(userId) };

      const projection = { projection: { icons: 1 } };
      const user = await playerCollection.findOne(query, projection);

      if (!user) {
        return res.status(404).json({ message: 'User not found' });
      }

      const ownedIcons = user.icons || [];
      if (!ownedIcons.includes(iconName) && iconName !== 'Default') {
        return res
          .status(403)
          .json({ message: `You do not own the icon '${iconName}'` });
      }

      const updateOperation = {
        $set: {
          'user.pfp': iconName,
        },
      };

      const updateResult = await playerCollection.updateOne(
        query,
        updateOperation
      );

      if (updateResult.matchedCount === 0) {
        return res
          .status(404)
          .json({ message: 'User not found during update.' });
      }

      if (updateResult.modifiedCount === 1 || updateResult.matchedCount === 1) {
        res
          .status(200)
          .json({ message: 'Icon set successfully!', newPfp: iconName });
      } else {
        console.error(
          `Failed to set pfp for user ${userId} to ${iconName}. Result:`,
          updateResult
        );
        return res
          .status(500)
          .json({ message: 'Database update failed unexpectedly' });
      }
    } catch (error) {
      console.error('Error using icon:', error);
      res.status(500).json({
        message: 'Server error during icon use',
        error: error.message,
      });
    }
  });

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

  app.get('/location/:id/:location', (req, res) => {
    console.log(req.params.id);
    lobbyServer.addPlayerLocation(req.params.id, req.params.location);
    res.send({});
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

  app.get('/data/rankings', async (req, res) => {
    try {
      const topPlayers = await getTopPlayers(playerCollection);
      res.send(topPlayers);
    } catch (err) {
      res.send(err);
    }
  });

  app.get('/data/champstats', async (req, res) => {
    try {
      let globalStats = await getGlobalChampionStats(champCollection);
      res.send(globalStats);
    } catch (err) {
      console.log(err);
      res.send('An error occured while fetching data');
    }
  });

  app.post('/data/player', async (req, res) => {
    try {
      const { playerName } = req.body;

      let normalizedPlayerName = playerName.toUpperCase();
      const query = { 'user.dname': normalizedPlayerName };

      const projection = {
        projection: {
          'user.dname': 1,
          'user.pfp': 1,
          'player.playsPVP': 1,
          'player.winsPVP': 1,
          'player.elo': 1,
          'player.kills': 1,
          'player.deaths': 1,
          'player.largestSpree': 1,
          'player.largestMulti': 1,
          'player.rank': 1,
        },
      };
      const player = await playerCollection.findOne(query, projection);
      const allPlayers = await playerCollection
        .find()
        .sort({ 'player.elo': -1 })
        .toArray();
      const playerIndex = allPlayers.findIndex(
        (player) => player.user.dname === normalizedPlayerName
      );

      if (playerIndex != -1) {
        player.player.leaderboardPosition = playerIndex + 1;
        res.send(player);
      } else {
        res.send({ message: 'Player not found' });
      }
    } catch (err) {
      console.log(err);
    }
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
    const name1 = req.body.name1 ? req.body.name1.trim() : '';
    const name2 = req.body.name2 ? req.body.name2.trim() : '';
    const name3 = req.body.name3 ? req.body.name3.trim() : '';

    if (name1 !== '' && displayNames.list1.includes(getLowerCaseName(name1))) {
      nameCount++;
    } else if (name1 !== '') {
      nameCount = -100;
    }

    if (name2 !== '' && displayNames.list2.includes(getLowerCaseName(name2))) {
      nameCount++;
    } else if (name2 !== '') {
      nameCount = -100;
    }

    if (name3 !== '' && displayNames.list3.includes(getLowerCaseName(name3))) {
      nameCount++;
    } else if (name3 !== '') {
      nameCount = -100;
    }

    const username = req.body.username ? req.body.username.trim() : '';
    const password = req.body.password;
    const confirmPassword = req.body.confirm;
    const forgotPhrase = req.body.forgot ? req.body.forgot.trim() : '';

    if (
      username !== '' &&
      password !== '' &&
      forgotPhrase !== '' &&
      nameCount >= 2 && // Require at least 2 valid parts for the display name
      password === confirmPassword
    ) {
      var namesForDisplay = [name1, name2, name3].filter(n => n !== ''); // Filter out empty parts for display name construction

      postRequest
        .handleRegister(
          username,
          password,
          namesForDisplay,
          forgotPhrase,
          playerCollection
        )
        .then((result) => {
          if (result === 'usernameTaken') {
            res.redirect('/register?usernameTaken=true');
          } else if (result === 'displayNameTaken') {
            res.redirect('/register?displayNameTaken=true');
          } else if (typeof result === 'object' && result.user && result.session) { // Successful registration
            res.cookie('TEGid', result.user.TEGid);
            res.cookie('authid', result.user.authid);
            res.cookie('dname', result.user.dname);
            res.cookie('authpass', result.user.authpass);
            var date = Date.parse(result.session.expires_at);
            res.cookie('session_token', result.session.token, {
              maxAge: date.valueOf() - Date.now(),
            });
            res.cookie('logged', true);
            res.redirect(config.httpserver.url);
          } else {
            console.warn("handleRegister returned unexpected result:", result);
            res.redirect('/register?failed=true&reason=unexpected');
          }
        })
        .catch((e) => {
          console.error("Error during registration process:", e);

          res.redirect('/register?failed=true&reason=servererror');
        });
    } else {
      // Initial validation failed (empty fields, passwords don't match, invalid display name count)
      let reason = 'validation';
      if (password !== confirmPassword) reason = 'passwordmismatch';
      if (nameCount < 2 && nameCount > -100) reason = 'displaynameparts'; // if nameCount is -100, it's an invalid selection
      else if (nameCount === -100) reason = 'invaliddisplayname';

      res.redirect(`/register?failed=true&reason=${reason}`);
    }
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
    console.log(res);
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

  app.post('/service/presence/present', async (req, res) => {
    try {
      let playerFound = false;
      const options = JSON.parse(req.body.options);
      const username = req.body.username;

      for (let p of onlinePlayers) {
        if (p.username === username) {
          playerFound = true;
          p.lastChecked = Date.now();
          p.level = options.level;
          p.elo = options.elo;
          p.location = options.location;
          p.name = options.name;
          p.tier = options.tier;

          try {
            const data = await playerCollection.findOne({
              'user.TEGid': username,
            });
            if (data) {
              p.pfp = data.user?.pfp;
              p.friends = data.friends;
            } else {
              console.log(
                `Player ${username} found online but does not exist in DB during update`
              );
            }
          } catch (dbError) {
            console.error(
              'Error fetching player data during presence update: ' + dbError
            );
          }
          break;
        }
      }

      if (!playerFound) {
        const data = await playerCollection.findOne({ 'user.TEGid': username });

        if (data != null) {
          const playerObj = {
            username: username,
            level: options.level,
            elo: options.elo,
            location: options.location,
            name: options.name,
            tier: options.tier,
            lastChecked: Date.now(),
            friends: data.friends || [],
            pfp: data.user?.pfp,
          };
          onlinePlayers.push(playerObj);
        } else {
          console.log(
            `New player ${username} not found in DB. Not adding to online list`
          );
        }
      }
      res.send(postRequest.handlePresent(req.body));
    } catch (e) {
      console.log('Error performing presence ' + e);
    }
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
      lobbyServer = new ATBPLobbyServer(config.lobbyserver.port);
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
