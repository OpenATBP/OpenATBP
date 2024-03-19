package main

import (
	"errors"
	"fmt"
	"net/http"
)

func configDocumentHandler(configDocument []byte) func(http.ResponseWriter, *http.Request) {
	return func(w http.ResponseWriter, r *http.Request) {
		_, err := w.Write(configDocument)
		if err != nil {
			logger.Error("Error sending response to %s: %s", r.RemoteAddr, err.Error())
		}
	}
}

func createServer(config IConfig) *http.Server {
	var port int = config.Port
	var staticDirectory string = config.StaticDirectory
	var staticRoute string = config.StaticRoute
	var configDocumentRoute string = config.ConfigDocumentRoute

	var mux *http.ServeMux = http.NewServeMux()
	var fs http.Handler = http.FileServer(http.Dir(staticDirectory))
	mux.Handle(staticRoute, http.StripPrefix(staticRoute, fs))

	var configDocument string = createConfigDocumentFile(config)
	var configDocumentBuffer []byte = []byte(configDocument)
	var handler func(http.ResponseWriter, *http.Request) = configDocumentHandler(configDocumentBuffer)
	mux.HandleFunc(configDocumentRoute, handler)

	var server *http.Server = &http.Server{Addr: fmt.Sprintf(":%d", port), Handler: mux}
	return server
}

func main() {
	var server *http.Server = createServer(config)

	logger.Info("AssetServer running on port %d", config.Port)
	err := server.ListenAndServe()
	if errors.Is(err, http.ErrServerClosed) {
		logger.Info("AssetServer closed")
	} else if err != nil {
		logger.Fatal("Failed to start server: %s", err.Error())
	}
}
