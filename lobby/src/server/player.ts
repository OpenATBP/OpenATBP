import type { Socket, AddressInfo } from 'node:net';

import { send } from './packet';
import type Queue from './queue';
import type { ResponsePayload } from './response.type';

export type PlayerObject = {
  name: string,
  auth_id: number,
  teg_id: string,
};

export default class {
  private pid: number;

  private name: string;

  private avatar: string;

  private tegid: string;

  private ready: boolean;

  private socket: Socket;

  private team: string | undefined;

  private queue: Queue | undefined;

  constructor(socket: Socket, playerOrGuestNum: PlayerObject | number) {
    this.socket = socket;
    this.avatar = 'unassigned';
    this.ready = false;
    this.team = undefined;
    this.queue = undefined;
    if (typeof playerOrGuestNum === 'number') {
      this.pid = playerOrGuestNum;
      this.name = 'Guest';
      this.tegid = 'Guest';
    } else {
      this.pid = playerOrGuestNum.auth_id;
      this.name = playerOrGuestNum.name;
      this.tegid = playerOrGuestNum.teg_id;
    }
  }

  public toObject = () => ({
    name: this.name,
    player: this.pid,
    teg_id: this.tegid,
    avatar: this.avatar,
    is_ready: this.ready,
  });

  public getPid = (): number => this.pid;

  public getName = (): string => this.name;

  public getAvatar = (): string => this.avatar;

  public getUsername = (): string => this.tegid;

  public getReady = (): boolean => this.ready;

  public getOutputStream = (): Socket => this.socket;

  public getAddress = (): string => (this.socket.address() as AddressInfo).address;

  public getTeam = (): string | undefined => this.team;

  public getQueue = (): Queue | undefined => this.queue;

  public setAvatar = (avatar: string) => { this.avatar = avatar; };

  public setReady = (ready: boolean = true) => { this.ready = ready; };

  public setTeam = (team: string) => { this.team = team; };

  public setQueue = (q: Queue) => { this.queue = q; };

  public isReady = (): boolean => this.getReady();

  public isAddress = (address: string): boolean => this.getAddress() === address;

  public leaveTeam = () => {
    this.avatar = 'unassigned';
    this.ready = false;
    this.team = undefined;
    this.queue = undefined;
  };

  public send = (payload: ResponsePayload) => send(this.socket, JSON.stringify(payload));
}
