import { Router } from 'express';

import logger from '../log';
import namesData from '../data/names.json';
import type { DatabaseInterface, User } from '../database/database';

export default (database: DatabaseInterface) => {
  const router: Router = Router();

  router.get('/login', async (_, res) => {
    const name = [
      namesData.list1[Math.floor(Math.random() * namesData.list1.length)],
      namesData.list2[Math.floor(Math.random() * namesData.list2.length)],
      namesData.list3[Math.floor(Math.random() * namesData.list3.length)],
    ].join(' ');
    logger.debug(`Login: ${name}`);
    const user: User = await database.createNewUser(name, name, 'open');
    res.cookie('TEGid', user.user.TEGid); // random id
    res.cookie('authid', user.user.authid); // username
    res.cookie('dname', user.user.dname); // display name
    res.cookie('authpass', user.user.authpass);
    res.cookie('logged', true);
  });

  return router;
};
