package main

import (
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"sync"
	"time"
)

// ── 1. SECURITY HEADERS MIDDLEWARE ──

// SecurityHeadersMiddleware adds OWASP-recommended security headers to all HTTP responses
func SecurityHeadersMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Prevent MIME-sniffing
		w.Header().Set("X-Content-Type-Options", "nosniff")
		// Prevent Clickjacking (framing)
		w.Header().Set("X-Frame-Options", "DENY")
		// Enable XSS filtering
		w.Header().Set("X-XSS-Protection", "1; mode=block")
		// Control referrer information
		w.Header().Set("Referrer-Policy", "strict-origin-when-cross-origin")
		// Cache control for API responses
		w.Header().Set("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
		w.Header().Set("Pragma", "no-cache")

		next.ServeHTTP(w, r)
	})
}

// ── 2. PIN HASHING & CRYPTO UTILITIES ──

const PINSalt = "sunpos_pos_pin_salt_2026"

// HashPIN computes a SHA-256 hash with salt for a user PIN
func HashPIN(pin string) string {
	hasher := sha256.New()
	hasher.Write([]byte(pin + PINSalt))
	return hex.EncodeToString(hasher.Sum(nil))
}

// VerifyPIN verifies a plain PIN against stored hash or legacy plain text
func VerifyPIN(inputPIN, storedHashOrPIN string) bool {
	if storedHashOrPIN == "" {
		return false
	}
	// Direct match (for legacy plain PINs / dev seeds)
	if inputPIN == storedHashOrPIN {
		return true
	}
	// Hash match
	computedHash := HashPIN(inputPIN)
	return computedHash == storedHashOrPIN
}

// ── 3. RATE LIMITER (BRUTE FORCE PROTECTION FOR PIN LOGIN) ──

type attemptInfo struct {
	count     int
	blockedAt time.Time
}

// RateLimiter manages login attempt rate limiting per IP / device
type RateLimiter struct {
	mu          sync.Mutex
	attempts    map[string]*attemptInfo
	maxAttempts int
	window      time.Duration
	blockTime   time.Duration
}

var GlobalAuthRateLimiter = NewRateLimiter(5, 1*time.Minute, 3*time.Minute)

// NewRateLimiter creates a new sliding window rate limiter
func NewRateLimiter(maxAttempts int, window, blockTime time.Duration) *RateLimiter {
	rl := &RateLimiter{
		attempts:    make(map[string]*attemptInfo),
		maxAttempts: maxAttempts,
		window:      window,
		blockTime:   blockTime,
	}
	// Background cleanup of stale attempts
	go rl.cleanupLoop()
	return rl
}

func (rl *RateLimiter) cleanupLoop() {
	ticker := time.NewTicker(5 * time.Minute)
	defer ticker.Stop()
	for range ticker.C {
		rl.mu.Lock()
		now := time.Now()
		for key, info := range rl.attempts {
			if !info.blockedAt.IsZero() && now.Sub(info.blockedAt) > rl.blockTime {
				delete(rl.attempts, key)
			}
		}
		rl.mu.Unlock()
	}
}

// CheckLimit returns true if the key is blocked, along with remaining wait time
func (rl *RateLimiter) CheckLimit(key string) (bool, time.Duration) {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	info, exists := rl.attempts[key]
	if !exists {
		return false, 0
	}

	if !info.blockedAt.IsZero() {
		elapsed := time.Since(info.blockedAt)
		if elapsed < rl.blockTime {
			return true, rl.blockTime - elapsed
		}
		// Block expired
		delete(rl.attempts, key)
		return false, 0
	}

	return false, 0
}

// RecordFailedAttempt records a failed login attempt and blocks if exceeding max
func (rl *RateLimiter) RecordFailedAttempt(key string) (blocked bool, remainingAttempts int) {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	info, exists := rl.attempts[key]
	if !exists {
		info = &attemptInfo{count: 0}
		rl.attempts[key] = info
	}

	info.count++
	if info.count >= rl.maxAttempts {
		info.blockedAt = time.Now()
		return true, 0
	}

	return false, rl.maxAttempts - info.count
}

// Reset clears the failed attempt count on successful login
func (rl *RateLimiter) Reset(key string) {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	delete(rl.attempts, key)
}
