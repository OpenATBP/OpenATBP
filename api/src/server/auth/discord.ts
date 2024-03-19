import { Router } from 'express';

import * as config from '../config';
import logger from '../log';
import type { DatabaseInterface, User } from '../database/database';

export default (database: DatabaseInterface) => {
  const router: Router = Router();

  router.get('/login', async (_, res) => {
    res.redirect(`https://discord.com/api/oauth2/authorize?client_id=${config.authClientId}&redirect_uri=${config.authRedirectUrl}&response_type=code&scope=identify`);
  });

  router.get('/', async (req, res) => {
    try {
      const { code } = req.query;
      if (!code) throw new Error('Invalid code!');
      const tokenResult = await fetch('https://discord.com/api/oauth2/token', {
        method: 'POST',
        body: new URLSearchParams({
          client_id: config.authClientId,
          client_secret: config.authClientSecret,
          code: `${code}`,
          grant_type: 'authorization_code',
          redirect_uri: `${config.apiUrl}/auth/`,
          scope: 'identify',
        }).toString(),
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      });
      const tokenData: any = await tokenResult.json();
      logger.debug(tokenData);
      if (!tokenData) throw new Error('Invalid token data!');
      const userResult = await fetch('https://discord.com/api/users/@me', {
        headers: {
          authorization: `${tokenData.token_type} ${tokenData.access_token}`,
        },
      });
      const userData: any = await userResult.json();
      logger.debug(userData);
      if (!userData) throw new Error('Invalid user info!');
      const data: User = await database.findUserByUsername(userData.id);
      res.cookie('TEGid', data.user.TEGid);
      res.cookie('authid', data.user.authid);
      res.cookie('dname', data.user.dname);
      res.cookie('authpass', data.user.authpass);
      res.cookie('logged', true);
      // res.redirect(config.httpserver.url);
    } catch (e: any) {
      logger.debug(e.message);
      res.redirect(config.apiUrl);
    }
  });

  return router;
};
