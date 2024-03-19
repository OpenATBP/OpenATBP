import type { Socket, AddressInfo } from 'node:net';

import logger from './log';
import { send } from './packet';
import * as ResponseHandler from './response';
import Queue, { State } from './queue';
import Player from './player';
import type {
  RequestPayloadLogin,
  RequestPayloadChatMessage,
  RequestPayloadAutoJoin,
  RequestPayloadSetAvatar,
  RequestPayloadSendInvite,
  RequestPayloadCreateTeam,
  RequestPayloadJoinTeam,
  RequestPayloadDeclineInvite,
} from './request.type';

const findPlayerByAddress = (address: string, players: Player[]): Player | undefined => players
  .find((player) => player.isAddress(address));

const findQueueByPlayer = (player: Player, queues: Queue[]): Queue | undefined => queues
  .find((queue) => queue.hasPlayerByUsername(player));

export const handleHandshake = (socket: Socket) => send(
  socket,
  JSON.stringify(ResponseHandler.responseHandshake(true)),
);

export const handleLogin = (
  socket: Socket,
  request: RequestPayloadLogin,
  players: Player[],
): void => {
  const { address } = socket.address() as AddressInfo;
  if (!findPlayerByAddress(address, players)) {
    if (request.payload.name.includes('Guest')) {
      // players.add(new Player(socket,guestNum)); // Adds logged player in to server's player list
      // console.log(`Guest joined! ${guestNum}`);
      send(socket, JSON.stringify(ResponseHandler.responseHandshake(false)));
    } else {
      const player = new Player(socket, request.payload);
      players.push(player); // Adds logged player in to server's player list
      const guestNum: number = players.filter((p) => p.getUsername().includes('Guest')).length;
      player.send(ResponseHandler.responseLogin(request.payload, guestNum));
    }
  }
};

export const handleAutoJoin = (
  socket: Socket,
  request: RequestPayloadAutoJoin,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  if (requestingPlayer) {
    if (queues.length === 0) { // If there are no current queues
      queues.push(new Queue(
        [requestingPlayer],
        request.payload.act,
        request.payload.vs,
      )); // Creates new queue with player in it
      logger.debug(`New queue size: ${queues.length}`);
    } else { // If there are active queues
      let tries: number = 0;
      for (let i = 0; i < queues.length; i += 1) {
        // If the queue is not currently a premade, has enough open spots to fit the player, and is the correct game type
        if (queues[i]!.getState() === State.MATCHMAKING
          && queues[i]!.getSize() + 1 <= 6
          && queues[i]!.getType() === request.payload.act
          && queues[i]!.isPvP() === request.payload.vs) {
          queues[i]!.addPlayer(requestingPlayer); // Adds player to the queue
          requestingPlayer.setQueue(queues[i]!);
          break;
        } else {
          tries += 1;
        }
      }
      if (tries === queues.length) { // If there are no queues the player can join, it'll make a new one
        // TODO: Needs to account for multiple queues that could join together
        queues.push(new Queue(
          [requestingPlayer],
          request.payload.act,
          request.payload.vs,
        ));
        logger.debug('No existing type - New queue created');
      }
    }
  }
};

export const handleSetAvatar = (
  socket: Socket,
  request: RequestPayloadSetAvatar,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  if (requestingPlayer) {
    const affectedQueue: Queue | undefined = findQueueByPlayer(requestingPlayer, queues);
    requestingPlayer.setAvatar(request.payload.name); // Updates player character
    if (affectedQueue) affectedQueue.updatePlayer(requestingPlayer); // Updates queue with new player info
  }
};

export const handleSetReady = (socket: Socket, players: Player[], queues: Queue[]): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  if (requestingPlayer) {
    const affectedQueue: Queue | undefined = findQueueByPlayer(requestingPlayer, queues);
    requestingPlayer.setReady(); // Updates player ready status
    if (affectedQueue) affectedQueue.updatePlayer(requestingPlayer);
  }
};

export const handleLeaveTeam = (socket: Socket, players: Player[], queues: Queue[]): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  if (requestingPlayer) {
    const affectedQueue: Queue | undefined = findQueueByPlayer(requestingPlayer!, queues);
    if (affectedQueue) {
      requestingPlayer.leaveTeam();
      affectedQueue.removePlayer(requestingPlayer);
      if (affectedQueue.isPremade()) { // If the queue is a premade team
        affectedQueue.getPartyLeader()!
          .send(ResponseHandler.responseInviteDecline(requestingPlayer.getUsername())); // Sends invite decline so host can reinvite
      }
      if (affectedQueue.getSize() === 0) { // If this was the last player in the team/queue, disbands the queue
        const affectedQueueIndex: number = queues
          .findIndex((queue) => queue === affectedQueue);
        queues.splice(affectedQueueIndex, 1);
        logger.debug('Removed queue!');
      }
    }
  }
};

