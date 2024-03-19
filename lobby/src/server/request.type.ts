export type RequestPayload = RequestPayloadHandshake | RequestPayloadLogin | RequestPayloadAutoJoin
| RequestPayloadSetAvatar | RequestPayloadSetReady | RequestPayloadLeaveTeam
| RequestPayloadSendInvite | RequestPayloadCreateTeam | RequestPayloadJoinTeam
| RequestPayloadDeclineInvite | RequestPayloadUnlockTeam | RequestPayloadChatMessage;

export type RequestPayloadHandshake = {
  type: 'handshake',
};

export type RequestPayloadLogin = {
  type: 'login',
  payload: {
    name: string,
    auth_id: number,
    teg_id: string,
  },
};

export type RequestPayloadAutoJoin = {
  type: 'auto_join',
  payload: {
    act: string,
    vs: boolean,
  },
};

export type RequestPayloadSetAvatar = {
  type: 'set_avatar',
  payload: {
    name: string,
  },
};

export type RequestPayloadSetReady = {
  type: 'set_ready',
};

export type RequestPayloadLeaveTeam = {
  type: 'leave_team',
};

export type RequestPayloadSendInvite = {
  type: 'send_invite',
  payload: {
    player: string,
  },
};

export type RequestPayloadCreateTeam = {
  type: 'create_team',
  payload: {
    act: string,
    vs: boolean,
  },
};

export type RequestPayloadJoinTeam = {
  type: 'join_team',
  payload: {
    name: string,
  },
};

export type RequestPayloadDeclineInvite = {
  type: 'decline_invite',
  payload: {
    party_leader: number,
  },
};

export type RequestPayloadUnlockTeam = {
  type: 'unlock_team',
};

export type RequestPayloadChatMessage = {
  type: 'chat_message',
  payload: {
    message_id: string,
  },
};
