# OpenATBP

[![Releases Badge](https://img.shields.io/github/v/release/OpenATBP/OpenATBP?include_prereleases)](https://github.com/OpenATBP/OpenATBP/releases)
[![Trello Badge](https://img.shields.io/badge/trello-progress_tracking-0052CC?logo=trello)](https://trello.com/b/DcrsFKB1/openatbp)
[![Discord Badge](https://img.shields.io/discord/929861280456671324?color=687DC5&logo=discord)](https://discord.gg/AwmCCuAdT4)
[![License Badge](https://img.shields.io/github/license/OpenATBP/OpenATBP)](https://github.com/OpenATBP/OpenATBP/blob/master/LICENSE.md)

An open-source lobby, service, and game server for Adventure Time Battle Party.

![Screenshot](docs/screenshot2.png)

## Status

Currently, a handful of characters have their full kits functional, and games can be played from start to finish. Collision, pathfinding, and a few other systems still need work. For the most up-to-date progress, check the [Trello board](https://trello.com/b/DcrsFKB1/openatbp). Contributions are always welcome!
If you have any questions or would like to join our community, feel free to reach out to us on [our Discord](https://discord.gg/AwmCCuAdT4).

## Architecture

OpenATBP requires several server-side components in order to to function:
* `assets`: web server to serve static content/streaming assets
* `api`: web server that provides service/API endpoints (internal name "Facade")
* `sockpol`: socket policy server to satisfy the Unity Web Player [security sandbox](https://docs.unity3d.com/351/Documentation/Manual/SecuritySandbox.html)
* `lobby`: lobby server for players to form parties and search for matches (internal name "DungeonServer")
* `game`: SmartFoxServer2X with custom extension acting as the actual game server

See [Architecture](docs/architecture.md) for more detailed information.

## Development

Each server component requires different dependencies installed.

See [Development](docs/development.md) for more detailed information.

## Deployment

The simplest way to run this project is using Docker. Simply run `make run` in a terminal.

*Ensure these are all installed before proceeding!*
* Make ([Windows](https://gnuwin32.sourceforge.net/packages/make.htm) or [Linux](https://www.digitalocean.com/community/tutorials/how-to-use-makefiles-to-automate-repetitive-tasks-on-an-ubuntu-vps#installing-make))
* Docker ([Windows](https://docs.docker.com/desktop/install/windows-install/) or [Linux](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-20-04#step-1-installing-docker))

See [Deployment](docs/deployment.md) for more detailed information.

## License

MIT unless specified otherwise. See [LICENSE](LICENSE.md).

![SFS2X Logo](docs/sfs2xlogo.png)
