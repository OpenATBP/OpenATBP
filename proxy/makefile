#!/usr/bin/make -f

ENV?=development
ENV_FILE?=./env/.env.build.${ENV}
-include ${ENV_FILE}

SERVICE?=openatbp-proxy
VERSION?=0.0.1

export

EXCLUDED_CHARACTERS:=< . ? @ , % ^ * + >
FILTERED_VARIABLES:=$(sort $(filter-out $(foreach C,${EXCLUDED_CHARACTERS},$(foreach S,${.VARIABLES},$(if $(findstring $C,$S),$S))),${.VARIABLES}))

docker_build: Dockerfile
	docker build -t ${SERVICE}:${VERSION} -t ${SERVICE}:latest -f ./Dockerfile $(addprefix --build-arg ,${FILTERED_VARIABLES}) .

docker_run:
	docker run --rm -p 8080:8080/tcp --name ${SERVICE} --env-file ${ENV_FILE} ${SERVICE}:${VERSION}

docker_push:
	docker push ${SERVICE}:${VERSION}
	docker push ${SERVICE}:latest
