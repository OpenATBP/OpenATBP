// import { Router, Request } from 'express';

// import logger from './log';
// import type { DatabaseInterface, User } from './database/database';

// export const extractAuthToken = (req: Request) => (/Bearer (.*)/.exec(req.headers.authorization || '') || [])[1];

// export default (database: DatabaseInterface) => {
//   const router: Router = Router();

//   router.get('/auth', async (req, res) => {
//     const authToken = extractAuthToken(req);
//     if (!authToken) throw new Error('Unauthorized!');
//     logger.debug(`Admin client authenticated: ${authToken}`);
//     const data: User = await database.findUserById(authToken);
//     if (!data) throw new Error('User not found!');
//     res.send(JSON.stringify(data));
//   });

//   router.get('/user/:id', async (req, res) => {
//     const authToken = extractAuthToken(req);
//     if (!authToken) throw new Error('Unauthorized!');
//     const data: User = await database.findUserById(authToken);
//     if (!data) throw new Error('User not found!');
//     res.send(JSON.stringify(data));
//   });

//   router.post('/user/:id', async (req, res) => {
//     const authToken = extractAuthToken(req);
//     if (!authToken) throw new Error('Unauthorized!');
//     const data: User = await database.findUserById(authToken);
//     if (!data) throw new Error('User not found!');
//     res.send(JSON.stringify(data));
//   });

//   return router;
// };
