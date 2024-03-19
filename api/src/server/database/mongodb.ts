import crypto from 'node:crypto';
import { Collection, MongoClient, ServerApiVersion } from 'mongodb';

import * as config from '../config';
import logger from '../log';
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
  private client: MongoClient;

  private usersCollection: Collection<User>;

  constructor() {
    this.client = new MongoClient(config.databaseUrl, {
      serverApi: ServerApiVersion.v1,
      minPoolSize: 6,
      maxPoolSize: 8,
      ssl: config.databaseSSL,
    });
    this.client.connect().catch((err) => {
      logger.error(`FATAL: MongoDB connect failed: ${err.message}`);
      process.exit(1);
    });
    this.usersCollection = this.client.db('openatbp').collection('users');
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
    const data = await this.usersCollection.findOneAndUpdate(
      { 'user.authid': username },
      { $set: playerFile },
      { upsert: true, returnDocument: 'after', includeResultMetadata: false },
    );
    if (data === null) throw new Error('Error creating user!');
    return data;
  };

  public findUserByUsername: FindUserByUsername = async (username: string) => {
    const data: User | null = await this.usersCollection.findOne<User>({ 'user.authid': username });
    if (data === null) throw new Error('User does not exist!');
    return data;
  };

  public findUserById: FindUserById = async (id: string) => {
    const data: User | null = await this.usersCollection.findOne<User>({ 'user.TEGid': id });
    if (data === null) throw new Error('User does not exist!');
    return data;
  };

  public findUserByAuthToken: FindUserByAuthToken = async (authToken: string) => {
    const data: User | null = await this.usersCollection.findOne<User>({ authToken });
    if (data === null) throw new Error('User does not exist!');
    return data;
  };

  public purchaseItem: PurchaseItem = async (
    authToken: string,
    itemToPurchase: string,
    itemCost: number,
  ) => {
    const data = await this.usersCollection.findOneAndUpdate(
      { authToken },
      { $push: { inventory: itemToPurchase as any }, $inc: { 'player.coins': itemCost * -1 } },
      { returnDocument: 'after', includeResultMetadata: false },
    );
    if (data === null) throw new Error('User does not exist!');
    return data;
  };

  public addFriend: AddFriend = async (authToken: string, friendUserId: string) => {
    const data = await this.usersCollection.findOneAndUpdate(
      { authToken },
      { $push: { friends: friendUserId as any } },
      { returnDocument: 'after', includeResultMetadata: false },
    );
    if (data === null) throw new Error('User does not exist!');
    return data;
  };
}
