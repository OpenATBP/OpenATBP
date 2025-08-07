import crypto from 'crypto';
import fs from 'node:fs';
import got from 'got';
import config from './config.js';

var newUserFunction = function (
  username,
  displayName,
  authpass,
  forgot,
  collection
) {
  //Creates new user in web server and database
  return new Promise((fulfill, reject) => {
    var inventoryArray = [];
    fs.readFile('data/shop.json', (err, data) => {
      if (err) reject(err);
      else {
        var today = new Date();
        today.setDate(today.getDate() + 1);
        for (var item of JSON.parse(data)) {
          if (item.type == 'BACKPACK') inventoryArray.push(item.id);
        }
        if (displayName.charAt(displayName.length - 1) == ' ')
          displayName = displayName.substring(0, displayName.length - 1);
        var playerFile = {
          user: {
            TEGid: `${username.toLowerCase()}`,
            dname: `${displayName}`,
            authid: `${Math.floor(Math.random() * 1000000000)}`,
            authpass: `${authpass}`,
          },
          session: {
            token: `${crypto.randomUUID()}`,
            expires_at: today,
            renewable: false,
          },
          player: {
            playsPVP: 1.0,
            tier: 1.0,
            elo: 1.0,
            disconnects: 0.0,
            playsBots: 0.0,
            rank: 1.0,
            rankProgress: 0.0,
            winsPVP: 0.0,
            winsBots: 0.0,
            points: 0.0,
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
          friends: [],
          betaTester: false, //TODO: Remove when open beta starts
          earlyAccess: false,
          forgot: forgot,
          requests: [],
          address: 'newAccount',
          queue: {
            lastDodge: -1,
            queueBan: -1,
            dodgeCount: 0,
            timesOffended: 0,
          },
        };
        const opt = { upsert: true };
        const update = { $set: playerFile };
        const filter = { 'user.TEGid': username };
        collection
          .updateOne(filter, update, opt)
          .then(() => {
            //Creates new user in the db
            fulfill(playerFile);
          })
          .catch((err) => {
            reject(err);
          });
      }
    });
  });
};

var handleQueueData = function (player, playerDatabase) {
  if (player != undefined) {
    playerDatabase
      .updateOne(
        { 'user.TEGid': player.teg_id },
        { $set: { queue: player.queueData } },
        { upsert: true }
      )
      .catch(console.error);
  }
};

var handleRefreshToken = function(user,token, playerDatabase){
  return new Promise(async function(resolve, reject) {
    var options = {
      "headers": ["Content-Type: application/x-www-form-urlencoded"],
      "form": {
        "grant_type": "refresh_token",
        "refresh_token": token,
        "client_id": config.discord.client_id,
        "client_secret": config.discord.client_secret
      }
    };
    try{
      const data = await got.post('https://discord.com/api/oauth2/token',options).json();
      data.expires_at = Date.now() + data.expires_in;
      if(user == null){
        resolve(data);
      }else{
        playerDatabase.updateOne({"user.authid":user.authid},{$set: {"session":data}}).then(() => {
          console.log("Updated data!");
          resolve(data);
        }).catch(console.error);
      }
    }catch(e){
      reject(e);
    }
  });
}

export default {
  createNewUser: newUserFunction,
  handleQueueData: handleQueueData,
  handleRefreshToken: handleRefreshToken
};
