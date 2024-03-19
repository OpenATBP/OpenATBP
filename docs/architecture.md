# Architecture

Originally, Battle Party required several server-side components in order to to function:
* Web server to serve static content/streaming assets
* Web server that provides service/API endpoints (internal name "Facade")
* Socket policy server to satisfy the Unity Web Player [security sandbox](https://docs.unity3d.com/351/Documentation/Manual/SecuritySandbox.html)
* Lobby server for players to form parties and search for matches (internal name "DungeonServer")
* SmartFoxServer2X with custom extension acting as the actual game server

These components have been reimplemented in this project, resulting in the following server components:
* `assets`: web server to serve static content/streaming assets
* `api`: web server that provides service/API endpoints
* `sockpol`: socket policy server to satisfy the Unity Web Player [security sandbox](https://docs.unity3d.com/351/Documentation/Manual/SecuritySandbox.html)
* `lobby`: lobby server for players to form parties and search for matches
* `game`: SmartFoxServer2X with custom extension acting as the actual game server
* `proxy`: Caddy server acting as a reverse proxy for all other services, allowing all services to share the same network entrypoint

Each service is available in its own directory. To simplify, all components share the same Make targets and folder structure.
