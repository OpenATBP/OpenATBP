package main

import (
	"encoding/xml"
	"regexp"
)

type ConfigDocument struct {
	XMLName             xml.Name `xml:"config"`
	StreamedAssetUrl    string   `xml:"StreamedAssetURL"`
	Zone                string   `xml:"Zone"`
	AspenConfigUrl      string   `xml:"AspenConfigURL"`
	AspenEnabled        string   `xml:"AspenEnabled"`
	Debug               string   `xml:"Debug"`
	DebugConsole        string   `xml:"DebugConsole"`
	LobbyIp             string   `xml:"LobbyIP"`
	LobbyPort           string   `xml:"LobbyPort"`
	LobbyPolicyPort     string   `xml:"LobbyPolicyPort"`
	Language            string   `xml:"Language"`
	FacadeUrl           string   `xml:"FacadeURL"`
	Profiler            string   `xml:"Profiler"`
	LobbyVersion        string   `xml:"LobbyVersion"`
	PingLimit           string   `xml:"PingLimit"`
	ShowPromo           string   `xml:"ShowPromo"`
	DevLogin            string   `xml:"DevLogin"`
	DevMaps             string   `xml:"DevMaps"`
	PresenceUpdateDelay string   `xml:"PresenceUpdateDelay"`
	PresenceUrl         string   `xml:"PresenceURL"`
	GuestModeOnly       string   `xml:"GuestModeOnly"`
	PromoImageUrl       string   `xml:"PromoImageURL"`
	PromoUrl            string   `xml:"PromoURL"`
	DirectPresence      string   `xml:"DirectPresence"`
}

func createConfigDocumentFile(config IConfig) string {
	var indent bool = config.ConfigDocumentIndent
	var selfclosing bool = config.ConfigDocumentSelfclosing
	policy := &ConfigDocument{
		StreamedAssetUrl:    config.ConfigStreamedAssetUrl,
		Zone:                config.ConfigZone,
		Debug:               config.ConfigDebug,
		DebugConsole:        config.ConfigDebugConsole,
		LobbyIp:             config.ConfigLobbyIp,
		LobbyPort:           config.ConfigLobbyPort,
		LobbyPolicyPort:     config.ConfigLobbyPolicyPort,
		Language:            config.ConfigLanguage,
		FacadeUrl:           config.ConfigFacadeUrl,
		ShowPromo:           config.ConfigShowPromo,
		PresenceUrl:         config.ConfigPresenceUrl,
		PresenceUpdateDelay: config.ConfigPresenceUpdateDelay,
		DirectPresence:      config.ConfigDirectPresence,
		DevLogin:            config.ConfigDevLogin,
		DevMaps:             config.ConfigDevMaps,
		GuestModeOnly:       config.ConfigGuestModeOnly,
		AspenEnabled:        config.ConfigAspenEnabled,
		PingLimit:           config.ConfigPingLimit,
	}
	var content []byte
	var err error
	if indent {
		content, err = xml.MarshalIndent(policy, "", "  ")
	} else {
		content, err = xml.Marshal(policy)
	}
	if err != nil {
		logger.Fatal("Failed to create config.xml file: %s", err.Error())
	}
	contentStr := xml.Header + string(content)
	// Turn nested tags into self-closing tags
	if selfclosing {
		contentStr = regexp.MustCompile("<(.+)(.*)></.+>").ReplaceAllString(contentStr, "<$1$2/>")
	}
	return contentStr
}
