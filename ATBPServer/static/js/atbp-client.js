var unity;
window.onload = function () {
  unity = document.getElementById('unity_player');
};

function OnResize() {
  unity.style.width = window.innerWidth + 'px';
  unity.style.height = window.innerHeight + 'px';
}

function Fireteam_CheckMSIBLoggedIn(name, callback) {
  returnOK = document.cookie
    .split('; ')
    .find((row) => row.startsWith('logged'))
    ?.split('=')[1];
  console.log(returnOK);
  if (returnOK == undefined) returnOK = 'false';
  unity.SendMessage(name, callback, returnOK);
}

function Fireteam_GetCookies(name, callback) {
  unity.SendMessage(name, callback, document.cookie);
}

function Fireteam_AspenInit(name, callback) {
  // stubbed
}

var Fireteam_AspenSend = function (name, callback) {
  console.log('Fireteam!');
  console.log(name);
  console.log(callback);
};

function AchievementUnityComm() {}

AchievementUnityComm.doUnityLoaded = function () {
  // stubbed
};

AchievementUnityComm.doUnityGameStarted = function () {
  // stubbed
};

function TopScoresModuleComm() {}

TopScoresModuleComm.onScore = function () {
  // stubbed
};

var UnityRequest = function (
  go_name,
  responce_func,
  request_name,
  request_param
) {
  alert('llego msg!' + 'obj: ' + go_name + '.' + responce_func + '()');
  alert(request_name);
  if (request_name == 'isLoggedIn') {
    unity.SendMessage(go_name, responce_func, 'LOGGED');
  } else if (request_name == 'checkAuthorization') {
    unity.SendMessage(go_name, responce_func, 'AUTHORIZED');
  } else if (request_name == 'readCookie') {
    unity.SendMessage(go_name, responce_func, '');
  }
};

var LoginModule = function () {};

LoginModule.showLoginWindow = function () {
  window.location.href = '/login';
  /*
  if (true) {
    //TODO: Fix so it is variable
    window.location.href =
      'https://discord.com/api/oauth2/authorize?client_id=' +
      client_id +
      '&redirect_uri=' +
      redirect +
      '&response_type=code&scope=identify';
  } else {
    console.log(discord_enabled);
    var userName = prompt('Enter name');
    if (userName != null) {
      try {
        fetch(`service/authenticate/user/${userName.replace('%20', '')}`, {
          //If user exists, requests the user information to authenticate
          Method: 'GET',
          Body: JSON.stringify({ username: userName }),
          Mode: 'cors',
          Cache: 'default',
        })
          .then((res) => res.json())
          .then((data) => {
            if (data != null) {
              (document.cookie = `TEGid=${data.user.TEGid}`),
                (document.cookie = `authid=${data.user.authid}`);
              document.cookie = `dname=${data.user.dname}`;
              document.cookie = `authpass=${data.user.authpass}`;
              document.cookie = 'logged=true';
              unity.SendMessage('MainGameController', 'HandleLogin', '');
            } else {
              //If user does not exist, it asks for a password to add as the authpass to the database. If passwords are stored, will need to hash
              var password = prompt('New user! Enter password');
              if (password != null) {
                var xhr = new XMLHttpRequest();
                xhr.open(
                  'POST',
                  `service/authenticate/user/${userName.replace('%20', ' ')}`
                );

                xhr.onload = () => {
                  var userData = JSON.parse(xhr.response);

                  (document.cookie = `TEGid=${userData.TEGid}`),
                    (document.cookie = `authid=${userData.authid}`);
                  document.cookie = `dname=${userData.dname}`;
                  document.cookie = `authpass=${userData.authpass}`;
                  document.cookie = 'logged=true';
                  unity.SendMessage('MainGameController', 'HandleLogin', '');
                };

                xhr.send(JSON.stringify({ password: password }));
              }
            }
          })
          .catch((err) => {
            console.log(err);
          });
      } catch (e) {
        console.log(e);
      }
    }
  }
  */
};
