ARG ENV=production
ARG SFS2X_WORK_DIR=/SmartFoxServer_2X
ARG GRADLE_WORK_DIR=/home/gradle/project
ARG SFS2X_VERSION=2_19_0
# ARG SFS2X_PATCH_VERSION=2.19.3
ARG DOWNLOAD_DEFINITIONS_URL=https://archive.org/download/atbp-20150519/atbp-20150519-definitions.zip

FROM alpine:3.19.1 AS downloader
ARG DOWNLOAD_DEFINITIONS_URL
ARG SFS2X_VERSION
WORKDIR /
COPY ./makefile /makefile
RUN apk add --no-cache make wget tar unzip && \
    make download_definitions && \
    wget -O- http://www.smartfoxserver.com/downloads/sfs2x/SFS2X_unix_${SFS2X_VERSION}.tar.gz | tar zxf -

# FROM debian:12.5-slim AS sfs2x
# ARG SFS2X_VERSION
# ARG SFS2X_PATCH_VERSION
# WORKDIR /
# RUN apt update && apt install -y wget tar
# RUN wget -O- sfs2x.tar.gz http://www.smartfoxserver.com/downloads/sfs2x/SFS2X_unix_${SFS2X_VERSION}.tar.gz
# RUN tar xf sfs2x.tar.gz
# RUN wget -O sfs2x_patch.zip http://www.smartfoxserver.com/downloads/sfs2x/patches/SFS2X-Patch-${SFS2X_PATCH_VERSION}.zip
# RUN unzip -qod $SFS2X_WORK_DIR sfs2x_patch.zip
# RUN cd $SFS2X_WORK_DIR/SFS2X-Patch-${SFS2X_PATCH_VERSION} && ./install-linux.sh && cd ../..

FROM gradle:8.6.0-jdk11-alpine AS gradle
ARG SFS2X_WORK_DIR
ARG GRADLE_WORK_DIR
WORKDIR $GRADLE_WORK_DIR
COPY ./src $GRADLE_WORK_DIR/src
COPY ./lib $GRADLE_WORK_DIR/lib
COPY ./settings.docker.gradle $GRADLE_WORK_DIR/settings.gradle
COPY ./build.docker.gradle $GRADLE_WORK_DIR/build.gradle
COPY ./makefile $GRADLE_WORK_DIR
COPY --from=downloader $SFS2X_WORK_DIR/SFS2X/lib/sfs2x-core.jar $GRADLE_WORK_DIR/lib/sfs2x-core.jar
COPY --from=downloader $SFS2X_WORK_DIR/SFS2X/lib/sfs2x.jar $GRADLE_WORK_DIR/lib/sfs2x.jar
# --stacktrace --debug
RUN apk add --no-cache make && make build

FROM alpine:3.19.1 AS final
# FROM timlien/docker-smartfox:2.18.4
# FROM bijohns/smartfoxserver:2.17.3
ARG ENV
# ENV ENV=$ENV
ARG SFS2X_WORK_DIR
ARG GRADLE_WORK_DIR
WORKDIR $SFS2X_WORK_DIR
RUN apk add --no-cache openjdk11-jre
COPY ./deploy/extensions $SFS2X_WORK_DIR/SFS2X/extensions
COPY ./deploy/zones $SFS2X_WORK_DIR/SFS2X/zones
COPY --from=downloader /definitions $SFS2X_WORK_DIR/SFS2X/extensions/Champions/definitions
COPY --from=downloader $SFS2X_WORK_DIR/SFS2X $SFS2X_WORK_DIR/SFS2X
COPY --from=downloader $SFS2X_WORK_DIR/.install4j $SFS2X_WORK_DIR/.install4j
COPY --from=gradle $GRADLE_WORK_DIR/build/libs/ATBPExtension.jar $SFS2X_WORK_DIR/SFS2X/extensions/Champions/ATBPExtension.jar
RUN echo "ENV=$ENV" >> $SFS2X_WORK_DIR/SFS2X/extensions/Champions/config.properties
WORKDIR $SFS2X_WORK_DIR/SFS2X
# EXPOSE 9933 9933/udp 8080 8443 8787 5000
ENTRYPOINT ["./sfs2x-service", "run"]
