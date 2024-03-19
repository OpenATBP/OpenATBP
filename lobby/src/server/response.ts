import * as config from './config';
import type Player from './player';
import type { PlayerObject } from './player';
import type Queue from './queue';
import type {
  ResponsePayloadHandshake,
  ResponsePayloadLogin,
  ResponsePayloadMatchFound,
  ResponsePayloadTeamUpdate,
  ResponsePayloadQueueUpdate,
  ResponsePayloadGameReady,
  ResponsePayloadInvite,
  ResponsePayloadTeamJoin,
  ResponsePayloadInviteAccept,
  ResponsePayloadInviteDecline,
  ResponsePayloadDisband,
  ResponsePayloadChatMessage,
} from './response.type';

export const responseHandshake = (successful: boolean): ResponsePayloadHandshake => ({
  cmd: 'handshake',
  payload: {
    result: successful,
  },
});

export const responseLogin = (playerObj: PlayerObject, guestNum: number): ResponsePayloadLogin => ({
  cmd: 'login',
  payload: {
    player: (playerObj.name as string || '').includes('Guest') ? guestNum : playerObj.auth_id,
    teg_id: playerObj.teg_id,
    name: (playerObj.name as string || '').replace(/%20/, ' '),
  },
});

export const responseMatchFound = (countdown?: number): ResponsePayloadMatchFound => ({
  cmd: 'match_found',
  payload: {
    countdown: countdown ?? 30,
  },
});

export const responseTeamUpdate = (team: any[], teamStr: string): ResponsePayloadTeamUpdate => ({
  cmd: 'team_update',
  payload: {
    players: team,
    team: teamStr,
  },
});

export const responseQueueUpdate = (size: number): ResponsePayloadQueueUpdate => ({
  cmd: 'queue_update',
  payload: {
    size: Math.min(size, 3),
  },
});

export const responseGameReady = (
  partyLeader: Player,
  teamStr: string,
  type: string,
): ResponsePayloadGameReady => ({
  cmd: 'game_ready',
  payload: {
    countdown: 5,
    ip: config.gameUrl,
    port: config.gamePort,
    policy_port: config.sockpolPort,
    room_id: `${type.split('_')[type.split('_').length - 1]}_${partyLeader.getUsername().toLowerCase() === 'guest'
      ? `Guest${partyLeader.getPid()}`
      : partyLeader.getUsername()}`,
    team: teamStr, // Change based on players
    password: '',
  },
});

export const responseInvite = (queue: Queue): ResponsePayloadInvite => ({
  cmd: 'receive_invite',
  payload: {
    name: queue.getPartyLeader()!.getName(),
    player: queue.getPartyLeader()!.getPid(),
    act: queue.getType(),
    vs: queue.isPvP(),
    team: queue.getPartyLeader()!.getUsername(),
  },
});

export const responseTeamJoin = (queue: Queue): ResponsePayloadTeamJoin => ({
  cmd: 'team_update',
  payload: {
    players: queue.getPlayerObjects(),
    team: queue.getPartyLeader()!.getUsername(),
  },
});

export const responseInviteAccept = (): ResponsePayloadInviteAccept => ({
  cmd: 'invite_verified',
  payload: {
    result: 'success',
  },
});

export const responseInviteDecline = (decliner: string): ResponsePayloadInviteDecline => ({
  cmd: 'invite_declined',
  payload: {
    player: decliner,
  },
});

export const responseDisband = (reason?: string): ResponsePayloadDisband => ({
  cmd: 'team_disband',
  payload: {
    reason: reason ?? 'disconnect',
  },
});

export const responseChatMessage = (
  player: Player,
  message: string,
): ResponsePayloadChatMessage => ({
  cmd: 'chat_message',
  payload: {
    name: player.getName(),
    teg_id: player.getUsername(),
    message_id: parseFloat(message),
  },
});
