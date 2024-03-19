# Deployment

## Minimum dependencies

These instructions only require Docker:
1. Copy the example environment files and change any necessary configurations. If unsure, use the file `env/.env.build.development`
2. Ensure you have [Docker](https://docs.docker.com/engine/install/) installed
3. Run the following command to run all services locally: `docker compose -f docker-compose.yml --env-file env/.env.build.development up`

Alternatively, these instructions require Docker and Make:
1. Copy the example environment files and change any necessary configurations. By default, the system uses the file `env/.env.build.development`
2. Ensure you have [Docker](https://docs.docker.com/engine/install/) and Make ([Windows](https://gnuwin32.sourceforge.net/packages/make.htm) or [Linux](https://www.digitalocean.com/community/tutorials/how-to-use-makefiles-to-automate-repetitive-tasks-on-an-ubuntu-vps#installing-make)) installed
3. Run the following command to run all services locally: `make run`

To run the game, you must visit the main page using a NPAPI-compatible browser, such as [Pale Moon](https://www.palemoon.org/download.shtml) or [Waterfox Classic](https://classic.waterfox.net/). newer browsers do not allow running plugins.

## Environment variables

The system is configured via environment variables, which are propagated through Make, Docker and each service.

For a complete list of all available environment variables, please refer to the example configuration files in the `env` directory.
