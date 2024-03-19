import express from 'express';
import bodyParser from 'body-parser';
import type { Express } from 'express';

import * as config from './config';
import logger from './log';
import { getDatabase } from './database/database';
import { getAuth } from './auth/auth';
import serviceRouter from './service.route';
// import adminRouter from './admin.route';
import type { DatabaseInterface } from './database/database';

export const setupServer = (): Express => {
  const app: Express = express();
  const database: DatabaseInterface = getDatabase(config.databaseType);

  app.use(bodyParser.urlencoded({ extended: false }));
  app.use(bodyParser.json());

  // / RENDERS index page
  app.get('/', (_, res) => res.render('index'));

  // /service ROUTES service endpoints
  app.use('/service', serviceRouter(database));

  // /admin ROUTES admin endpoints
  // app.use('/admin', adminRouter(database));

  // /auth ROUTES auth endpoints
  app.use('/auth', getAuth(config.authType, database));

  return app;
};

export default (port: number): void => {
  const server: Express = setupServer();

  server.listen(port, () => logger.info(`FacadeServer running on port ${port}!`));
};
