import MongodbDatabase from './mongodb';
import MemoryDatabase from './memory';

export const getDatabase = (databaseType: string): DatabaseInterface => {
  switch (databaseType.toLowerCase()) {
    case 'mongodb':
      return new MongodbDatabase();
    case 'memory':
    default:
      return new MemoryDatabase();
  }
};

export type ShopItem = {
  type: string;
  id: string;
  default: boolean;
  cost: number;
};

export type User = {
  user: {
    TEGid: string,
    dname: string,
    authid: string,
    authpass: string,
  },
  player: {
    playsPVP: number,
    tier: number,
    elo: number,
    disconnects: number,
    playsBots: number,
    rank: number,
    rankProgress: number,
    winsPVP: number,
    winsBots: number,
    points: number,
    coins: number,
    kills: number,
    deaths: number,
    assists: number,
    towers: number,
    minions: number,
    jungleMobs: number,
    altars: number,
    largestSpree: number,
    largestMulti: number,
    scoreHighest: number,
    scoreTotal: number,
  },
  inventory: string[],
  authToken: string,
  friends: string[],
};

export interface DatabaseInterface {
  createNewUser: CreateNewUser,
  findUserByUsername: FindUserByUsername,
  findUserById: FindUserById,
  findUserByAuthToken: FindUserByAuthToken,
  purchaseItem: PurchaseItem,
  addFriend: AddFriend,
}

export type CreateNewUser = (
  username: string,
  displayName: string,
  authpass: string,
) => Promise<User>;

export type FindUserByUsername = (username: string) => Promise<User>;

export type FindUserById = (id: string) => Promise<User>;

export type FindUserByAuthToken = (authToken: string) => Promise<User>;

export type PurchaseItem = (
  authToken: string,
  itemToPurchase: string,
  itemCost: number,
) => Promise<User>;

export type AddFriend = (authToken: string, friendUserId: string) => Promise<User>;
