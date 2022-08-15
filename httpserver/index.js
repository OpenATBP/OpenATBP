const http = require('http');
const fs = require('fs').promises;

const host = 'localhost';
const port = 8001;

const requestListener = function(req,res) {
  //console.log(Object.keys(req));
  //console.log(res);
  res.writeHead(200, { //This is what requests are allowed to go through
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'OPTIONS, POST, GET',
    'Access-Control-Max-Age': 2592000
  });

  if(req.method == "POST"){
    var body = "";
    req.on("data", (data) => {
      body += data;
    });

    req.on('end', () => { //When finished reading the data, it will write to the file listed by the POST request. This is to my PC but will eventually go to the server.
      console.log(JSON.parse(body));
      fs.writeFile(`/Users/0lies/Desktop/Blank ATBP/ATBP-web/htdocs${req.url}`,JSON.stringify(JSON.parse(body))).then(() => {
        console.log("Success!");
      }).catch((e) => {
        console.log(e);
      });
    });
  }
  res.end(JSON.stringify({"test": "Working!"})); //Doesn't seem to matter right now. Just resolves the request.
}

const server = http.createServer(requestListener)

server.listen(port,host, () => {
  console.log("Server is running!");
});
