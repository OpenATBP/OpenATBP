#!/usr/bin/make -f

ENV?=development
ENV_FILE?=./env/.env.build.${ENV}
-include ${ENV_FILE}

SERVICE?=openatbp-game
VERSION?=0.0.1
GAME_PORT?=9933

PORT:=${GAME_PORT}
DOWNLOAD_DEFINITIONS_URL?=https://archive.org/download/atbp-20150519/atbp-20150519-definitions.zip
SRC_DIR?=src
BIN_DIR?=build
BIN_FILE?=libs/ATBPExtension.jar

export

EXCLUDED_CHARACTERS:=< . ? @ , % ^ * + >
FILTERED_VARIABLES:=$(sort $(filter-out $(foreach C,${EXCLUDED_CHARACTERS},$(foreach S,${.VARIABLES},$(if $(findstring $C,$S),$S))),${.VARIABLES}))

.PHONY: all download_definitions build run docker_build docker_run docker_push clean

all: build

# TODO: change to the command below after assets are uploaded in a tarball
# This will allow the use of PowerShell's wget and tar
# wget -O- ${DOWNLOAD_DEFINITIONS_URL} | tar zxf - -C definitions
download_definitions:
	wget -O definitions.zip ${DOWNLOAD_DEFINITIONS_URL}
	unzip -qod definitions definitions.zip

build: ${BIN_DIR}/${BIN_FILE}

${BIN_DIR}/${BIN_FILE}: ${SRC_DIR}/main/java/xyz/openatbp/extension/ATBPExtension.java
	gradle --warning-mode all --scan :jar

run: ${BIN_DIR}/${BIN_FILE}
	java -cp $< xyz.openatbp.extension.ATBPExtension

docker_build: Dockerfile
	docker build -t ${SERVICE}:${VERSION} -t ${SERVICE}:latest -f ./Dockerfile $(addprefix --build-arg ,${FILTERED_VARIABLES}) .

docker_run:
	docker run --rm -p ${PORT}:${PORT}/tcp --name ${SERVICE} --env-file ${ENV_FILE} ${SERVICE}:${VERSION}

docker_push:
	docker push ${SERVICE}:${VERSION}
	docker push ${SERVICE}:latest

clean:
	rm -rf ${BIN_DIR}
