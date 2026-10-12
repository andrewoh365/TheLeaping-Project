# Stage 1: build the spring boot Jar
FROM maven:3.9-eclipse-temurin-21 AS build 
WORKDIR /app 
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests 
    #Test will be ran on Jenkins pipeline


# Stage 2: Run Jar on a small java runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup 
COPY --from=build /app/target/portfolio-app-*.jar app.jar 
USER appuser 
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
