# =========================
# Stage 1: Build
# =========================
FROM eclipse-temurin:11-alpine-3.23 AS builder

# System deps
RUN apk add --no-cache wget unzip bash

ENV SFS_VERSION=2_19_0
ENV SFS_PATCH=2.20.0
ENV SFS2X_HOME=/opt/SmartFoxServer_2X/SFS2X

# Install SmartFoxServer
RUN wget -q -O - https://www.smartfoxserver.com/downloads/sfs2x/SFS2X_unix_${SFS_VERSION}.tar.gz \
  | tar -C /opt -xzvf - \
  && rm -rf /opt/SmartFoxServer_2X/jre \
  && ln -s /opt/java/openjdk /opt/SmartFoxServer_2X/jre

WORKDIR /opt/SmartFoxServer_2X

# Apply patch
RUN wget -q https://www.smartfoxserver.com/downloads/sfs2x/patches/SFS2X-Patch-${SFS_PATCH}.zip \
  && unzip SFS2X-Patch-${SFS_PATCH}.zip \
  && cd SFS2X-Patch-${SFS_PATCH} \
  && ./install-linux.sh \
  && cd .. \
  && rm -rf SFS2X-Patch-${SFS_PATCH}*

# Build extensions
WORKDIR /build
COPY . .

RUN ./gradlew ATBPExtension:copySFS2XLibs \
  && ./gradlew ATBPExtension:jar \
  && ./gradlew ATBPExtension:deploy \
  && ./gradlew ATBPExtension:copyDataFiles


# =========================
# Stage 2: Runtime
# =========================
FROM eclipse-temurin:11-alpine-3.23

ENV SFS2X_HOME=/opt/SmartFoxServer_2X/SFS2X

COPY --from=builder /opt/SmartFoxServer_2X /opt/SmartFoxServer_2X

WORKDIR /opt/SmartFoxServer_2X/SFS2X

CMD ["./sfs2x-service", "run"]
