#!/usr/bin/make -f

# add services and targets to the following lines
SERVICES:=api assets game lobby sockpol proxy
TARGETS:=install_deps build run test docker_build docker_run docker_push clean

ENV?=development
ENV_FILE?=./env/.env.build.${ENV}
-include ${ENV_FILE}

export

EMPTY:=
SPACE:=${EMPTY} ${EMPTY}

define get_service
$(word 1,$(subst _,${SPACE},$1))
endef

define get_target
$(subst ${SPACE},_,$(wordlist 2,$(words $(subst _,${SPACE},$1)),$(subst _,${SPACE},$1)))
endef

SERVICE_TARGETS:=$(foreach T,${TARGETS},$(addsuffix _$T,${SERVICES}))
ALL_TARGETS:=$(addprefix all_,${TARGETS})

.PHONY: run ${SERVICE_TARGETS} ${ALL_TARGETS}

run: docker-compose.yml
	docker compose -f docker-compose.yml --env-file ${ENV_FILE} up

${ALL_TARGETS}: all_%: $(addsuffix _%,${SERVICES})

${SERVICE_TARGETS}:
	@make -C $(call get_service,$@) -f makefile --warn-undefined-variables $(call get_target,$@)
