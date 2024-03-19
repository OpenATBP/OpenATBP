export type ResponsePayload = ResponsePayloadHandshake | ResponsePayloadLogin
| ResponsePayloadMatchFound | ResponsePayloadTeamUpdate | ResponsePayloadQueueUpdate
| ResponsePayloadGameReady | ResponsePayloadInvite | ResponsePayloadTeamJoin
| ResponsePayloadInviteAccept | ResponsePayloadInviteDecline | ResponsePayloadDisband
| ResponsePayloadChatMessage;

export type ResponsePayloadHandshake = {
  cmd: 'handshake',
  payload: {
    result: boolean,
  },
};

export type ResponsePayloadLogin = {
  cmd: 'login',
  payload: {
    player: number,
    teg_id: string,
    name: string,
  },
};

export type ResponsePayloadMatchFound = {
  cmd: 'match_found',
  payload: {
    countdown: number,
  },
};

export type ResponsePayloadTeamUpdate = {
  cmd: 'team_update',
  payload: {
    players: any[],
    team: string,
  },
};

export type ResponsePayloadQueueUpdate = {
  cmd: 'queue_update',
  payload: {
    size: number,
  },
};

export type ResponsePayloadGameReady = {
  cmd: 'game_ready',
  payload: {
    countdown: number,
    ip: string,
    port: number,
    policy_port: number,
    room_id: string,
    team: string,
    password: string,
  },
};

export type ResponsePayloadInvite = {
  cmd: 'receive_invite',
  payload: {
    name: string,
    player: number,
    act: string,
    vs: boolean,
    team: string,
  },
};

export type ResponsePayloadTeamJoin = ResponsePayloadTeamUpdate;

export type ResponsePayloadInviteAccept = {
  cmd: 'invite_verified',
  payload: {
    result: string,
  },
};

export type ResponsePayloadInviteDecline = {
  cmd: 'invite_declined',
  payload: {
    player: string,
  },
};

export type ResponsePayloadDisband = {
  cmd: 'team_disband',
  payload: {
    reason: string,
  },
};

export type ResponsePayloadChatMessage = {
  cmd: 'chat_message',
  payload: {
    name: string,
    teg_id: string,
    message_id: number,
  },
};
