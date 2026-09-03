package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"runtime/debug"
	"strings"
	"sync"
	"time"
)

// Supervisor oversees the HTTP server and database, automatically reloading on crashes
type Supervisor struct {
	port       string
	httpServer *http.Server
	mu         sync.Mutex
	isStopping bool
	restartCh  chan struct{}
}

func NewSupervisor(port string) *Supervisor {
	if port == "" {
		port = "8888"
	}
	if !strings.HasPrefix(port, ":") {
		port = ":" + port
	}
	return &Supervisor{
		port:      port,
		restartCh: make(chan struct{}, 1),
	}
}

// TriggerReload requests an immediate restart of the backend engine
func (s *Supervisor) TriggerReload() {
	select {
	case s.restartCh <- struct{}{}:
	default:
	}
}

// Stop gracefully stops the supervisor and underlying server
func (s *Supervisor) Stop() {
	s.mu.Lock()
	s.isStopping = true
	s.mu.Unlock()

	if s.httpServer != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = s.httpServer.Shutdown(ctx)
	}
}

// RunSupervisorLoop runs the server inside a crash-recovery loop
func (s *Supervisor) RunSupervisorLoop() {
	crashCount := 0
	for {
		s.mu.Lock()
		if s.isStopping {
			s.mu.Unlock()
			break
		}
		s.mu.Unlock()

		log.Println("[Supervisor] 🚀 Starting SunPOS Backend Core Service...")

		// Run engine protected with panic recovery
		s.runEngineSafe(&crashCount)

		s.mu.Lock()
		if s.isStopping {
			s.mu.Unlock()
			break
		}
		s.mu.Unlock()

		// Wait briefly before reloading
		log.Println("[Supervisor] ⏳ Auto-Recovery in progress... Reloading in 1 second...")
		time.Sleep(1 * time.Second)
	}
}

