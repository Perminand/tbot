# Многоэтапная сборка для оптимизации размера образа
FROM maven:latest AS build

# Установка рабочей директории
WORKDIR /app

# Копирование файлов зависимостей
COPY pom.xml .
COPY src ./src

# Сборка приложения
RUN mvn clean package -DskipTests

# Этап выполнения
FROM amazoncorretto:21-alpine

# Установка рабочей директории
WORKDIR /app

# Создание пользователя для безопасности
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Копирование собранного JAR файла
COPY --from=build /app/target/*.jar app.jar

# Создание директории для логов
RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# Переключение на непривилегированного пользователя
USER appuser

# Открытие порта (исправляем на 8080 для production)
EXPOSE 8080

# Переменные окружения по умолчанию
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE="docker"

# Команда запуска
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]