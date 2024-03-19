package main

import (
	"fmt"
	"net"
	"testing"
)

func TestNETServer_Run(t *testing.T) {
	var port int = 1843
	setupServer(port, "content", IConfig{LogLevel: "debug", Request: "request"})
	// Simply check that the server is up and can
	// accept connections.
	socket, err := net.Dial("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		t.Errorf("Could not connect to server: %s", err.Error())
	}
	defer socket.Close()
}

// func TestNETServer_Request(t *testing.T) {
// 	tt := []struct {
// 		test    string
// 		payload []byte
// 		want    []byte
// 	}{
// 		{
// 			"Sending a simple request returns result",
// 			[]byte("hello world\n"),
// 			[]byte("Request received: hello world"),
// 		},
// 		{
// 			"Sending another simple request works",
// 			[]byte("goodbye world\n"),
// 			[]byte("Request received: goodbye world"),
// 		},
// 	}

// 	for _, tc := range tt {
// 		t.Run(tc.test, func(t *testing.T) {
// 			conn, err := net.Dial("tcp", ":843")
// 			if err != nil {
// 				t.Error("could not connect to TCP server: ", err)
// 			}
// 			defer conn.Close()

// 			if _, err := conn.Write(tc.payload); err != nil {
// 				t.Error("could not write payload to TCP server:", err)
// 			}

// 			out := make([]byte, 1024)
// 			if _, err := conn.Read(out); err == nil {
// 				if bytes.Equal(out, tc.want) {
// 					t.Error("response did match expected output")
// 				}
// 			} else {
// 				t.Error("could not read from connection")
// 			}
// 		})
// 	}
// }
