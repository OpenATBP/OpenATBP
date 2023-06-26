const fs = require('fs');
const net = require('net');
const config = require('./config.js');

const POLICY_REQUEST = '<policy-file-request/>\0';

const policyContent = fs.readFileSync(config.sockpol.file, 'utf8');

const sockpol = net.createServer(function (socket) {
  socket.setEncoding('utf8');
  socket.setTimeout(3000);

  socket.on('data', function (data) {
    if (data === POLICY_REQUEST) {
      socket.write(policyContent);
      socket.end();
    }
  });

  socket.on('error', function (error) {
    console.error('Socket error:', error);
  });
});

sockpol.listen(config.sockpol.port, function () {
  console.log(`Socket policy running on port ${config.sockpol.port}!`);
});

module.exports = sockpol;