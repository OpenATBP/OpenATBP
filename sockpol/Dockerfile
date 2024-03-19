ARG ENV=production

FROM golang:1.22.1-alpine3.19 AS builder
WORKDIR /
COPY ./src /src
COPY ./go.mod /go.mod
COPY ./makefile /makefile
RUN apk add --no-cache make && make build

FROM alpine:3.19.1 AS final
WORKDIR /
COPY --from=builder /bin/main /main
ENTRYPOINT ["./main"]
