import crypto from 'node:crypto';

import shopData from '../data/shop.json';
import type {
  User,
  CreateNewUser,
  FindUserByUsername,
  FindUserById,
  FindUserByAuthToken,
  PurchaseItem,
  AddFriend,
  DatabaseInterface,
} from './database';

export default class implements DatabaseInterface {
  private userCollection: User[];

  constructor() {
    this.userCollection = [];
  }

  // Creates new user in web server and database
  public createNewUser: CreateNewUser = async (
    username: string,
    displayName: string,
    authpass: string,
  ) => {
    const token = Math.random().toString(36).slice(2, 10);
    const inventoryArray = shopData.map((item) => item.id);
    const playerFile: User = {
      user: {
        TEGid: `${crypto.randomUUID()}`,
        dname: `${displayName}`,
        authid: `${username}`,
        authpass: `${authpass}`,
      },
      player: {
        playsPVP: 1,
        tier: 0,
        elo: 0,
        disconnects: 0,
        playsBots: 0,
        rank: 1,
        rankProgress: 0,
        winsPVP: 0,
        winsBots: 0,
        points: 0,
        coins: 500,
        kills: 0,
        deaths: 0,
        assists: 0,
        towers: 0,
        minions: 0,
        jungleMobs: 0,
        altars: 0,
        largestSpree: 0,
        largestMulti: 0,
        scoreHighest: 0,
        scoreTotal: 0,
      },
      inventory: inventoryArray,
      authToken: token,
      friends: [],
    };
    // Creates new user in the db
    const user = this.userCollection.find((u) => u.user.authid === username);
    if (user) Object.assign(user, playerFile);
    else this.userCollection.push(playerFile);
    return playerFile;
  };

  public findUserByUsername: FindUserByUsername = async (username: string) => {
    const data: User | undefined = this.userCollection
      .find((user) => user.user.authid === username);
    if (!data) throw new Error('User does not exist!');
    return data;
  };

  public findUserById: FindUserById = async (id: string) => {
    const data: User | undefined = this.userCollection
      .find((user) => user.user.TEGid === id);
    if (!data) throw new Error('User does not exist!');
    return data;
  };

  public findUserByAuthToken: FindUserByAuthToken = async (authToken: string) => {
    const data: User | undefined = this.userCollection
      .find((user) => user.authToken === authToken);
    if (!data) throw new Error('User does not exist!');
    return data;
  };

  public purchaseItem: PurchaseItem = async (
    authToken: string,
    itemToPurchase: string,
    itemCost: number,
  ) => {
    const data: User | undefined = this.userCollection
      .find((user) => user.authToken === authToken);
    if (!data) throw new Error('User does not exist!');
    if (data.player.coins < itemCost) throw new Error('Not enough coins!');
    data.inventory.push(itemToPurchase);
    data.player.coins -= itemCost;
    return data;
  };

  public addFriend: AddFriend = async (authToken: string, friendUserId: string) => {
    const data: User | undefined = this.userCollection
      .find((user) => user.authToken === authToken);
    if (!data) throw new Error('User does not exist!');
    data.friends.push(friendUserId);
    return data;
  };
}
