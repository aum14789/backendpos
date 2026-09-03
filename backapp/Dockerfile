# Multi-stage build for SunPOS Go Backapp Service
FROM golang:1.24-alpine AS builder
WORKDIR /app

# Cache Go modules
COPY go.mod go.sum ./
RUN go mod download

# Build statically linked binary
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-w -s" -o backapp .

# Minimal runtime stage
FROM alpine:3.20
WORKDIR /app

RUN apk --no-cache add ca-certificates tzdata

COPY --from=builder /app/backapp /app/backapp

EXPOSE 8888

ENTRYPOINT ["/app/backapp"]
