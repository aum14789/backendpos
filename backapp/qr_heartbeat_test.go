package main

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

func TestParseHeartbeatHeader(t *testing.T) {
	cases := []struct {
		frame      string
		expectedSx int
		expectedSy int
	}{
		{
			frame:      "CONNECTED\nversion:1.2\nheart-beat:10000,10000\n\n\x00",
			expectedSx: 10000,
			expectedSy: 10000,
		},
		{
			frame:      "CONNECTED\nversion:1.2\nHEART-BEAT:5000,15000\nuser-name:branch-001\n\n\x00",
			expectedSx: 5000,
			expectedSy: 15000,
		},
		{
			frame:      "CONNECTED\nversion:1.2\n\n\x00",
			expectedSx: 10000,
			expectedSy: 10000,
		},
	}

	for i, tc := range cases {
		t.Run(fmt.Sprintf("case_%d", i), func(t *testing.T) {
			sx, sy := parseHeartbeatHeader(tc.frame)
			if sx != tc.expectedSx || sy != tc.expectedSy {
				t.Errorf("expected (%d, %d), got (%d, %d)", tc.expectedSx, tc.expectedSy, sx, sy)
			}
		})
	}
}

func TestSTOMPHeartbeatAndOrderFlow(t *testing.T) {
	var heartbeatsReceived int32
	var connected atomic.Bool

	upgrader := websocket.Upgrader{CheckOrigin: func(r *http.Request) bool { return true }}

	// 1. Create Mock STOMP Server
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ws, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer ws.Close()

		// Read CONNECT
		_, msg, err := ws.ReadMessage()
		if err != nil {
			return
		}
		if !strings.HasPrefix(string(msg), "CONNECT") {
			t.Errorf("expected CONNECT frame, got: %s", string(msg))
			return
		}

		// Send CONNECTED with 100ms heartbeat for rapid test
		connectedFrame := "CONNECTED\nversion:1.2\nheart-beat:100,100\n\n\x00"
		_ = ws.WriteMessage(websocket.TextMessage, []byte(connectedFrame))

		// Read SUBSCRIBE
		_, _, _ = ws.ReadMessage()
		connected.Store(true)

		// Listen for heartbeats & send server heartbeats
		for i := 0; i < 5; i++ {
			_ = ws.WriteMessage(websocket.TextMessage, []byte("\n")) // Server heartbeat
			_, ping, err := ws.ReadMessage()
			if err != nil {
				return
			}
			if string(ping) == "\n" {
				atomic.AddInt32(&heartbeatsReceived, 1)
			}
		}
	}))
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http")

	listener := NewQROrderListener(nil, &QROrderConfig{
		CloudWSURL: wsURL,
		BranchID:   "branch-hb-test",
		ActiveKey:  "KEY-123",
	})

	// Run connection in background
	go func() {
		_ = listener.connectAndListen()
	}()

	// Wait up to 2 seconds for heartbeats to be exchanged
	time.Sleep(1200 * time.Millisecond)
	listener.Stop()

	if !connected.Load() {
		t.Errorf("expected client to connect to mock STOMP server")
	}

	hbCount := atomic.LoadInt32(&heartbeatsReceived)
	if hbCount == 0 {
		t.Errorf("expected client to send heartbeat frames to server, got: %d", hbCount)
	}
}
