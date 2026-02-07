FROM node:25-alpine

COPY ./ATBPServer /app
WORKDIR /app

RUN npm install
RUN npm run postinstall

CMD ["npm", "run", "start"]