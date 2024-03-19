import * as config from './config';
import createApiServer from './server';

createApiServer(config.apiPort); // FacadeServer
