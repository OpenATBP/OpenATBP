import pino from 'pino';
import pinoPretty from 'pino-pretty';

import * as config from './config';

export default pino({
  level: config.logLevel,
  timestamp: process.stdout.isTTY,
}, pinoPretty({
  colorize: process.stdout.isTTY,
  translateTime: process.stdout.isTTY,
  ignore: [...(process.stdout.isTTY ? [] : ['time']), 'name', 'pid', 'hostname'].join(','),
}));
