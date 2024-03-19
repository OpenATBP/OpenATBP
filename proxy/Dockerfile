ARG ENV=production

FROM caddy:2.7.6-builder-alpine AS builder
# https://github.com/mholt/caddy-l4
RUN xcaddy build --with github.com/mholt/caddy-l4

FROM caddy:2.7.6-alpine AS final
COPY --from=builder /usr/bin/caddy /usr/bin/caddy
COPY ./caddy.json /etc/caddy/caddy.json
