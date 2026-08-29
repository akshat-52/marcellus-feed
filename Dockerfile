FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# NEW: Bring the src folder (and our posts!) into the final server
COPY --from=build /app/src /app/src
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]