# Portfolio App - Authentication Backend

## Overview
This is the JWT-based authentication implementation for the Portfolio App backend.

## Features Implemented
- ✅ User authentication with email/password
- ✅ JWT token generation and validation
- ✅ Invalid credentials error handling
- ✅ Session token management
- ✅ Login/Logout endpoints

## API Endpoints

### 1. Login
**POST** `/api/auth/login`

Request:
```json
{
  "email": "alice@example.com",
  "password": "password123"
}
```

Success Response (200):
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "email": "alice@example.com",
  "message": "Authentication successful",
  "success": true
}
```

Error Response (401):
```json
{
  "token": null,
  "email": null,
  "message": "Invalid email or password",
  "success": false
}
```

### 2. Validate Token
**GET** `/api/auth/validate`

Headers:
```
Authorization: Bearer <your_jwt_token>
```

Response (200):
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "email": "alice@example.com",
  "message": "Token is valid",
  "success": true
}
```

### 3. Logout
**POST** `/api/auth/logout`

Response (200):
```json
{
  "token": null,
  "email": null,
  "message": "Logout successful",
  "success": true
}
```

### 4. Health Check
**GET** `/api/auth/health`

Response (200):
```
Auth service is running
```

## Test Credentials

The following test users are pre-loaded in the in-memory store:

| Email | Password | Name |
|-------|----------|------|
| alice@example.com | password123 | Alice Smith |
| bob@example.com | securepass456 | Bob Johnson |
| charlie@example.com | mypassword789 | Charlie Brown |

## Configuration

JWT settings can be customized in `application.yaml`:

```yaml
jwt:
  secret: mySecretKeyForJWTTokenGenerationAndValidationPurpose123
  expiration: 86400000  # 24 hours in milliseconds
```

## Testing with cURL

```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"password123"}'

# Validate token
curl -X GET http://localhost:8080/api/auth/validate \
  -H "Authorization: Bearer <your_token_here>"

# Logout
curl -X POST http://localhost:8080/api/auth/logout
```

## Acceptance Criteria Status

- ✅ User can authenticate using valid credentials
- ✅ Invalid credentials display an error message
- ✅ Session or JWT token is generated (JWT implemented)
- ✅ User remains authenticated until logout or session expiration (24-hour token expiration)

## Future Work

- [ ] Integrate with PostgreSQL database
- [ ] Add user registration endpoint
- [ ] Implement refresh token mechanism
- [ ] Add role-based access control (RBAC)
- [ ] Secure password reset functionality
- [ ] Add audit logging
- [ ] Two-factor authentication (2FA)
