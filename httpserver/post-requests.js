module.exports = {
  handleLogin: function(data){
    return new Promise(function(resolve, reject) {
      resolve(JSON.stringify({
      	"authToken": {
      		"text": "0001"
      	}
      }));
    });
  }
};
