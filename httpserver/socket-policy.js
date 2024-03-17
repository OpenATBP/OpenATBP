const net = require('net');

module.exports = class SocketPolicyServer {
  constructor(port, content) {
    this.port = port;
    this.content = content;
    this.server = null;
  }
  start(callback) {
    const policyRequest = '<policy-file-request/>\0';
    this.server = net.createServer((socket) => {
      socket.setEncoding('utf8');
      socket.setTimeout(3000);

      socket.on('data', (data) => {
        if (data === policyRequest)
          socket.write(this.content); 
        socket.end();
      });

      socket.on('error', (error) => {
        console.error('Socket error:', error);
      });

      socket.on('timeout', () => {
        socket.end();
      });
    });

    this.server.listen(this.port, () => {
      callback();
    });
  }
  stop(callback) {
    if (this.server)
      this.server.close(callback());
  }
}
