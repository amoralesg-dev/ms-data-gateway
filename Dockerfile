# Multi-stage Dockerfile for MS Data Gateway
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Data Gateway Team"
LABEL description="Microservicio Data Gateway para Progress OpenEdge"

WORKDIR /app

# Crear usuario sin privilegios para ejecución segura
RUN groupadd -r gateway && useradd -r -g gateway gateway

# Copiar el artefacto fat JAR generado
COPY target/ms-data-gateway.jar app.jar

# Cambiar permisos al usuario gateway
RUN chown -R gateway:gateway /app

USER gateway

# Puerto HTTP estándar del microservicio
EXPOSE 8080

# Parámetros JVM optimizados para contenedores
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
