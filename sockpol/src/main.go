package main

import (
	"fmt"
	"net"
	"time"
)

func clientWorker(socket net.Conn, content string, config IConfig) {
	defer socket.Close()
	var timeoutSeconds int = config.TimeoutSeconds
	var request string = fmt.Sprintf("%s\x00", config.Request)
	buffer := make([]byte, 1024)
	socket.SetDeadline(time.Now().Add(time.Duration(uint64(timeoutSeconds) * uint64(time.Second))))
	n, err := socket.Read(buffer)
	if err != nil {
		logger.Error("Socket error: %s", err.Error())
		return
	}
	data := string(buffer[:n])
	if data == request {
		logger.Debug("Received request from %s", socket.RemoteAddr().String())
		socket.Write([]byte(content))
	} else {
		logger.Debug("Invalid connection from %s", socket.RemoteAddr().String())
	}
}

func setupServer(port int, content string, config IConfig) {
	server, err := net.ListenTCP("tcp", &net.TCPAddr{Port: port, IP: net.ParseIP("0.0.0.0")}) // fmt.Sprintf(":%d", port)
	if err != nil {
		logger.Fatal("Failed to start server: %s", err.Error())
	}
	defer server.Close()
	logger.Info("SocketPolicyServer running on port %d", port)

	for {
		socket, err := server.Accept()
		if err != nil {
			logger.Error("Failed to accept connection: %s", err.Error())
			continue
		}
		go clientWorker(socket, content, config)
	}
}

func main() {
	var port int = config.Port
	content := createPoliciesFile(config)
	setupServer(port, content, config)
}
