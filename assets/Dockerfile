ARG ENV=production
ARG DOWNLOAD_ASSETS_URL=https://archive.org/download/atbp-20150519/atbp-20150519-full.zip

FROM alpine:3.19.1 AS downloader
ARG DOWNLOAD_ASSETS_URL
WORKDIR /
COPY ./makefile /makefile
RUN apk add --no-cache make wget unzip && make download_assets

FROM golang:1.22.1-alpine3.19 AS builder
WORKDIR /
COPY ./src /src
COPY ./go.mod /go.mod
COPY ./makefile /makefile
RUN apk add --no-cache make && make build

FROM alpine:3.19.1 AS final
WORKDIR /
COPY ./static /static
COPY --from=downloader /static /static
COPY --from=builder /bin/main /main
ENTRYPOINT ["./main"]
