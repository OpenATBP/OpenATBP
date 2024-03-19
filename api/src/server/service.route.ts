import { Router } from 'express';

import logger from './log';
import shopData from './data/shop.json';
import type { DatabaseInterface, User } from './database/database';

export default (database: DatabaseInterface) => {
  const router: Router = Router();

  const onlinePlayers: any[] = [];
  // var onlineChecker = setInterval(() => {
  //   logger.debug("Checking online users!");
  //   for (var p of onlinePlayers) {
  //     if (Date.now() - p.lastChecked > 10000) {
  //       logger.debug(p.username + " offline!");
  //       onlinePlayers = onlinePlayers.filter((i) => i.username != p.username);
  //       // logger.debug(onlinePlayers);
  //     } else {
  //       logger.debug(p.username + " online!");
  //       // logger.debug(onlinePlayers);
  //     }
  //   }
  // }, 11000);

  // /service/presence/present
  router.get('/presence/present', (_, res) => res.send(JSON.stringify({})));

  // /service/authenticate/whoami?authToken={data.token} RETURNS displayName
  router.get('/authenticate/whoami', async (req, res) => {
    const { authToken } = req.query;
    logger.debug(`AuthToken: ${authToken}`);
    const data = await database.findUserByAuthToken(`${authToken}`);
    if (data === null) throw new Error('User not found!');
    res.send(JSON.stringify({ displayName: data.user.dname }));
  });

  // /service/data/user/champions/tournament?authToken={data.token} Useless unless we do a tournament
  router.get('/data/user/champions/tournament', (_, res) => res
    .send(JSON.stringify({ tournamentData: {} })));

  // /service/shop/inventory RETURNS all inventory data
  router.get('/shop/inventory', (_, res) => res.send(JSON.stringify(shopData)));

  // /service/data/config/champions/ Not sure if this is what it should be returning or not.
  router.get('/data/config/champions/', (_, res) => res.send(JSON.stringify({
    upperELO: 2000.0,
    // This changes the tiers that change your icon based on elo. 500 marks bronze and 2000 is burple
    eloTier: ['1', '100', '200', '500'],
  })));

  // /service/shop/player?authToken={data.token} RETURNS player inventory from db
  router.get('/shop/player', async (req, res) => {
    const { authToken } = req.query;
    const data: User = await database.findUserByAuthToken(`${authToken}`);
    if (data === null) throw new Error('User not found!');
    res.send(JSON.stringify(data.inventory));
  });

  // /service/data/user/champions/profile?authToken={data} RETURNS player info from db
  router.get('/data/user/champions/profile', async (req, res) => {
    const { authToken } = req.query;
    logger.debug(`Getting data for: ${authToken}`);
    const data: User = await database.findUserByAuthToken(`${authToken}`);
    if (data === null) throw new Error('User not found!');
    res.send(JSON.stringify(data.player));
  });

  // /service/presence/roster/{TEGiid} RETURNS friends list from db
  router.get('/presence/roster/:username', (req, res) => {
    const friendsList: string[] = (onlinePlayers
      .find((p) => p.username === req.params.username) || {}).friends || [];
    const friends = friendsList.map((name) => {
      const player = onlinePlayers.find((p) => p.username === name) || {};
      if (player) {
        return {
          user_id: name,
          name: player.name,
          avatar: 'lich.png',
          options: {
            location: player.location,
            game: 'ATBP',
            tier: player.tier,
            level: player.level,
            elo: player.elo,
          },
        };
      }
      return undefined;
    })
      .filter((p) => !!p);
    res.send(JSON.stringify({ roster: friends }));
  });

  // /authenticate/user/{username} RETURNS username from database
  router.get('/authenticate/user/:username', async (req, res) => {
    const data: User = await database.findUserById(req.params.username);
    res.send(JSON.stringify(data));
  });

  // /service/authenticate/login PROVIDES authToken [pid,TEGid,authid,authpass] RETURNS authToken.text={authToken}
  router.post('/authenticate/login', async (req, res) => {
    logger.debug(`Auth ID: ${req.body.authToken.authid}`);
    const data: User = await database.findUserByUsername(`${req.body.authToken.authid}`);
    if (data === null) throw new Error('User not found!');
    res.send(JSON.stringify({ authToken: { text: data.authToken } }));
  });

  // /service/presence/present PROVIDES username, property, level, elo, location, displayName, game, and tier
  router.post('/presence/present', async (req, res) => {
    logger.debug(req.body);
    let test = 0;
    const options = JSON.parse(req.body.options);
    for (let i = 0; i < onlinePlayers.length; i += 1) {
      const p = onlinePlayers[i];
      if (p.username !== req.body.username) {
        test += 1;
      } else {
        logger.debug(Date.now() - p.lastChecked);
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
    if (test === onlinePlayers.length) {
      logger.debug('Pushed!');
      const playerObj = {
        username: req.body.username,
        level: options.level,
        elo: options.level,
        location: options.location,
        name: options.name,
        tier: options.tier,
        lastChecked: Date.now(),
        friends: [] as string[],
      };
      const data: User = await database.findUserById(req.body.username);
      playerObj.friends = data.friends;
      onlinePlayers.push(playerObj);
    } else {
      logger.debug(`Players: ${onlinePlayers.length} vs ${test}`);
    }
    res.send(JSON.stringify({}));
  });

  // /service/shop/purchase?authToken={token} PROVIDES authToken RETURNS success object
  router.post('/shop/purchase', async (req, res) => {
    const { authToken } = req.query;
    const itemToPurchase = req.body.data.item;
    const foundItem = shopData.find((item) => item.id === itemToPurchase);
    if (!foundItem) throw new Error('Item not found');
    await database.purchaseItem(`${authToken}`, itemToPurchase, foundItem.cost);
    res.send(JSON.stringify({ success: 'true' }));
  });

  router.post('/authenticate/user/:username', async (req, res) => {
    const data: User = await database
      .createNewUser(req.params.username, req.params.username, req.body.password);
    res.send(JSON.stringify(data));
  });

  router.post('/friend/request', async (req, res) => {
    await database.addFriend(`${req.query.authToken}`, req.body.toUserId);
    res.send(JSON.stringify({}));
  });

  return router;
};
