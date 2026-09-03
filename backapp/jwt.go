package main

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"os"
	"strings"
	"time"
)

type contextKey string

const (
	UserClaimsContextKey contextKey = "userClaims"
	DefaultJWTSecret                 = "sunpos-super-secure-jwt-hmac256-pos-secret-2026-cloud"
	TokenDuration                    = 24 * time.Hour // POS Shift Token Duration (24 Hours)
)

func getJWTSecret() []byte {
	secret := os.Getenv("JWT_SECRET")
	if secret == "" {
		secret = DefaultJWTSecret
	}
	return []byte(secret)
}

// JWTHeader represents RFC 7519 JWT header
type JWTHeader struct {
	Alg string `json:"alg"`
	Typ string `json:"typ"`
}

// JWTClaims represents claims embedded in the POS authentication token
type JWTClaims struct {
	UserID      string   `json:"sub"`
	Username    string   `json:"username"`
	FullName    string   `json:"fullName"`
	CompanyID   string   `json:"companyId"`
	BranchID    string   `json:"branchId"`
	DeviceID    string   `json:"deviceId"`
	Permissions []string `json:"permissions"`
	IssuedAt    int64    `json:"iat"`
	ExpiresAt   int64    `json:"exp"`
}

// HasPermission checks if the token claims contain a given permission
func (c *JWTClaims) HasPermission(permission string) bool {
	for _, p := range c.Permissions {
		if p == permission || p == "ROLE_SUPER_ADMIN" || p == "USER_MANAGE" {
			return true
		}
	}
	return false
}

// GenerateJWT creates a signed HMAC-SHA256 JWT Token
func GenerateJWT(user User, branchID, deviceID string) (string, error) {
	now := time.Now()
	claims := JWTClaims{
		UserID:      user.UserID,
		Username:    user.Username,
		FullName:    user.FullName,
		CompanyID:   user.CompanyID,
		BranchID:    branchID,
		DeviceID:    deviceID,
		Permissions: user.Permissions,
		IssuedAt:    now.Unix(),
		ExpiresAt:   now.Add(TokenDuration).Unix(),
	}

	header := JWTHeader{
		Alg: "HS256",
		Typ: "JWT",
	}

	headerBytes, err := json.Marshal(header)
	if err != nil {
		return "", fmt.Errorf("failed to marshal JWT header: %w", err)
	}

	claimsBytes, err := json.Marshal(claims)
	if err != nil {
		return "", fmt.Errorf("failed to marshal JWT claims: %w", err)
	}

	headerB64 := base64.RawURLEncoding.EncodeToString(headerBytes)
	claimsB64 := base64.RawURLEncoding.EncodeToString(claimsBytes)

	signingInput := headerB64 + "." + claimsB64
	mac := hmac.New(sha256.New, getJWTSecret())
	mac.Write([]byte(signingInput))
	signature := mac.Sum(nil)
	sigB64 := base64.RawURLEncoding.EncodeToString(signature)

	return signingInput + "." + sigB64, nil
}

// VerifyJWT validates the signature and expiration of a JWT token string
func VerifyJWT(tokenString string) (*JWTClaims, error) {
	parts := strings.Split(tokenString, ".")
	if len(parts) != 3 {
		return nil, errors.New("invalid token format")
	}

	headerB64, claimsB64, sigB64 := parts[0], parts[1], parts[2]

	// Verify Signature
	signingInput := headerB64 + "." + claimsB64
	mac := hmac.New(sha256.New, getJWTSecret())
	mac.Write([]byte(signingInput))
	expectedSignature := mac.Sum(nil)

	actualSignature, err := base64.RawURLEncoding.DecodeString(sigB64)
	if err != nil {
		return nil, errors.New("failed to decode signature")
	}

	if !hmac.Equal(actualSignature, expectedSignature) {
		return nil, errors.New("invalid token signature")
	}

	// Decode claims
	claimsBytes, err := base64.RawURLEncoding.DecodeString(claimsB64)
	if err != nil {
		return nil, errors.New("failed to decode claims")
	}

	var claims JWTClaims
	if err := json.Unmarshal(claimsBytes, &claims); err != nil {
		return nil, fmt.Errorf("failed to unmarshal claims: %w", err)
	}

	// Check expiration
	if time.Now().Unix() > claims.ExpiresAt {
		return nil, errors.New("token has expired")
	}

	return &claims, nil
}

// ExtractClaims extracts verified JWTClaims from the request context
func ExtractClaims(r *http.Request) *JWTClaims {
	if val := r.Context().Value(UserClaimsContextKey); val != nil {
		if claims, ok := val.(*JWTClaims); ok {
			return claims
		}
	}
	return nil
}

// JWTMiddleware validates Bearer JWT token on incoming requests and enforces permissions
func JWTMiddleware(next http.HandlerFunc, requiredPermissions ...string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			errorResponse(w, http.StatusUnauthorized, "Missing Authorization header (Bearer token required)")
			return
		}

		parts := strings.Split(authHeader, " ")
		if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
			errorResponse(w, http.StatusUnauthorized, "Invalid Authorization header format. Expected 'Bearer <token>'")
			return
		}

		tokenString := parts[1]
		claims, err := VerifyJWT(tokenString)
		if err != nil {
			errorResponse(w, http.StatusUnauthorized, fmt.Sprintf("Authentication failed: %v", err))
			return
		}

		// Enforce permissions if specified
		if len(requiredPermissions) > 0 {
			authorized := false
			for _, reqPerm := range requiredPermissions {
				if claims.HasPermission(reqPerm) {
					authorized = true
					break
				}
			}
			if !authorized {
				errorResponse(w, http.StatusForbidden, fmt.Sprintf("Access denied: missing required permission (%s)", strings.Join(requiredPermissions, ", ")))
				return
			}
		}

		// Inject claims into context
		ctx := context.WithValue(r.Context(), UserClaimsContextKey, claims)
		next(w, r.WithContext(ctx))
	}
}
