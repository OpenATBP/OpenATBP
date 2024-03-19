package main

import (
	"os"
	"strconv"
)

type IConfig struct {
	LogLevel       string
	Port           int
	TimeoutSeconds int
	Request        string
	Policies       string
	Domain         string
	Headers        string
	Indent         bool
	Selfclosing    bool
}

func setupConfig() IConfig {
	return IConfig{
		LogLevel:       envString("LOG_LEVEL", "debug"),
		Port:           int(envInt("SOCKPOL_PORT", 843)),
		TimeoutSeconds: int(envInt("SOCKPOL_TIMEOUT_SECONDS", 60)),
		Request:        envString("SOCKPOL_REQUEST", "<policy-file-request/>"),
		Policies:       envString("SOCKPOL_POLICIES", "master-only"),
		Domain:         envString("SOCKPOL_DOMAIN", "*"),
		Headers:        envString("SOCKPOL_HEADERS", "*"),
		Indent:         envBool("SOCKPOL_INDENT", true),
		Selfclosing:    envBool("SOCKPOL_SELFCLOSING", true),
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
