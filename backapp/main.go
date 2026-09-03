package main

import (
	"log"
	"net/http"
	"os"
	"strings"
)

// corsMiddleware adds CORS headers to allow Web POS frontend access
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Device-ID, X-Branch-ID")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func main() {
	log.Println("==================================================")
	log.Println("☀️  SunPOS Background Service (System Tray Mode)")
	log.Println("==================================================")

	port := os.Getenv("PORT")
	if port == "" {
		port = "8888"
	}
	if !strings.HasPrefix(port, ":") {
		port = ":" + port
	}

	// 1. Initialize self-healing Supervisor Engine
	supervisor := NewSupervisor(port)

	// 2. Start Supervisor Loop in background goroutine (Auto-reload on crash)
	go supervisor.RunSupervisorLoop()

	// 3. Start Windows System Tray on Main Thread
	err := RunSystemTray(
		// On Reload Callback
		func() {
			supervisor.TriggerReload()
		},
		// On Exit Callback
		func() {
			log.Println("[Main] Graceful shutdown initiated...")
			supervisor.Stop()
		},
	)

	if err != nil {
		log.Printf("[Main] System tray fallback: %v. Running in background...", err)
		select {} // Keep running if tray failed
	}
}