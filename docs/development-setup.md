# SunPOS Development Setup Guide

## Prerequisites

- **Java Development Kit (JDK)**: JDK 21 (Temurin / Oracle)
- **Go**: v1.22+ or v1.27+
- **Node.js**: v20+ or v24+ (npm 10+)
- **Cloud Database**: Neon PostgreSQL (Serverless / AWS Singapore)
- **Local Database Engine**: SQLite 3 (Pure Go `modernc.org/sqlite`)

---

## Directory Map

- `/backend`: Kotlin Spring Boot 3.x Modular Monolith (Neon PostgreSQL, WebSocket STOMP)
- `/backapp`: Windows Go Background Service (SQLite WAL, STOMP Client, ESC/POS Thermal Printer)
- `/sunpos`: Web POS Cashier & Backoffice App (React + TypeScript + Vite, Port 5173)
- `/qrorder`: Mobile-First Customer QR Ordering App (React + TypeScript + TailwindCSS v4, Port 5174)
- `/android-pos`: Android POS Application (Jetpack Compose, Room, WorkManager)
- `/docs`: Architecture, Domain, Sync, Database Strategy & BA/SA Specifications

---

## 1. Backend Setup (`/backend`)

1. Verify environment configuration or `src/main/resources/application.yml`:
   ```yaml
   spring:
     datasource:
       url: ${DATABASE_URL:postgresql://user:password@host/neondb?sslmode=require}
       username: ${DATABASE_USERNAME:neondb_owner}
       password: ${DATABASE_PASSWORD:your_password_here}
   ```
2. Build and run tests:
   ```bash
   cd backend
   ./mvnw test
   ./mvnw spring-boot:run
   ```
3. Cloud Backend service runs on `http://localhost:8080` (STOMP broker at `ws://localhost:8080/ws`).

---

## 2. Customer QR Ordering App (`/qrorder`)

1. Install dependencies:
   ```bash
   cd qrorder
   npm install
   ```
2. Launch Vite development server:
   ```bash
   npm run dev
   ```
3. App is available at `http://localhost:5174/?branchId=branch-001&tableNumber=A05`
   *(Port 5174 is used to prevent collision with the main POS on 5173).*

---

## 3. Go Branch Background Service (`/backapp`)

1. Run and build:
   ```bash
   cd backapp
   go test -v .
   go run .
   ```
2. Configuration file `qr_config.json`:
   ```json
   {
     "cloudWsUrl": "ws://localhost:8080/ws",
     "cloudHttpUrl": "http://localhost:8080",
     "branchId": "branch-001",
     "activeKey": "SUN-BRANCH-001-KEY",
     "printer": {
       "enabled": true,
       "printerIp": "192.168.1.200",
       "printerPort": 9100,
       "paperWidth": 32,
       "buzzerEnabled": true
     }
   }
   ```
3. Local REST API runs on `http://localhost:8888`.

---

## 4. Web POS Application (`/sunpos`)

1. Install & launch:
   ```bash
   cd sunpos
   npm install
   npm run dev
   ```
2. POS cashier interface is available at `http://localhost:5173`.
