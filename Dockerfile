# === Build stage ===
FROM maven:3.9.7-eclipse-temurin-21 AS builder
WORKDIR /app

# Кешируем зависимости
COPY pom.xml .
RUN mvn -B -q -e -DskipTests dependency:go-offline

# Копируем исходники и собираем
COPY src ./src
RUN mvn -B -q -DskipTests package

# === Run stage ===
FROM eclipse-temurin:21-jre
WORKDIR /app

# Создаем непривилегированного пользователя
RUN useradd -r -s /bin/false appuser

# Копируем собранный jar
COPY --from=builder /app/target/*-SNAPSHOT.jar /app/app.jar

# Порт приложения
EXPOSE 8081

# Переменные окружения для БД (по умолчанию)
ENV DB_URL=jdbc:postgresql://postgres:5432/tbot_db \
    DB_USER=postgres \
    DB_PASSWORD=password \
    JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

USER appuser

ENTRYPOINT ["java","-jar","/app/app.jar"]
