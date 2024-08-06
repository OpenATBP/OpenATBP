var unity;
window.onload = function () {
  unity = document.getElementById('unity_player');
};

var OnResize = function () {
  unity.style.width = window.innerWidth + 'px';
  unity.style.height = window.innerHeight + 'px';
};

function Fireteam_CheckMSIBLoggedIn(name, callback) {
  var cookies = document.cookie.split(';');
  for (var c of cookies) {
    if (c.includes('logged')) {
      returnOK = c.replace('logged=', '').replace(' ', '').replace(';', '');
    }
  }
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

var LoginModule = {
  showLoginWindow: function () {
    window.location.href = '/login';
  },
};
