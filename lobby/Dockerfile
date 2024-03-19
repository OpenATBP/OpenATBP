ARG ENV=production
ARG WORK_DIR=/home/node/app

FROM node:20.11.1-alpine3.19 AS builder
ARG ENV
ENV NODE_ENV=$ENV
ARG WORK_DIR
WORKDIR $WORK_DIR
COPY ./package.json $WORK_DIR/
COPY ./babel.config.js $WORK_DIR/
COPY ./tsconfig.json $WORK_DIR/
COPY ./src $WORK_DIR/src
COPY ./makefile $WORK_DIR/makefile
RUN apk add --no-cache make && make install_deps && make build && npm prune --production

FROM node:20.11.1-alpine3.19 AS final
ARG ENV
ENV NODE_ENV=$ENV
ARG WORK_DIR
WORKDIR $WORK_DIR
COPY --from=builder $WORK_DIR/node_modules $WORK_DIR/node_modules
COPY --from=builder $WORK_DIR/dist/server $WORK_DIR/dist/server
COPY --from=builder $WORK_DIR/package*.json $WORK_DIR/
ENTRYPOINT [ "npm", "start" ]
CMD [ "--silent" ]
