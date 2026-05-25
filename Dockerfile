# Bước 1: Dùng môi trường Maven chuẩn cho Java 21 để build code
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Bước 2: Dùng môi trường Java 21 siêu nhẹ để chạy file .jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Render sẽ tự động tìm file .jar trong thư mục target và đổi tên thành app.jar
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]