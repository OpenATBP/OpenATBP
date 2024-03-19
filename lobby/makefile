#!/usr/bin/make -f

ENV?=development
ENV_FILE?=./env/.env.build.${ENV}
-include ${ENV_FILE}

SERVICE?=openatbp-lobby
VERSION?=0.0.1
LOBBY_PORT?=6778

PORT:=${LOBBY_PORT}
SRC_DIR?=src
BIN_DIR?=dist
BIN_FILE?=server/main.js

export

EXCLUDED_CHARACTERS:=< . ? @ , % ^ * + >
FILTERED_VARIABLES:=$(sort $(filter-out $(foreach C,${EXCLUDED_CHARACTERS},$(foreach S,${.VARIABLES},$(if $(findstring $C,$S),$S))),${.VARIABLES}))

.PHONY: all install_deps build run docker_build docker_run docker_push clean

all: build

install_deps: package.json
	npm install --production=false

build: ${BIN_DIR}/${BIN_FILE}

${BIN_DIR}/${BIN_FILE}: package.json ${SRC_DIR}/$(BIN_FILE:js=ts)
	npm run build

run: package.json ${BIN_DIR}/${BIN_FILE}
	npm run start

docker_build: Dockerfile
	docker build -t ${SERVICE}:${VERSION} -t ${SERVICE}:latest -f ./Dockerfile $(addprefix --build-arg ,${FILTERED_VARIABLES}) .

docker_run:
	docker run --rm -p ${PORT}:${PORT}/tcp --name ${SERVICE} --env-file ${ENV_FILE} ${SERVICE}:${VERSION}

docker_push:
	docker push ${SERVICE}:${VERSION}
	docker push ${SERVICE}:latest

clean:
	rm -rf ${BIN_DIR} node_modules
