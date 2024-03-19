package main

import (
	"fmt"
	"log"
	"os"
	"strings"
)

type ILogger struct {
	Fatal     func(format string, v ...interface{})
	Fatalln   func(v ...interface{})
	Error     func(format string, v ...interface{})
	Errorln   func(v ...interface{})
	Warning   func(format string, v ...interface{})
	Warningln func(v ...interface{})
	Info      func(format string, v ...interface{})
	Infoln    func(v ...interface{})
	Debug     func(format string, v ...interface{})
	Debugln   func(v ...interface{})
}

func setupLogger() ILogger {
	var _logger = log.New(os.Stderr, "", 0)
	var logger = ILogger{
		Fatal:     log.Fatalf,
		Fatalln:   log.Fatalln,
		Errorln:   func(v ...interface{}) {},
		Warningln: func(v ...interface{}) {},
		Infoln:    func(v ...interface{}) {},
		Debugln:   func(v ...interface{}) {},
	}
	level := strings.ToLower(config.LogLevel)
	switch level {
	case "debug":
		logger.Debugln = func(v ...interface{}) { _logger.Println(v...) }
		fallthrough
	case "info":
		logger.Infoln = func(v ...interface{}) { _logger.Println(v...) }
		fallthrough
	case "warn":
		logger.Warningln = func(v ...interface{}) { _logger.Println(v...) }
		fallthrough
	case "error":
		logger.Errorln = func(v ...interface{}) { _logger.Println(v...) }
	}
	logger.Error = func(format string, v ...interface{}) { logger.Errorln(fmt.Sprintf(format, v...)) }
	logger.Warning = func(format string, v ...interface{}) { logger.Warningln(fmt.Sprintf(format, v...)) }
	logger.Info = func(format string, v ...interface{}) { logger.Infoln(fmt.Sprintf(format, v...)) }
	logger.Debug = func(format string, v ...interface{}) { logger.Debugln(fmt.Sprintf(format, v...)) }
	return logger
}

var logger ILogger = setupLogger()
