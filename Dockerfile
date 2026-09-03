# Multi-stage build for SunPOS Spring Boot Kotlin Backend (Render Optimized)

# ------------------------------------------------------------------------------
# Stage 1: Build JAR with memory-constrained Maven
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Cap Maven & Kotlin compiler memory during build to prevent OOM
ENV MAVEN_OPTS="-Xmx512m -XX:+TieredCompilation -XX:TieredStopAtLevel=1"

# Cache dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build application JAR (skip tests to speed up deploy & save memory)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal & Memory-Safe Runtime for Render (512MB RAM limit)
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

# Render assigns dynamic PORT environment variable (typically 10000 on Render, 8080 locally)
EXPOSE 8080 10000

# JVM Flags optimized for Render Free / Starter Tier (512MB RAM limit):
# - UseSerialGC: Minimal memory overhead garbage collector for small containers
# - Xmx320m / Xms128m: Keeps heap safely within 512MB limit, preventing exit code 137 (OOM)
# - MaxMetaspaceSize=128m: Prevents metaspace growth
# - Dserver.port=${PORT:-8080}: Dynamically binds to Render's assigned $PORT
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:+UseSerialGC -Xmx320m -Xms128m -XX:MaxMetaspaceSize=128m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