func (s *Supervisor) runEngineSafe(crashCount *int) {
	defer func() {
		if r := recover(); r != nil {
			*crashCount++
			stack := string(debug.Stack())
			errLog := fmt.Sprintf("=== SUNPOS BACKEND CRASH #%d ===\nTimestamp: %s\nError: %v\nStack Trace:\n%s\n=================================\n\n",
				*crashCount, time.Now().Format(time.RFC3339), r, stack)

			log.Printf("[Supervisor] 💥 CRASH DETECTED: %v", r)
			_ = os.WriteFile("crash.log", []byte(errLog), 0644)

			ShowTrayNotification("⚠️ SunPOS Service Recovered", fmt.Sprintf("Backend crashed and reloaded automatically (#%d).", *crashCount))
		}
	}()

	// 1. Initialize SQLite Database
	db, err := InitDB("pos.db")
	if err != nil {
		log.Printf("[Supervisor] DB Init Error: %v", err)
		time.Sleep(2 * time.Second)
		return
	}
	defer db.Close()

	// 2. Seed Initial Master Data
	if err := SeedDatabase(db); err != nil {
		log.Printf("[Supervisor] DB Seed Warning: %v", err)
	}

	// 3. Start Offline-First 2-Way Sync Engine
	syncEngine := InitSyncEngine()
	syncEngine.Start()
	defer syncEngine.Stop()

	// 3.1 Start Cloud QR Order Listener (WebSocket STOMP + Fallback Poller)
	qrListener := StartQROrderListener(db)
	if qrListener != nil {
		defer qrListener.Stop()
	}

	// 4. Setup Routes
	mux := http.NewServeMux()

	// Auth & Device Activation (Multi-Branch POS Identity)
	mux.HandleFunc("/api/auth/pin", HandlePinLogin)
	mux.HandleFunc("/api/device/identity", HandleGetDeviceIdentity)
	mux.HandleFunc("/api/device/activate", HandleActivateDevice)
	mux.HandleFunc("/api/device/deactivate", HandleDeactivateDevice)
	mux.HandleFunc("/api/admin/activation-codes/generate", HandleGenerateActivationCode)
	mux.HandleFunc("/api/admin/activation-codes", HandleListActivationCodes)

	// Tables & Zones
	mux.HandleFunc("/api/zones", HandleGetZones)
	mux.HandleFunc("/api/tables", HandleGetTables)
	mux.HandleFunc("/api/tables/move", JWTMiddleware(HandleMoveTable))
	mux.HandleFunc("/api/tables/merge", JWTMiddleware(HandleMergeTables))
	mux.HandleFunc("/api/tables/", func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/status") {
			JWTMiddleware(HandleUpdateTableStatus)(w, r)
		} else {
			http.NotFound(w, r)
		}
	})

	// Menu & Catalog
	mux.HandleFunc("/api/menu/categories", HandleGetMenuCategories)
	mux.HandleFunc("/api/menu/items", HandleGetMenuItems)

	// Buffet
	mux.HandleFunc("/api/buffet/tiers", HandleGetBuffetTiers)
	mux.HandleFunc("/api/buffet/sessions", HandleGetBuffetSessions)

	// CRM & Customers
	mux.HandleFunc("/api/customers", JWTMiddleware(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			HandleCreateCustomer(w, r)
		} else {
			HandleGetCustomers(w, r)
		}
	}))
	mux.HandleFunc("/api/customers/search", JWTMiddleware(HandleGetCustomers))

	// Promotions & Coupons
	mux.HandleFunc("/api/promotions/apply-coupon", JWTMiddleware(HandleApplyCoupon))

	// Orders & Payment
	mux.HandleFunc("/api/orders", JWTMiddleware(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodPost {
			HandleCreateOrder(w, r)
		} else {
			HandleGetActiveOrders(w, r)
		}
	}))
	mux.HandleFunc("/api/orders/", func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/status") {
			JWTMiddleware(HandleUpdateOrderStatus)(w, r)
		} else {
			http.NotFound(w, r)
		}
	})
	mux.HandleFunc("/api/orders/active", JWTMiddleware(HandleGetActiveOrders))
	mux.HandleFunc("/api/payments", JWTMiddleware(HandleProcessPayment))

	// QR Order Kitchen Reprint Endpoint: POST /api/orders/qr/{orderId}/reprint
	mux.HandleFunc("/api/orders/qr/", func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, "/reprint") && r.Method == http.MethodPost {
			parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
			if len(parts) >= 4 {
				orderID := parts[3]
				printerCfg := LoadPrinterConfig()
				if err := ReprintKitchenOrder(db, printerCfg, orderID); err != nil {
					jsonResponse(w, http.StatusInternalServerError, ApiResponse[any]{
						Success: false,
						Message: fmt.Sprintf("พิมพ์ซ้ำไม่สำเร็จ: %v", err),
					})
					return
				}
				jsonResponse(w, http.StatusOK, ApiResponse[any]{
					Success: true,
					Message: fmt.Sprintf("พิมพ์ซ้ำใบสั่งครัวสำหรับออเดอร์ %s สำเร็จ", orderID),
				})
				return
			}
		}
		http.NotFound(w, r)
	})

	// 2-Way Cloud Sync Engine Routes
	mux.HandleFunc("/api/sync/status", HandleGetSyncStatus)
	mux.HandleFunc("/api/sync/trigger", HandleTriggerSync)

	// Health Check & Simulated Crash trigger for testing
	mux.HandleFunc("/api/health", func(w http.ResponseWriter, r *http.Request) {
		jsonResponse(w, http.StatusOK, map[string]any{
			"status":     "healthy",
			"service":    "sunpos-backapp",
			"version":    "1.0.0",
			"port":       s.port,
			"autoReload": true,
			"sync":       syncEngine.GetStatus(),
		})
	})

	// Test endpoint to verify self-healing crash reload
	mux.HandleFunc("/api/test-crash", func(w http.ResponseWriter, r *http.Request) {
		log.Println("[Test] Simulated crash endpoint triggered. Panicking now...")
		panic("Simulated critical panic for auto-recovery verification")
	})

	// Recovery Middleware to catch handler panics, log crash, and auto-reload
	recoveryHandler := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rec := recover(); rec != nil {
				*crashCount++
				stack := string(debug.Stack())
				errLog := fmt.Sprintf("=== SUNPOS BACKEND CRASH #%d ===\nTimestamp: %s\nPath: %s\nError: %v\nStack Trace:\n%s\n=================================\n\n",
					*crashCount, time.Now().Format(time.RFC3339), r.URL.Path, rec, stack)

				log.Printf("[Supervisor] 💥 CRASH IN HANDLER (%s): %v", r.URL.Path, rec)
				_ = os.WriteFile("crash.log", []byte(errLog), 0644)

				ShowTrayNotification("⚠️ SunPOS Service Recovered", fmt.Sprintf("Backend caught crash on %s and auto-recovered (#%d).", r.URL.Path, *crashCount))

				jsonResponse(w, http.StatusInternalServerError, ApiResponse[any]{
					Success: false,
					Message: fmt.Sprintf("เกิดข้อผิดพลาดภายในระบบ แต่ระบบได้ทำการ Auto-Recover สำเร็จ: %v", rec),
				})
			}
		}()
		corsMiddleware(SecurityHeadersMiddleware(mux)).ServeHTTP(w, r)
	})

	s.mu.Lock()
	s.httpServer = &http.Server{
		Addr:         s.port,
		Handler:      recoveryHandler,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
	}
	s.mu.Unlock()

	log.Printf("[Supervisor] 🟢 SunPOS Go REST API is active on http://localhost%s", s.port)

	// Listen for manual restart requests
	go func() {
		select {
		case <-s.restartCh:
			log.Println("[Supervisor] 🔄 Manual restart requested via Tray menu.")
			if s.httpServer != nil {
				ctx, cancel := context.WithTimeout(context.Background(), 1*time.Second)
				defer cancel()
				_ = s.httpServer.Shutdown(ctx)
			}
		}
	}()

	if err := s.httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Printf("[Supervisor] HTTP server error: %v", err)
	}
}
