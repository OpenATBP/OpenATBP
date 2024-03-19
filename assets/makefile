#!/usr/bin/make -f

ENV?=development
ENV_FILE?=./env/.env.build.${ENV}
-include ${ENV_FILE}

SERVICE?=openatbp-assets
VERSION?=0.0.1
ASSETS_PORT?=8081

PORT:=${ASSETS_PORT}
DOWNLOAD_ASSETS_URL?=https://archive.org/download/atbp-20150519/atbp-20150519-full.zip
SRC_DIR?=src
BIN_DIR?=bin
BIN_FILE?=main
# The following flags disable debugging info, reducing the executable's size: -w -s
LDFLAGS?=

export

EXCLUDED_CHARACTERS:=< . ? @ , % ^ * + >
FILTERED_VARIABLES:=$(sort $(filter-out $(foreach C,${EXCLUDED_CHARACTERS},$(foreach S,${.VARIABLES},$(if $(findstring $C,$S),$S))),${.VARIABLES}))

.PHONY: all download_assets install_deps build run docker_build docker_run docker_push clean

all: build

# TODO: change to the command below after assets are uploaded in a tarball
# This will allow the use of PowerShell's wget and tar
# wget -O- ${DOWNLOAD_ASSETS_URL} | tar zxf - -C static
download_assets:
	wget -O assets.zip ${DOWNLOAD_ASSETS_URL}
	unzip -qod static assets.zip

install_deps: go.mod
	go get

${BIN_DIR}:
	mkdir -p $@

build: ${BIN_DIR}/${BIN_FILE}

${BIN_DIR}/${BIN_FILE}: ${BIN_DIR} ${SRC_DIR}/main.go
	go build -a -ldflags="${LDFLAGS}" -o ${BIN_DIR}/${BIN_FILE} ./${SRC_DIR}

run: ${BIN_DIR}/${BIN_FILE}
	./${BIN_DIR}/${BIN_FILE}

docker_build: Dockerfile
	docker build -t ${SERVICE}:${VERSION} -t ${SERVICE}:latest -f ./Dockerfile $(addprefix --build-arg ,${FILTERED_VARIABLES}) .

docker_run:
	docker run --rm -p ${PORT}:${PORT}/tcp --name ${SERVICE} --env-file ${ENV_FILE} ${SERVICE}:${VERSION}

docker_push:
	docker push ${SERVICE}:${VERSION}
	docker push ${SERVICE}:latest

clean:
	rm -rf ${BIN_DIR} go.sum
