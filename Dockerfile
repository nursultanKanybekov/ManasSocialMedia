# ============================================================
# Stage 1 – Build
# ============================================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy dependency descriptors first (better layer caching)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Download dependencies
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src/ src/
RUN mvn clean package -Dmaven.test.skip=true -q

# ============================================================
# Stage 2 – Runtime
# ============================================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Create a non-root user for security
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create uploads directory
RUN mkdir -p /app/uploads/avatars /app/uploads/posts \
    && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
