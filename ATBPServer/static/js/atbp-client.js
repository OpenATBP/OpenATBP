var unity = null;

function isInternetExplorer() {
  if (window.document.documentMode) {
    return true;
  }
  return false;
}

function createParam(name, val) {
  var param = document.createElement('param');
  param.setAttribute('name', name);
  param.setAttribute('value', val);
  return param;
}

function logOut() {
  document.cookie = 'TEGid=;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  document.cookie = 'authid=;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  document.cookie = 'authpass=;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  document.cookie = 'dname=;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  document.cookie = 'logged=;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  document.cookie = 'session_token=;expires=Thu, 01 Jan 1970 00:00:00 GMT';
  location.reload();
}

function embedUnity() {
  var object = document.createElement('object');
  object.setAttribute('classid', 'clsid:444785F1-DE89-4295-863A-D46C3A781394');
  object.setAttribute(
    'codebase',
    'undefined/UnityWebPlayer.cab#version=2,0,0,0'
  );
  object.setAttribute('id', 'unity-object');
  object.setAttribute('width', '100%');
  object.setAttribute('height', '100%');

  var params = {
    src: 'CNChampions.unity3d',
    bordercolor: 'DF2900',
    backgroundcolor: '021E2F',
    textcolor: 'DF2900',
    disableContextMenu: 'true',
    disablefullscreen: 'false',
    logoimage: 'logoimage.png',
    progressbarimage: 'progressbarimage.png',
    progressframeimage: 'progressframeimage.png',
  };

  if (!isInternetExplorer()) {
    var embed = document.createElement('embed');
    embed.setAttribute('class', 'embed-responsive-item');
    embed.setAttribute('type', 'application/vnd.unity');
    embed.setAttribute('id', 'unity-embed');
    Object.keys(params).forEach(function (key) {
      embed.setAttribute(key, params[key]);
    });
  } else {
    Object.keys(params).forEach(function (key) {
      var paramToAppend = createParam(key, params[key]);
      object.appendChild(paramToAppend);
    });
  }

  var div = document.getElementById('embed-container');
  div.innerHTML = '';
  if (!isInternetExplorer()) {
    object.appendChild(embed);
    div.appendChild(object);
    unity = document.getElementById('unity-embed');
  } else {
    div.appendChild(object);
    unity = document.getElementById('unity-object');
  }
}

window.onload = function () {
  if (!isInternetExplorer()) {
    for (var i = 0; i < navigator.plugins.length; i++) {
      if (navigator.plugins[i].name.indexOf('Unity Player') != -1) {
        embedUnity();
      }
    }
  } else {
    try {
      var plugin = new ActiveXObject('UnityWebPlayer.UnityWebPlayer.1');
      embedUnity();
    } catch (e) {
      console.log('Failed to embed ActiveXObject');
    }
  }
  OnResize();
  var cookies = document.cookie.split(';');
  var displayName = null;
  for (var i = 0; i < cookies.length; i++) {
    if (cookies[i].indexOf('dname') != -1) {
      displayName = cookies[i]
        .replace('dname=', '')
        .replace(' ', '')
        .replace(';', '');
    }
  }
  if (displayName != null) {
    document.getElementById('login-button').remove();
    document.getElementById('username-text').innerHTML =
      'Logged in as ' + decodeURI(displayName);
  } else {
    document.getElementById('logout-button').remove();
  }
};

var OnResize = function () {
  if (unity != null) {
    unity.style.width = unity.parentElement.width;
    unity.style.height = window.innerHeight - 56 + 'px';
  }
};

function Fireteam_CheckMSIBLoggedIn(name, callback) {
  var cookies = document.cookie.split(';');
  var returnOK = null;
  for (var i = 0; i < cookies.length; i++) {
    if (cookies[i].indexOf('logged') != -1) {
      returnOK = cookies[i]
        .replace('logged=', '')
        .replace(' ', '')
        .replace(';', '');
    }
  }
  console.log(returnOK);
  if (returnOK == null) returnOK = 'false';
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
  // console.log(callback);
  /*
  var cookies = document.cookie.split(';');
  var displayName = null;
  for (var i = 0; i < cookies.length; i++) {
    if (cookies[i].indexOf('TEGid') != -1) {
      displayName = cookies[i]
        .replace('TEGid=', '')
        .replace(' ', '')
        .replace(';', '');
    }
  }
  var jsonObj = JSON.parse(callback);
  if (jsonObj.gameLocation != undefined) {
    fetch(
      'http://127.0.0.1:8000/location/' +
        displayName +
        '/' +
        jsonObj.gameLocation,
      (res) => {
        console.log(res.json());
      }
    ).catch(console.error);
  }
  */
};

var AchievementUnityComm = {
  doUnityLoaded: function () {
    // stubbed
  },
  doUnityGameStarted: function () {
    // stubbed
  },

  doSendStat: function () {
    // stubbed
  },
};

var TopScoresModuleComm = {
  onScore: function () {
    // stubbed
  },
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
