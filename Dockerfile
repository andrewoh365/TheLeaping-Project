FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/portfolio-app/pom.xml .
COPY backend/portfolio-app/src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=build /src/target/portfolio-app-*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
