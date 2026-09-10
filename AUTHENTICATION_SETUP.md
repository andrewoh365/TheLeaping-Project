# Leaping Portfolio - Authentication Integration Guide

Complete end-to-end setup guide for implementing JWT authentication across the full stack.

## 🎯 Overview

This guide walks through setting up and connecting the Angular frontend with the Spring Boot backend for JWT-based authentication.

### Acceptance Criteria Implementation
- ✅ **User can authenticate using valid credentials** - Login endpoint validates email/password
- ✅ **Invalid credentials display an error message** - Error messages shown in UI
- ✅ **Successful authentication redirects user to dashboard** - Router redirects after login
- ✅ **Session or JWT token is generated** - Token generated on login/register
- ✅ **User remains authenticated until logout or session expiration** - Token stored in localStorage, auto-refresh on 401

## 📋 Prerequisites

### Backend Requirements
- Java 21
- Maven 3.6+
- MySQL 8.0+ (or any SQL database)
- Spring Boot 4.1.1

### Frontend Requirements
- Node.js v18+
- npm or yarn
- Angular CLI 17.x

## 🚀 Quick Start

### Part 1: Backend Setup (30 minutes)

#### Step 1: Add Maven Dependencies

Edit `backend/portfolio-app/pom.xml` and add:

```xml
<dependencies>
    <!-- Existing dependencies -->
    
    <!-- JWT Support -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- MySQL Driver -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
        <version>8.0.33</version>
    </dependency>
</dependencies>
```

#### Step 2: Configure Database

Update `backend/portfolio-app/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: portfolio-app
  
  datasource:
    url: jdbc:mysql://localhost:3306/leaping_portfolio
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
  
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: your-super-secret-key-must-be-at-least-256-bits-long-for-hs512-very-long-secret-key-here
  expiration: 3600000      # 1 hour
  refresh-expiration: 604800000  # 7 days

server:
  port: 8080
  servlet:
    context-path: /
```

#### Step 3: Create Database Schema

Create `backend/portfolio-app/src/main/resources/db/migration/V1__Create_users_table.sql`:

```sql
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at BIGINT,
    updated_at BIGINT,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    KEY idx_email (email),
    KEY idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### Step 4: Implement Backend Code

Copy the implementation from `BACKEND_AUTH_GUIDE.md` into your project:

1. Create `src/main/java/com/leaping/portfolio_app/entity/User.java`
2. Create `src/main/java/com/leaping/portfolio_app/repository/UserRepository.java`
3. Create `src/main/java/com/leaping/portfolio_app/security/JwtProvider.java`
4. Create `src/main/java/com/leaping/portfolio_app/dto/` classes (request/response DTOs)
5. Create `src/main/java/com/leaping/portfolio_app/service/AuthService.java`
6. Create `src/main/java/com/leaping/portfolio_app/controller/AuthController.java`
7. Create `src/main/java/com/leaping/portfolio_app/config/SecurityConfig.java`

#### Step 5: Build and Run Backend

```bash
cd backend/portfolio-app
mvn clean install
mvn spring-boot:run
```

Verify API is running:
```bash
curl http://localhost:8080/api/auth/me
# Should return 401 Unauthorized (expected without token)
```

---

### Part 2: Frontend Setup (20 minutes)

#### Step 1: Install Dependencies

```bash
cd frontend
npm install
```

#### Step 2: Update API Configuration

Edit `src/app/services/auth.service.ts` and ensure the API URL matches your backend:

```typescript
private readonly API_URL = 'http://localhost:8080/api';
```

#### Step 3: Run Development Server

```bash
npm start
```

Application will be available at `http://localhost:4200`

---

## 🧪 Testing the Complete Flow

### Test 1: Registration

