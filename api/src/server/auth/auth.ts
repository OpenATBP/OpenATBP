import type { Router } from 'express';

import discordAuth from './discord';
import openAuth from './open';
import type { DatabaseInterface } from '../database/database';

export const getAuth = (authType: string, database: DatabaseInterface): Router => {
  switch (authType.toLowerCase()) {
    case 'discord':
      return discordAuth(database);
    case 'open':
    default:
      return openAuth(database);
  }
};

export type AuthInterface = (database: DatabaseInterface) => Router;
