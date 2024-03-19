# Development

## Prerequisites

* Git ([Windows](https://gitforwindows.org/) or [Linux](https://git-scm.com/downloads))
* [Docker](https://docs.docker.com/engine/install/)
* Make ([Windows](https://gnuwin32.sourceforge.net/packages/make.htm) or [Linux](https://www.digitalocean.com/community/tutorials/how-to-use-makefiles-to-automate-repetitive-tasks-on-an-ubuntu-vps#installing-make))
* [MongoDB Server](https://www.mongodb.com/docs/manual/installation/)

### API

* [NodeJS and NPM](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)

### Assets

* [Go](https://go.dev/doc/install)

### Game

* [Java Development Kit 11](https://www.oracle.com/pt/java/technologies/javase/jdk11-archive-downloads.html)
* [SFS2X Community Edition](https://www.smartfoxserver.com/download/sfs2x#p=installer)
* [Gradle](https://gradle.org/install/)

### Lobby

* [NodeJS and NPM](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm)

### Sockpol

* [Go](https://go.dev/doc/install)

### Proxy

* [Caddy Server](https://caddyserver.com/docs/install)

## Make targets

Each service includes the following Make targets:
* `install_deps`: install packages and dependencies
* `build`: compile/transpile the subproject
* `run`: run the service locally
* `test`: run tests locally
* `docker_build`: build the subproject in a Docker environment
* `docker_run`: run the subproject in a Docker environment
* `docker_push`: upload the Docker image to a remote repository
* `clean`: clean and reset the subproject's directory

Additionally, you can run targets for any subproject from the projects root directory. For example, to run `make build` in the `game` subproject, simply run `make game_build` in the root directory.

## Setting up

1. Clone the repository: `git clone https://github.com/OpenATBP/OpenATBP.git`
2. Open a new terminal inside of the `api` directory
3. In this new terminal window, run the following command to install dependencies and download required asset files - this may take a while! `npm install`
4. Copy the example config in the httpserver directory: `cp config.js.example config.js` - once copied, edit it to include the connection string URI for your MongoDB server
5. Run httpserver using the following command: `npm run start` - if done correctly you should see `App running on port 8000!`
6. Start SmartFoxServer2X once so it can generate the correct files and folders, then close it
7. Open another terminal, this time in the root of the repository
8. Run the following command to compile and run the lobby: `.\gradlew ATBPLobby:run` - if successful you should see `DungeonServer running on port 6778`
9. Run the following commands to copy necessary files, then compile the game extension: `.\gradlew ATBPExtension:copySFS2XLibs`, `.\gradlew ATBPExtension:jar`
10. Provided there weren't any errors, deploy the SmartFox extension: `.\gradlew ATBPExtension:deploy` - this will also copy the data, definition and zone file(s) if needed
11. Copy the example config in the SFS2X extension directory (SFS2X/extensions/Champions, should be right next to the jar file): `cp config.properties.example config.properties` - once copied, edit it to include the same URI string you did in step 4.
12. Start SmartFoxServer2X, you should see a log line indicating the extension is working: `ATBP Extension loaded`
13. Finally, connect to http://127.0.0.1:8000 with an NPAPI-compatible browser such as Pale Moon to test the game!

Note that you can also run any Gradle task (`gradlew` commands) graphically through an IDE such as IntelliJ IDEA or Eclipse. 

These instructions are subject to change, if you run into any problems or have questions feel free to open an issue here on Github or reach out to us on [our Discord](https://discord.gg/AwmCCuAdT4).
