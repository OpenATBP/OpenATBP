#!/usr/bin/make -f

ENV?=development
ENV_FILE?=./env/.env.build.${ENV}
-include ${ENV_FILE}

SERVICE?=openatbp-sockpol
VERSION?=0.0.1
SOCKPOL_PORT?=843

PORT:=${SOCKPOL_PORT}
SRC_DIR?=src
BIN_DIR?=bin
BIN_FILE?=main
# The following flags disable debugging info, reducing the executable's size: -w -s
LDFLAGS?=

export

EXCLUDED_CHARACTERS:=< . ? @ , % ^ * + >
FILTERED_VARIABLES:=$(sort $(filter-out $(foreach C,${EXCLUDED_CHARACTERS},$(foreach S,${.VARIABLES},$(if $(findstring $C,$S),$S))),${.VARIABLES}))

.PHONY: all install_deps build run docker_build docker_run docker_push clean

all: build

install_deps: go.mod
	go get

${BIN_DIR}:
	mkdir -p $@

build: ${BIN_DIR}/${BIN_FILE}

${BIN_DIR}/${BIN_FILE}: ${BIN_DIR} ${SRC_DIR}/main.go
	go build -a -ldflags="${LDFLAGS}" -o ${BIN_DIR}/${BIN_FILE} ./${SRC_DIR}

run: ${BIN_DIR}/${BIN_FILE}
	./${BIN_DIR}/${BIN_FILE}

test:
	go test -v -cover ./${SRC_DIR}

docker_build: Dockerfile
	docker build -t ${SERVICE}:${VERSION} -t ${SERVICE}:latest -f ./Dockerfile $(addprefix --build-arg ,${FILTERED_VARIABLES}) .

docker_run:
	docker run --rm -p ${PORT}:${PORT}/tcp --name ${SERVICE} --env-file ${ENV_FILE} ${SERVICE}:${VERSION}

docker_push:
	docker push ${SERVICE}:${VERSION}
	docker push ${SERVICE}:latest

clean:
	rm -rf ${BIN_DIR} go.sum
