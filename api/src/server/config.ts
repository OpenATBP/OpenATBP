const getEnvString = (
  envVarName: string,
  defaultValue: string,
) => process.env[envVarName] || defaultValue;

const getEnvNumber = (
  envVarName: string,
  defaultValue: number,
) => parseInt(`${process.env[envVarName] || defaultValue}`, 10);

const getEnvBoolean = (
  envVarName: string,
  defaultValue: boolean,
) => (defaultValue
  ? (process.env[envVarName] || '').toLowerCase() !== 'false'
  : (process.env[envVarName] || '').toLowerCase() === 'true');

export const logLevel: string = getEnvString('LOG_LEVEL', 'debug');

export const apiUrl: string = getEnvString('API_URL', 'http://127.0.0.1');
export const apiPort: number = getEnvNumber('API_PORT', 8080);

export const assetsUrl: string = getEnvString('ASSETS_URL', 'http://127.0.0.1');
export const assetsPort: number = getEnvNumber('ASSETS_PORT', 8081);

export const gameUrl: string = getEnvString('GAME_URL', '127.0.0.1');
export const gamePort: number = getEnvNumber('GAME_PORT', 9933);

export const lobbyUrl: string = getEnvString('LOBBY_URL', '127.0.0.1');
export const lobbyPort: number = getEnvNumber('LOBBY_PORT', 6778);

export const sockpolUrl: string = getEnvString('SOCKPOL_URL', '127.0.0.1');
export const sockpolPort: number = getEnvNumber('SOCKPOL_PORT', 843);
export const sockpolPolicies: string = getEnvString('SOCKPOL_POLICIES', 'master-only');
export const sockpolDomain: string = getEnvString('SOCKPOL_DOMAIN', '*');
export const sockpolHeaders: string = getEnvString('SOCKPOL_HEADERS', '*');

export const databaseType: string = getEnvString('API_DATABASE_TYPE', 'memory');
export const databaseUrl: string = getEnvString('API_DATABASE_URL', 'mongodb://127.0.0.1:27017');
export const databaseSSL: boolean = getEnvBoolean('API_DATABASE_SSL', true);

export const authType: string = getEnvString('API_AUTH_TYPE', 'open');
export const authClientId: string = getEnvString('API_AUTH_CLIENT_ID', '');
export const authClientSecret: string = getEnvString('API_AUTH_CLIENT_SECRET', '');
export const authOauthUrl: string = getEnvString('API_AUTH_OAUTH_URL', 'https://discord.com/api/oauth2/authorize?client_id=&redirect_uri=');
export const authRedirectUrl: string = getEnvString('API_AUTH_REDIRECT_URL', 'http://127.0.0.1:8080/auth');
