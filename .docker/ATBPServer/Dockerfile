FROM node:25-alpine

COPY ./ATBPServer /app
WORKDIR /app

RUN npm install

CMD ["npm", "run", "start"]