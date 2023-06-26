var config = {};
config.httpserver = {};
config.sockpol = {};

config.httpserver.port = 8000;

config.sockpol.enable = true;
config.sockpol.port = 843;
config.sockpol.file = "static/crossdomain.xml";

module.exports = config;
