import type { Socket, AddressInfo } from 'node:net';

import logger from './log';
import { DataBuffer } from './packet';
import * as RequestHandler from './request';
import * as ResponseHandler from './response';
import { State } from './queue';
import type Queue from './queue';
import type Player from './player';
import type { RequestPayload } from './request.type';

const processRequest = (
  socket: Socket,
  request: RequestPayload,
  players: Player[],
  queues: Queue[],
) => {
  logger.debug(`Received ${request.type}`);
  logger.debug(`Payload ${JSON.stringify((request as any).payload)}`);
  switch (request.type) {
    case 'handshake':
      RequestHandler.handleHandshake(socket);
      break;
    case 'login':
      RequestHandler.handleLogin(socket, request, players);
      break;
    case 'auto_join':
      RequestHandler.handleAutoJoin(socket, request, players, queues);
      break;
    case 'set_avatar':
      RequestHandler.handleSetAvatar(socket, request, players, queues);
      break;
    case 'set_ready':
      RequestHandler.handleSetReady(socket, players, queues);
      break;
    case 'leave_team':
      RequestHandler.handleLeaveTeam(socket, players, queues);
      break;
    case 'send_invite':
      RequestHandler.handleSendInvite(socket, request, players, queues);
      break;
    case 'create_team':
      RequestHandler.handleCreateTeam(socket, request, players, queues);
      break;
    case 'join_team':
      RequestHandler.handleJoinTeam(socket, request, players, queues);
      break;
    case 'decline_invite':
      RequestHandler.handleDeclineInvite(socket, request, players, queues);
      break;
    case 'unlock_team':
      RequestHandler.handleUnlockTeam(socket, queues);
      break;
    case 'chat_message':
      RequestHandler.handleChatMessage(socket, request, players, queues);
      break;
    default:
      break;
  }
};

// TODO: Communicate with database to track queue dodges
const close = (socket: Socket, players: Player[], queues: Queue[]): void => {
  try {
    const { address } = socket.address() as AddressInfo;
    logger.debug(`Dropping client: ${address}`);
    const droppedPlayer: Player | undefined = players.find((player) => player.isAddress(address));
    if (!droppedPlayer) throw new Error('Player not found');
    const affectedQueue: Queue | undefined = queues
      .find((queue) => queue.hasPlayerByUsername(droppedPlayer));
    if (!affectedQueue) throw new Error('Queue not found');
    // Removes players from queues when they disconnect from the lobby server
    affectedQueue.removePlayer(droppedPlayer);
    if (affectedQueue.getState() === State.TEAM_BUILDING) {
      // Not sure if this is needed but did this to decline any outstanding invites to allow host to reinivte
      affectedQueue.getPartyLeader()!
        .send(ResponseHandler.responseInviteDecline(droppedPlayer.getUsername()));
    }
    if (affectedQueue.getSize() === 0) {
      const affectedQueueIndex: number = queues.findIndex((queue) => queue === affectedQueue);
      queues.splice(affectedQueueIndex, 1);
      logger.debug('Removed queue!');
    }
    const droppedPlayerIndex: number = players
      .findIndex((player) => player.getUsername().toLowerCase() === droppedPlayer!
        .getUsername().toLowerCase() && player.getPid() === droppedPlayer!.getPid());
    if (droppedPlayerIndex !== -1) players.splice(droppedPlayerIndex, 1); // Removes from server's players list
    logger.debug('Player removed!');
    socket.end();
    socket.destroy();
  } catch (e: any) {
    logger.error(`Error handling player disconnect: ${e.message}`);
  }
};

export default (socket: Socket, players: Player[], queues: Queue[]) => {
  // socket.setEncoding('utf8'); // must not set encoding, or data is string instead of buffer
  // socket.setTimeout(10000);
  socket.on('error', (error) => logger.debug(`Socket error: ${error.message}`));
  socket.on('timeout', () => logger.debug('Socket timed out!'));
  socket.on('end', (data: any) => logger.debug(`Socket ended from other end! ${data}`));
  socket.on('close', () => close(socket, players, queues));
  logger.info(`New client: ${(socket.address() as AddressInfo).address}`);
  const dataBuffer = new DataBuffer((message) => {
    try {
      players.forEach((player) => logger.debug(`Player: ${player.getUsername()} in players list`));
      queues.forEach((queue) => {
        logger.debug(`${queue.getPartyLeader()} queue is active`);
        queue.getPlayers().forEach((player) => logger.debug(`Queue ${queue.getPartyLeader()} has player ${player.getUsername()}`));
      });
      const parsedMessage = JSON.parse(message);
      if (!parsedMessage || !parsedMessage.req) throw new Error('Invalid message format');
      const request: RequestPayload = {
        type: parsedMessage.req.toLowerCase(),
        payload: parsedMessage.payload,
      };
      processRequest(socket, request, players, queues);
    } catch (e: any) {
      logger.error(`Error processing message: ${e.message}`);
    }
  });
  socket.on('data', (data) => {
    logger.debug(`Data received! ${data.length}`);
    dataBuffer.add(data);
  });
};
