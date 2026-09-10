# Leaping Portfolio - Frontend Setup Guide

## Overview
This is an Angular-based frontend application for the Leaping Portfolio trading platform, featuring JWT authentication, secure login/registration, and a protected dashboard.

## Features Implemented

### ✅ Acceptance Criteria Met
- ✅ User can authenticate using valid credentials
- ✅ Invalid credentials display an error message
- ✅ Successful authentication redirects user to dashboard
- ✅ Session or JWT token is generated
- ✅ User remains authenticated until logout or session expiration

### Architecture Components

#### 1. **Authentication Service** (`src/app/services/auth.service.ts`)
- Handles login, registration, and token refresh
- Manages JWT token storage in localStorage
- Provides Observable streams for current user and authentication status
- Automatically checks token expiration periodically
- Handles token refresh on API 401 responses

#### 2. **JWT Interceptor** (`src/app/interceptors/jwt.interceptor.ts`)
- Automatically adds JWT token to all API requests
- Handles 401 Unauthorized responses with token refresh
- Handles 403 Forbidden responses with logout
- Excludes login and register endpoints from token injection

#### 3. **Route Guards** 
- **AuthGuard** (`src/app/guards/auth.guard.ts`): Protects dashboard route, redirects unauthenticated users to login
- **NoAuthGuard** (`src/app/guards/no-auth.guard.ts`): Prevents authenticated users from accessing login/register pages

#### 4. **Components**
- **Login Component**: User authentication with email/password, error handling, password visibility toggle
- **Register Component**: New user registration with validation, password confirmation
- **Dashboard Component**: Protected route showing user profile and portfolio overview

## Installation & Setup

### Prerequisites
- Node.js (v18 or higher)
- npm or yarn package manager
- Angular CLI 17.x

### Steps

1. **Navigate to frontend directory**
   ```bash
   cd frontend
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Configure Backend API URL**
   - Open `src/app/services/auth.service.ts`
   - Update the `API_URL` constant:
   ```typescript
   private readonly API_URL = 'http://localhost:8080/api'; // Change to your backend URL
   ```

4. **Start the development server**
   ```bash
   npm start
   ```
   The application will be available at `http://localhost:4200`

## Backend API Integration

The frontend expects the following backend endpoints:

### Authentication Endpoints

#### 1. Register User
- **Endpoint**: `POST /api/auth/register`
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "securePassword123",
    "username": "username"
  }
  ```
- **Success Response (200)**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refreshTokenString",
    "expiresIn": 3600
  }
  ```
- **Error Response (400/409)**:
  ```json
  {
    "message": "Email already exists" or "Registration failed"
  }
  ```

#### 2. Login
- **Endpoint**: `POST /api/auth/login`
- **Request Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "securePassword123"
  }
  ```
- **Success Response (200)**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refreshTokenString",
    "expiresIn": 3600
  }
  ```
- **Error Response (401)**:
  ```json
  {
    "message": "Invalid email or password"
  }
  ```

#### 3. Refresh Token
- **Endpoint**: `POST /api/auth/refresh`
- **Request Body**:
  ```json
  {
    "refreshToken": "refreshTokenString"
  }
  ```
- **Success Response (200)**:
  ```json
  {
    "token": "newJwtToken",
    "refreshToken": "newRefreshToken",
    "expiresIn": 3600
  }
  ```

#### 4. Get Current User
- **Endpoint**: `GET /api/auth/me`
- **Headers**: `Authorization: Bearer {token}`
- **Success Response (200)**:
  ```json
  {
    "id": "userId",
    "email": "user@example.com",
    "username": "username"
  }
  ```
- **Error Response (401)**:
  ```json
  {
    "message": "Unauthorized"
  }
  ```

## JWT Token Structure

The JWT token should include the following claims:
```json
{
  "sub": "userId",
  "email": "user@example.com",
  "username": "username",
  "iat": 1234567890,
  "exp": 1234571490
}
```

## Security Implementation

### Token Storage
- JWT tokens are stored in browser's localStorage
- Refresh tokens are stored separately for token renewal
- Tokens are automatically cleared on logout or expiration

### Token Refresh Flow
1. On API call, if token is expired, intercept the 401 response
2. Attempt to refresh the token using the refresh token
3. If refresh succeeds, retry the original request with new token
4. If refresh fails, logout the user and redirect to login

### Session Expiration
- Token expiration is checked every minute
- If token is expired, user is automatically logged out
- On next API call, if token is invalid, automatic refresh is attempted

## Build & Deployment

### Development Build
```bash
npm run build
```

### Production Build
```bash
npm run build -- --configuration production
```

Output will be in `dist/leaping-portfolio/`

## Testing

Run unit tests:
```bash
npm test
```

## File Structure
```
frontend/
├── src/
│   ├── app/
│   │   ├── components/
│   │   │   ├── login/
│   │   │   ├── register/
│   │   │   └── dashboard/
│   │   ├── services/
│   │   │   └── auth.service.ts
│   │   ├── interceptors/
│   │   │   └── jwt.interceptor.ts
│   │   ├── guards/
│   │   │   ├── auth.guard.ts
│   │   │   └── no-auth.guard.ts
│   │   ├── app.component.ts
│   │   ├── app.routes.ts
│   │   └── app.component.scss
│   ├── index.html
│   ├── main.ts
│   └── styles.scss
├── angular.json
├── tsconfig.json
├── tsconfig.app.json
├── tsconfig.spec.json
└── package.json
```

## Troubleshooting

### Issue: Blank dashboard page
**Solution**: Ensure the backend API is running and `/api/auth/me` endpoint is returning user data.

### Issue: 401 Unauthorized errors
**Solution**: Check that:
1. Token is being stored correctly in localStorage
2. Backend is validating JWT tokens properly
3. Token hasn't expired

### Issue: CORS errors
**Solution**: Configure CORS on backend to allow requests from frontend URL:
```java
// Spring Boot example
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:4200")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*")
                    .allowCredentials(true);
            }
        };
    }
}
```

## Next Steps

1. **Implement Backend API**: Create Spring Boot endpoints for authentication
2. **Add Dashboard Features**: Implement portfolio tracking and market data
3. **Error Handling**: Add comprehensive error handling and user feedback
4. **Testing**: Add unit and integration tests
5. **Security**: Implement additional security measures (HTTPS, HttpOnly cookies, CSRF protection)

## Support

For issues or questions about the frontend setup, refer to the [Angular Documentation](https://angular.io) or [Angular Material](https://material.angular.io).
