import { createServer } from 'node:net';
import type { Server } from 'node:net';

import logger from './log';
import clientWorker from './worker';
import type Player from './player';
import type Queue from './queue';

export const setupServer = (): Server => {
  const players: Player[] = [];
  const queues: Queue[] = [];
  const server: Server = createServer((socket) => clientWorker(socket, players, queues));
  return server;
};

export default (port: number): void => {
  try {
    const server: Server = setupServer();
    server.listen(port, () => logger.info(`DungeonServer running on port ${port}`));
  } catch (ex) {
    logger.debug(`DungeonServer could not listen on port ${port}`);
    logger.error(ex);
    process.exit(-1);
  }
};
