# Environment Configuration

## Frontend Configuration (src/environments/environment.ts)

```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  auth: {
    tokenKey: 'jwt_token',
    refreshTokenKey: 'refresh_token',
    userKey: 'user'
  }
};
```

## Frontend Production Configuration (src/environments/environment.prod.ts)

```typescript
export const environment = {
  production: true,
  apiUrl: 'https://api.leaping-portfolio.com/api',
  auth: {
    tokenKey: 'jwt_token',
    refreshTokenKey: 'refresh_token',
    userKey: 'user'
  }
};
```

## Backend Configuration (application.yaml)

```yaml
# Development
spring:
  application:
    name: portfolio-app
  
  datasource:
    url: jdbc:mysql://localhost:3306/leaping_portfolio_dev
    username: dev_user
    password: dev_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: dev-secret-key-change-in-production-must-be-long-and-secure-at-least-256-bits
  expiration: 3600000          # 1 hour
  refresh-expiration: 604800000 # 7 days

server:
  port: 8080
  servlet:
    context-path: /

logging:
  level:
    root: INFO
    com.leaping: DEBUG
```

## Backend Production Configuration (application-prod.yaml)

```yaml
spring:
  application:
    name: portfolio-app
  
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET}
  expiration: 3600000          # 1 hour
  refresh-expiration: 604800000 # 7 days

server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12

logging:
  level:
    root: WARN
    com.leaping: INFO
  file:
    name: logs/application.log
```

## Docker Compose (docker-compose.yml)

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: leaping_mysql
    environment:
      MYSQL_DATABASE: leaping_portfolio_dev
      MYSQL_USER: dev_user
      MYSQL_PASSWORD: dev_password
      MYSQL_ROOT_PASSWORD: root_password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    networks:
      - leaping-network

  backend:
    build: ./backend/portfolio-app
    container_name: leaping_backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/leaping_portfolio_dev
      SPRING_DATASOURCE_USERNAME: dev_user
      SPRING_DATASOURCE_PASSWORD: dev_password
      JWT_SECRET: dev-secret-key-change-in-production
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    networks:
      - leaping-network

  frontend:
    build: ./frontend
    container_name: leaping_frontend
    ports:
      - "4200:80"
    depends_on:
      - backend
    networks:
      - leaping-network

volumes:
  mysql_data:

networks:
  leaping-network:
    driver: bridge
```