export const handleSendInvite = (
  socket: Socket,
  request: RequestPayloadSendInvite,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  // Finds player getting the invite
  const receivingPlayer: Player | undefined = players
    .find((player) => player.getUsername().toLowerCase() === request.payload.player.toLowerCase());
  if (requestingPlayer) {
    const affectedQueue: Queue | undefined = findQueueByPlayer(requestingPlayer, queues);
    if (receivingPlayer && affectedQueue) {
      receivingPlayer.send(ResponseHandler.responseInvite(affectedQueue)); // Sends the player the invite to team
    }
  }
};

export const handleCreateTeam = (
  socket: Socket,
  request: RequestPayloadCreateTeam,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  queues.push(new Queue(
    [requestingPlayer!],
    request.payload.act,
    request.payload.vs,
    true,
  )); // Adds a new queue that is a premade
};

export const handleJoinTeam = (
  socket: Socket,
  request: RequestPayloadJoinTeam,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  if (requestingPlayer) requestingPlayer.setTeam(request.payload.name);
  const queue: Queue | undefined = queues
    .find((q) => q.getPartyLeader()?.getUsername()
      .toLowerCase() === request.payload.name);
  if (queue && requestingPlayer) { // Finds queue/team correlated with the invite request
    requestingPlayer.send(ResponseHandler.responseInviteAccept());
    queue.addPlayer(requestingPlayer);
  }
};

export const handleDeclineInvite = (
  socket: Socket,
  request: RequestPayloadDeclineInvite,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const requestingPlayer: Player | undefined = findPlayerByAddress(address, players);
  if (requestingPlayer) {
    const queue: Queue | undefined = queues
      .find((q) => q.getPartyLeader()?.getPid() === request.payload.party_leader);
    if (queue && requestingPlayer) {
      queue.getPartyLeader()!
        .send(ResponseHandler.responseInviteDecline(requestingPlayer.getUsername()));
    }
  }
};

export const handleUnlockTeam = (socket: Socket, queues: Queue[]): void => {
  const { address } = socket.address() as AddressInfo;
  const currentQueue: Queue | undefined = queues.find((queue) => queue.hasPlayerByAddress(address));
  if (currentQueue) {
    let tries: number = 0;
    for (let i = 0; i < queues.length; i += 1) {
      if (!queues[i]!.isPremade()) { // If the queue is not a premade and matches gamemode it will add all players in the team
        if (queues[i]!.getType() === currentQueue.getType()
          && queues[i]!.isPvP() === currentQueue.isPvP()
          && queues[i]!.getSize() + currentQueue.getSize() <= 6) { // If the queue can fit all players in the party, adds everyone
          queues[i]!.addPlayers(currentQueue.getPlayers());
          const currentQueueIndex: number = queues.findIndex((queue) => queue === currentQueue);
          queues.splice(currentQueueIndex, 1); // Removes the premade queue as it combined into another queue
          break;
        } else {
          tries += 1;
        }
      } else {
        tries += 1;
      }
    }
    if (tries === queues.length) { // If no queue fits the search criteria, it will create a new one
      queues.push(new Queue(
        currentQueue.getPlayers(),
        currentQueue.getType(),
        currentQueue.isPvP(),
      ));
      const currentQueueIndex: number = queues.findIndex((queue) => queue === currentQueue);
      queues.splice(currentQueueIndex, 1); // Removes the premade queue as it is now public
    }
  }
};

export const handleChatMessage = (
  socket: Socket,
  request: RequestPayloadChatMessage,
  players: Player[],
  queues: Queue[],
): void => {
  const { address } = socket.address() as AddressInfo;
  const chatter: Player | undefined = findPlayerByAddress(address, players);
  if (chatter) {
    const queue: Queue | undefined = findQueueByPlayer(chatter, queues);
    if (queue && queue.getState() !== State.MATCHMAKING) {
      queue.getPlayers()
        .filter((player) => player.getTeam()?.toLowerCase() === chatter.getTeam()?.toLowerCase())
        .forEach((player) => player
          .send(ResponseHandler.responseChatMessage(chatter, request.payload.message_id)));
    }
  }
};
