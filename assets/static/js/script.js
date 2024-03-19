var unity = document.getElementById('unity_player');

function OnResize() {
  unity.style.width = window.innerWidth + 'px';
  unity.style.height = window.innerHeight + 'px';
}

// function Fireteam_AspenInit(name, callback) { } // stubbed

var AchievementUnityComm = {
  doUnityLoaded: function () { }, // stubbed
  doUnityGameStarted: function () { }, // stubbed
  doSendStat: function (codeValue) { }, // stubbed
};

var TopScoresModuleComm = {
  onScore: function (score) { }, // stubbed
};

var Fireteam_AspenSend = function (messageType, text, applicationName) { }; // stubbed

var Fireteam_AspenGetData = function (name, callback) {
  // unity.SendMessage(name, callback, sessionId);
}; // stubbed

function Fireteam_CheckMSIBLoggedIn(name, callback) {
  unity.SendMessage(name, callback, document.cookie.split("; ").find((row) => row.startsWith("logged"))?.split("=")[1] || "false");
}

function Fireteam_GetCookies(name, callback) {
  unity.SendMessage(name, callback, document.cookie);
}

var LoginModule = {
  showLoginWindow: function (options, event) {
    // TODO: get login url from loaded config
    // var api_url = "<%- api_url %>";
    // window.location.href = api_url + 'auth/login';
    window.location.href = location.origin + "/" + location.pathname + "/auth/login";
  }
};

function cnGameTracking(event) {
  // event === "INIT", "START", "PLAY", "REPLAY", "BUY", "TIME SPENT"
} // stubbed

function UnityRequest(go_name, responce_func, request_name, request_param) {
  console.log("UnityRequest! " + go_name + ", " + responce_func + ", " + request_name + ", " + request_param);
  if (request_name == "isLoggedIn") {
    unity.SendMessage(go_name, responce_func, "LOGGED");
  }
  else if (request_name == "checkAuthorization") {
    unity.SendMessage(go_name, responce_func, "AUTHORIZED");
  }
  else if (request_name == "readCookie") {
    unity.SendMessage(go_name, responce_func, "");
  }
};

OnResize();
