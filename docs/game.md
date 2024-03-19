# Game

[Adventure Time Battle Party](https://adventure-time-battle-party.fandom.com/wiki/Adventure_Time_Battle_Party_Wiki) was developed using Unity 4.5 in 2014. It uses Unity Web Player, a browser plugin that's no longer available in all major browsers.

## Assets

The game's assets were gathered by the community for this project.

The asset bundles can be extracted using [AssetRipper](https://github.com/AssetRipper/AssetRipper) and code can be analyzed using [ILSpy](https://github.com/icsharpcode/ILSpy).

## Interface

### JavaScript

Interoperability with JavaScript in the embedding web page is doen using the function `Application.ExternalCall`.

### Lobby Server

File `DungeonClient.cs` handles requests to the Lobby server (internal name "DungeonServer").

### API Server

Files `FacadeManager.cs` and `PresenceManager.cs` handle HTTP requests to the API server (internal name "Facade"), on routes `/service/*`.

### Socket Policy Server

To satisfy Unity Web Player's [security sandbox](https://docs.unity3d.com/351/Documentation/Manual/SecuritySandbox.html), a request to the Socket Policy Server is made using the function `Security.PrefetchSocketPolicy`.

### Game Server

Once the Lobby Server finds a match and sends the game server connection details to the game client, a connection is made to the SmartFoxServer 2X instance running the game server.

## Config

In line `GameSystem.cs:965`, the game loads and parses the configuration XML file, looking for the tags defined in `ConfigTags.cs`.

Due to the way the configuration is loaded, the Socket Policy Server must be accessible in the same IP/domain as the Lobby Server, but in a different port.
