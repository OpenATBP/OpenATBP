import * as ResponseHandler from './response';
import type Player from './player';

export enum State {
  MATCHMAKING,
  CHAMP_SELECT,
  IN_GAME,
  TEAM_BUILDING,
}

export default class {
  private players: Player[];

  private type: string;

  private state: State;

  private pvp: boolean;

  private premade: boolean;

  private partyLeader: Player;

  constructor(players: Player[], type: string, pvp: boolean, premade: boolean = false) {
    this.players = players;
    this.state = premade ? State.TEAM_BUILDING : State.MATCHMAKING;
    this.type = type;
    this.pvp = pvp;
    this.premade = premade;
    this.partyLeader = players[0]!;
    if (['m_moba_practice', 'm_moba_tutorial'].includes(type.toLowerCase())
      || type.includes('1p')
      || players.length === 6) this.queueFull();
  }

  public getPlayers = (): Player[] => this.players;

  public getType = (): string => this.type;

  public isPvP = (): boolean => this.pvp;

  public getPartyLeader = (): Player | undefined => (this.premade ? this.partyLeader : undefined);

  public isPremade = (): boolean => this.premade;

  public getState = (): State => this.state;

  public hasPlayerByUsername = (p: Player): boolean => this.players
    .some((player) => player.getUsername().toLowerCase() === p.getUsername().toLowerCase());

  public hasPlayerByAddress = (address: string): boolean => this.players
    .some((player) => player.isAddress(address));

  private findPlayerIndex = (p: Player): number => this.players
    .findIndex((player) => player.getUsername().toLowerCase() === p.getUsername().toLowerCase()
      && player.getPid() === p.getPid());

  public addPlayer = (p: Player): void => {
    this.players.push(p);
    // If it's not team-building, updates the queue status to clients
    if (!this.premade) {
      this.queueUpdate();
      if (this.getSize() === 6) this.queueFull(); // Queue is full
      if (this.getSize() === 2 && this.type.includes('3p')) this.queueFull();
    } else {
      this.updatePremade(); // Updates premade team when user joins
    }
  };

  public addPlayers = (players: Player[]): void => {
    players.forEach((player) => this.players.push(player));
    this.queueUpdate(); // Updates queue GUI
    if (this.getSize() === 2) this.queueFull(); // Goes to champ select when queue is full
  };

  public removePlayer = (player: Player): void => {
    this.players.splice(this.findPlayerIndex(player), 1);
    if (this.players.length > 0 && this.state === State.MATCHMAKING) this.queueUpdate();
    else if (this.state === State.TEAM_BUILDING) this.updateTeam();
    else if (this.state === State.CHAMP_SELECT) this.disbandTeam();
  };

  private disbandTeam = (): void => this.players
    .forEach((player) => player.send(ResponseHandler.responseDisband()));

  public getSize = (): number => this.players.length;

  public queueFull = (): void => {
    this.state = State.CHAMP_SELECT;
    for (let i = 0; i < this.players.length; i += 1) {
      if (this.players.length < 6) {
        this.players[i]!.setTeam(i % 2 === 0 ? 'BLUE' : 'PURPLE');
      } else {
        this.players[i]!.setTeam(i <= 2 ? 'PURPLE' : 'BLUE');
      }
      this.players[i]!.send(ResponseHandler.responseMatchFound());
    }
    this.updateTeam();
  };

  public getPlayerObjects = (team?: string) => (team
    ? this.players.filter((player) => player.getTeam()?.toLowerCase() === team.toLowerCase())
    : this.players)
    .map((player) => ({
      name: player.getName(),
      player: player.getPid(),
      teg_id: player.getUsername(),
      avatar: player.getAvatar(),
      is_ready: player.isReady(),
    }));

  private updateTeam = (): void => this.players.forEach((player) => player.send(ResponseHandler
    .responseTeamUpdate(this.getPlayerObjects(player.getTeam()!), player.getTeam()!)));

  private queueUpdate = (): void => this.players.forEach((player) => player
    .send(ResponseHandler.responseQueueUpdate(this.getSize())));

  public updatePlayer = (p: Player): void => {
    this.players[this.findPlayerIndex(p)] = p;
    this.updateTeam();
    if (this.getReadyPlayers() === this.players.length) this.gameReady();
  };

  private gameReady = (): void => {
    this.state = State.IN_GAME;
    this.players.filter((player) => player
      .send(ResponseHandler.responseGameReady(player, player.getTeam()!, this.type)));
  };

  private getReadyPlayers = (): number => this.players.filter((player) => player.isReady()).length;

  private updatePremade = (): void => this.players.forEach((player) => player
    .send(ResponseHandler.responseTeamJoin(this)));
}
