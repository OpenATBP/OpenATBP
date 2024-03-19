package main

import (
	"os"
	"strconv"
)

type IConfig struct {
	LogLevel                  string
	Port                      int
	StaticDirectory           string
	StaticRoute               string
	ConfigDocumentRoute       string
	ConfigDocumentIndent      bool
	ConfigDocumentSelfclosing bool
	ConfigStreamedAssetUrl    string
	ConfigZone                string
	ConfigDebug               string
	ConfigDebugConsole        string
	ConfigLobbyIp             string
	ConfigLobbyPort           string
	ConfigLobbyPolicyPort     string
	ConfigLanguage            string
	ConfigFacadeUrl           string
	ConfigShowPromo           string
	ConfigPresenceUrl         string
	ConfigPresenceUpdateDelay string
	ConfigDirectPresence      string
	ConfigDevLogin            string
	ConfigDevMaps             string
	ConfigGuestModeOnly       string
	ConfigAspenEnabled        string
	ConfigPingLimit           string
}

func setupConfig() IConfig {
	return IConfig{
		LogLevel:                  envString("LOG_LEVEL", "debug"),
		Port:                      int(envInt("ASSETS_PORT", 8081)),
		StaticDirectory:           envString("ASSETS_STATIC_DIRECTORY", "./static"),
		StaticRoute:               envString("ASSETS_STATIC_ROUTE", "/static"),
		ConfigDocumentRoute:       envString("ASSETS_CONFIG_DOCUMENT_ROUTE", "/config.xml"),
		ConfigDocumentIndent:      envBool("ASSETS_CONFIG_DOCUMENT_INDENT", true),
		ConfigDocumentSelfclosing: envBool("ASSETS_CONFIG_DOCUMENT_SELFCLOSING", false),
		ConfigStreamedAssetUrl:    envString("ASSETS_CONFIG_STREAMED_ASSET_URL", "http://127.0.0.1:8081"),
		ConfigZone:                envString("ASSETS_CONFIG_ZONE", "Champions"),
		ConfigDebug:               envString("ASSETS_CONFIG_DEBUG", "True"),
		ConfigDebugConsole:        envString("ASSETS_CONFIG_DEBUG_CONSOLE", "True"),
		ConfigLobbyIp:             envString("ASSETS_CONFIG_LOBBY_IP", "127.0.0.1"),
		ConfigLobbyPort:           envString("ASSETS_CONFIG_LOBBY_PORT", "6778"),
		ConfigLobbyPolicyPort:     envString("ASSETS_CONFIG_LOBBY_POLICY_PORT", "843"),
		ConfigLanguage:            envString("ASSETS_CONFIG_LANGUAGE", "English"),
		ConfigFacadeUrl:           envString("ASSETS_CONFIG_FACADE_URL", "http://127.0.0.1:8080"),
		ConfigShowPromo:           envString("ASSETS_CONFIG_SHOW_PROMO", "False"),
		ConfigPresenceUrl:         envString("ASSETS_CONFIG_PRESENCE_URL", "http://www.cartoonnetwork.com/presence/"),
		ConfigPresenceUpdateDelay: envString("ASSETS_CONFIG_PRESENCE_UPDATE_DELAY", "10000"),
		ConfigDirectPresence:      envString("ASSETS_CONFIG_DIRECT_PRESENCE", "False"),
		ConfigDevLogin:            envString("ASSETS_CONFIG_DEV_LOGIN", "False"),
		ConfigDevMaps:             envString("ASSETS_CONFIG_DEV_MAPS", "True"),
		ConfigGuestModeOnly:       envString("ASSETS_CONFIG_GUEST_MODE_ONLY", "False"),
		ConfigAspenEnabled:        envString("ASSETS_CONFIG_ASPEN_ENABLED", "False"),
		ConfigPingLimit:           envString("ASSETS_CONFIG_PING_LIMIT", "2000"),
	}
}

var config IConfig = setupConfig()

func envString(key string, defaultValue string) string {
	value, exists := os.LookupEnv(key)
	if exists {
		return value
	}
	return defaultValue
}

func envInt(key string, defaultValue uint64) uint64 {
	value, exists := os.LookupEnv(key)
	if exists {
		v, err := strconv.ParseUint(value, 10, 64)
		if err == nil {
			return v
		}
	}
	return defaultValue
}

func envBool(key string, defaultValue bool) bool {
	value, exists := os.LookupEnv(key)
	if exists {
		v, err := strconv.ParseBool(value)
		if err == nil {
			return v
		}
	}
	return defaultValue
}