1. Open `http://localhost:4200` (redirects to dashboard, but you're not authenticated)
2. You'll be redirected to login page
3. Click "Register here"
4. Fill in registration form:
   - Username: `testuser`
   - Email: `test@example.com`
   - Password: `password123`
   - Confirm Password: `password123`
5. Click "Register"

**Expected Result**: 
- ✅ User created in database
- ✅ JWT token received and stored
- ✅ Redirected to dashboard
- ✅ User email displayed in dashboard

### Test 2: Login

1. Go to `http://localhost:4200/login`
2. Enter credentials:
   - Email: `test@example.com`
   - Password: `password123`
3. Click "Login"

**Expected Result**:
- ✅ JWT token received and stored in localStorage
- ✅ Redirected to dashboard
- ✅ Dashboard displays current user email

### Test 3: Invalid Credentials

1. Go to `http://localhost:4200/login`
2. Enter:
   - Email: `test@example.com`
   - Password: `wrongpassword`
3. Click "Login"

**Expected Result**:
- ✅ Error message displayed: "Invalid email or password"
- ✅ NOT redirected to dashboard
- ✅ Remains on login page

### Test 4: Session Persistence

1. Login successfully
2. Close the browser tab or refresh the page
3. Open `http://localhost:4200` in a new tab

**Expected Result**:
- ✅ User remains logged in (token is in localStorage)
- ✅ Dashboard is displayed immediately
- ✅ User information is loaded

### Test 5: Token Expiration Handling

1. Login successfully
2. Wait for token to approach expiration
3. Make a request or wait for periodic check

**Expected Result**:
- ✅ Token is automatically refreshed using refresh token
- ✅ User is not logged out
- ✅ Session continues seamlessly

### Test 6: Logout

1. On dashboard, click on user icon (top right)
2. Click "Logout"

**Expected Result**:
- ✅ Tokens are cleared from localStorage
- ✅ User is redirected to login page
- ✅ Cannot access dashboard without logging in again

### Test 7: Protected Routes

1. Logout
2. Try to access `http://localhost:4200/dashboard` directly

**Expected Result**:
- ✅ Redirected to login page
- ✅ Return URL is saved as query parameter
- ✅ After login, redirected back to originally requested page

---

## 🔍 Debugging Guide

### Check Browser Storage
1. Open DevTools (F12)
2. Go to Application > Local Storage
3. Look for keys: `jwt_token`, `refresh_token`, `user`

### Check Network Requests
1. Open DevTools (F12)
2. Go to Network tab
3. Make login request
4. Verify:
   - Request includes login credentials
   - Response includes `token` and `refreshToken`
   - Authorization header is added to subsequent requests

### Check Backend Logs
```bash
# If running with logs
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}' \
  -v
```

---

## 🔐 Security Checklist

- [ ] Database is running and accessible
- [ ] JWT secret is long and secure (at least 256 bits)
- [ ] CORS is configured correctly for development/production
- [ ] Tokens are stored securely (localStorage for now, consider HttpOnly cookies for production)
- [ ] HTTPS is enabled in production
- [ ] Password encoding uses bcrypt
- [ ] Rate limiting is implemented on auth endpoints
- [ ] CSRF protection is enabled
- [ ] Token expiration times are appropriate

---

## 📁 Project Structure After Implementation

```
TheLeaping-Project/
├── backend/
│   └── portfolio-app/
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/
│       │   │   │   └── com/leaping/portfolio_app/
│       │   │   │       ├── controller/
│       │   │   │       │   └── AuthController.java
│       │   │   │       ├── service/
│       │   │   │       │   └── AuthService.java
│       │   │   │       ├── entity/
│       │   │   │       │   └── User.java
│       │   │   │       ├── repository/
│       │   │   │       │   └── UserRepository.java
│       │   │   │       ├── security/
│       │   │   │       │   └── JwtProvider.java
│       │   │   │       ├── dto/
│       │   │   │       │   ├── LoginRequest.java
│       │   │   │       │   ├── RegisterRequest.java
│       │   │   │       │   ├── AuthResponse.java
│       │   │   │       │   ├── UserDto.java
│       │   │   │       │   └── RefreshTokenRequest.java
│       │   │   │       ├── config/
│       │   │   │       │   └── SecurityConfig.java
│       │   │   │       └── PortfolioAppApplication.java
│       │   │   └── resources/
│       │   │       ├── application.yaml
│       │   │       └── db/migration/
│       │   │           └── V1__Create_users_table.sql
│       │   └── test/
│       ├── pom.xml
│       └── BACKEND_AUTH_GUIDE.md
│
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/
│   │   │   │   ├── login/
│   │   │   │   │   ├── login.component.ts
│   │   │   │   │   ├── login.component.html
│   │   │   │   │   └── login.component.scss
│   │   │   │   ├── register/
│   │   │   │   │   ├── register.component.ts
│   │   │   │   │   ├── register.component.html
│   │   │   │   │   └── register.component.scss
│   │   │   │   └── dashboard/
│   │   │   │       ├── dashboard.component.ts
│   │   │   │       ├── dashboard.component.html
│   │   │   │       └── dashboard.component.scss
│   │   │   ├── services/
│   │   │   │   └── auth.service.ts
│   │   │   ├── interceptors/
│   │   │   │   └── jwt.interceptor.ts
│   │   │   ├── guards/
│   │   │   │   ├── auth.guard.ts
│   │   │   │   └── no-auth.guard.ts
│   │   │   ├── app.routes.ts
│   │   │   ├── app.component.ts
│   │   │   └── app.component.scss
│   │   ├── index.html
│   │   ├── main.ts
│   │   └── styles.scss
│   ├── package.json
│   ├── angular.json
│   ├── tsconfig.json
│   └── FRONTEND_SETUP.md
│
├── database/
├── docker/
├── infrastructure/
├── README.md
└── Jenkinsfile
```

---

## 🐛 Common Issues & Solutions

### Issue: CORS Error
**Error**: `Access to XMLHttpRequest at 'http://localhost:8080/api/auth/login' blocked by CORS policy`

**Solution**: Ensure SecurityConfig has CORS configuration:
```java
registry.addMapping("/api/**")
    .allowedOrigins("http://localhost:4200")
    .allowedMethods("GET", "POST", "PUT", "DELETE")
    .allowedHeaders("*")
```

### Issue: 404 on `/api/auth/login`
**Error**: `404 Not Found` when calling login endpoint

**Solution**: Verify:
1. Backend is running on port 8080
2. AuthController is in correct package structure
3. API path in frontend matches backend controller path

### Issue: Token Not Being Added to Requests
**Error**: Backend returns 401 on protected endpoints

**Solution**: Check:
1. JwtInterceptor is registered in `main.ts`
2. Token is stored in localStorage with key `jwt_token`
3. Token is not expired

### Issue: "User remains authenticated" not working
**Error**: User is logged out after page refresh

**Solution**:
1. Check that localStorage is enabled in browser
2. Verify token is being saved: `localStorage.getItem('jwt_token')`
3. Check token expiration time

---

## 📚 Next Steps

1. **Add password reset functionality**
   - `/api/auth/forgot-password` endpoint
   - Email verification flow

2. **Implement role-based access control (RBAC)**
   - Add roles to User entity
   - Implement role-based route guards

3. **Add user profile management**
   - Update profile endpoint
   - Change password functionality

4. **Implement OAuth2 / Social Login**
   - Google OAuth integration
   - GitHub OAuth integration

5. **Add two-factor authentication (2FA)**
   - Email-based 2FA
   - Authenticator app support

6. **Production Deployment**
   - Set up HTTPS certificates
   - Configure production database
   - Set up environment variables
   - Deploy to cloud platform (AWS, Azure, GCP)

---

## 📖 Documentation References

- [Angular Documentation](https://angular.io/docs)
- [Angular Material](https://material.angular.io)
- [Spring Security](https://spring.io/projects/spring-security)
- [Spring Boot](https://spring.io/projects/spring-boot)
- [JWT.io](https://jwt.io)
- [Flyway Database Migrations](https://flywaydb.org)

---

## 💡 Tips for Development

1. **Use separate `.env` files** for API URLs based on environment (dev, staging, prod)
2. **Implement request/response logging** for debugging API calls
3. **Add unit tests** for AuthService and AuthGuard
4. **Implement error handling** for network timeouts and failures
5. **Add loading spinners** during authentication requests
6. **Keep sensitive data** out of localStorage (consider HttpOnly cookies)
7. **Implement auto-logout** after session expiration with user notification
8. **Add refresh token rotation** for enhanced security

---

## 🤝 Support & Questions

Refer to the individual component guides:
- **Frontend Issues**: See `frontend/FRONTEND_SETUP.md`
- **Backend Issues**: See `backend/portfolio-app/BACKEND_AUTH_GUIDE.md`
- **Integration Issues**: See this guide

For framework-specific issues:
- Angular: [Stack Overflow](https://stackoverflow.com/questions/tagged/angular) or [Angular Discord](https://discord.gg/angular)
- Spring Boot: [Stack Overflow](https://stackoverflow.com/questions/tagged/spring-boot) or [Spring Community](https://spring.io/community)
