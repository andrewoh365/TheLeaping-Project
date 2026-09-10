# Getting Started Checklist

Quick checklist to get the login/registration feature up and running.

## ✅ Pre-requisites Checklist

### System Requirements
- [ ] Java 21 installed
- [ ] Maven 3.6+ installed
- [ ] Node.js v18+ installed
- [ ] npm installed (comes with Node.js)
- [ ] MySQL 8.0+ installed and running
- [ ] Git installed

### Verify Installation
```bash
java -version        # Should show Java 21.x
mvn -version        # Should show Maven 3.6+
node -v             # Should show v18+
npm -v              # Should show 9.x+
mysql --version     # Should show 8.0+
```

---

## 🔧 Backend Setup (30 minutes)

### Step 1: Database Preparation
- [ ] Create MySQL database:
  ```bash
  mysql -u root -p
  CREATE DATABASE leaping_portfolio;
  CREATE USER 'dev_user'@'localhost' IDENTIFIED BY 'dev_password';
  GRANT ALL PRIVILEGES ON leaping_portfolio.* TO 'dev_user'@'localhost';
  FLUSH PRIVILEGES;
  EXIT;
  ```

### Step 2: Backend Dependencies
- [ ] Navigate to backend folder:
  ```bash
  cd backend/portfolio-app
  ```
- [ ] Add JWT dependencies to `pom.xml` (see BACKEND_AUTH_GUIDE.md)
- [ ] Add Lombok dependency to `pom.xml`
- [ ] Add MySQL driver dependency to `pom.xml`

### Step 3: Configuration
- [ ] Update `src/main/resources/application.yaml`:
  - [ ] Set correct database URL
  - [ ] Set database username/password
  - [ ] Set JWT secret (long, random string)
  - [ ] Verify port is 8080

### Step 4: Create Backend Files
- [ ] Create User entity
- [ ] Create UserRepository
- [ ] Create JwtProvider
- [ ] Create DTOs (LoginRequest, RegisterRequest, AuthResponse, UserDto, RefreshTokenRequest)
- [ ] Create AuthService
- [ ] Create AuthController
- [ ] Create SecurityConfig
- [ ] Create Flyway migration V1__Create_users_table.sql

### Step 5: Build and Run
- [ ] Build backend:
  ```bash
  mvn clean install
  ```
- [ ] Run backend:
  ```bash
  mvn spring-boot:run
  ```
- [ ] Verify it's running:
  ```bash
  curl http://localhost:8080/api/auth/me
  # Should return 401 Unauthorized (expected)
  ```

---

## 🎨 Frontend Setup (20 minutes)

### Step 1: Frontend Installation
- [ ] Navigate to frontend folder:
  ```bash
  cd frontend
  ```
- [ ] Install dependencies:
  ```bash
  npm install
  ```

### Step 2: Verify Files
- [ ] Check all component files exist:
  - [ ] `src/app/components/login/`
  - [ ] `src/app/components/register/`
  - [ ] `src/app/components/dashboard/`
- [ ] Check service files:
  - [ ] `src/app/services/auth.service.ts`
- [ ] Check interceptor:
  - [ ] `src/app/interceptors/jwt.interceptor.ts`
- [ ] Check guards:
  - [ ] `src/app/guards/auth.guard.ts`
  - [ ] `src/app/guards/no-auth.guard.ts`

### Step 3: Configuration
- [ ] Update API URL in `src/app/services/auth.service.ts`:
  ```typescript
  private readonly API_URL = 'http://localhost:8080/api';
  ```

### Step 4: Run Frontend
- [ ] Start development server:
  ```bash
  npm start
  ```
- [ ] Verify frontend loads:
  ```
  Open http://localhost:4200 in browser
  Should redirect to http://localhost:4200/login
  ```

---

## 🧪 Testing the Flow (15 minutes)

### Test 1: Registration
- [ ] Open http://localhost:4200/register
- [ ] Fill in form:
  - Username: `testuser`
  - Email: `test@example.com`
  - Password: `Password123!`
  - Confirm: `Password123!`
- [ ] Click Register
- [ ] Verify:
  - [ ] No errors displayed
  - [ ] Redirected to dashboard
  - [ ] User email shown on dashboard

### Test 2: Logout
- [ ] Click user menu (top right)
- [ ] Click Logout
- [ ] Verify:
  - [ ] Redirected to login page
  - [ ] Tokens cleared from localStorage

### Test 3: Login
- [ ] On login page
- [ ] Enter credentials:
  - Email: `test@example.com`
  - Password: `Password123!`
- [ ] Click Login
- [ ] Verify:
  - [ ] No errors
  - [ ] Redirected to dashboard
  - [ ] User email shown

### Test 4: Invalid Credentials
- [ ] On login page
- [ ] Enter:
  - Email: `test@example.com`
  - Password: `wrongpassword`
- [ ] Click Login
- [ ] Verify:
  - [ ] Error message displayed
  - [ ] NOT redirected

### Test 5: Protected Route
- [ ] Logout (if logged in)
- [ ] Try to access http://localhost:4200/dashboard
- [ ] Verify:
  - [ ] Redirected to login page
  - [ ] Return URL is saved as query param

### Test 6: Session Persistence
- [ ] Login successfully
- [ ] Refresh the page (F5)
- [ ] Verify:
  - [ ] Still logged in
  - [ ] Dashboard loads immediately

---

## 📊 Verification Checklist

### Frontend
- [ ] Login page displays correctly
- [ ] Register page displays correctly
- [ ] Form validation works
- [ ] Error messages show for invalid input
- [ ] Success redirects to dashboard
- [ ] Dashboard shows user info
- [ ] Logout works correctly

### Backend
- [ ] POST /api/auth/register works
- [ ] POST /api/auth/login works
- [ ] POST /api/auth/refresh works
- [ ] GET /api/auth/me works (with token)
- [ ] Database stores users correctly
- [ ] Passwords are bcrypt hashed
- [ ] JWTs are valid and contain correct claims

### Integration
- [ ] Frontend can reach backend
- [ ] Tokens are added to requests automatically
- [ ] Invalid tokens return 401
- [ ] 401 errors trigger token refresh
- [ ] Token refresh works correctly

---

## 🐛 Troubleshooting

### Issue: Cannot connect to backend
**Solution**:
- [ ] Verify backend is running: `http://localhost:8080`
- [ ] Check firewall isn't blocking port 8080
- [ ] Verify CORS is configured in SecurityConfig

### Issue: CORS error in browser
**Solution**:
- [ ] Check SecurityConfig has CORS mapping for `/api/**`
- [ ] Verify allowed origins includes `http://localhost:4200`

### Issue: 404 on /api/auth/login
**Solution**:
- [ ] Verify AuthController is in correct package
- [ ] Rebuild backend: `mvn clean install`
- [ ] Restart backend

### Issue: Blank login page
**Solution**:
- [ ] Check browser console for errors (F12)
- [ ] Verify frontend built correctly
- [ ] Clear browser cache

### Issue: Login but can't access dashboard
**Solution**:
- [ ] Check token is in localStorage: `localStorage.getItem('jwt_token')`
- [ ] Verify token is not expired
- [ ] Check AuthGuard is protecting the route

### Issue: Database connection error
**Solution**:
- [ ] Verify MySQL is running
- [ ] Check database credentials in application.yaml
- [ ] Verify database exists: `mysql -u root -p`
  ```bash
  SHOW DATABASES;
  USE leaping_portfolio;
  SHOW TABLES;
  ```

---

## 📚 Documentation Reference

| Document | Purpose |
|----------|---------|
| [AUTHENTICATION_SETUP.md](./AUTHENTICATION_SETUP.md) | Complete integration guide with all steps |
| [FRONTEND_SETUP.md](./frontend/FRONTEND_SETUP.md) | Frontend-specific setup and API spec |
| [BACKEND_AUTH_GUIDE.md](./backend/portfolio-app/BACKEND_AUTH_GUIDE.md) | Backend implementation details |
| [ENVIRONMENT_CONFIG.md](./ENVIRONMENT_CONFIG.md) | Configuration for different environments |
| [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) | Overview of what was implemented |

---

## ✅ Acceptance Criteria Verification

Verify all acceptance criteria are met:

- [ ] **User can authenticate using valid credentials**
  - Test: Login with valid email/password → Should succeed

- [ ] **Invalid credentials display an error message**
  - Test: Login with invalid password → Error message shown

- [ ] **Successful authentication redirects user to dashboard**
  - Test: Login succeeds → Redirected to /dashboard

- [ ] **Session or JWT token is generated**
  - Test: Check localStorage after login → `jwt_token` exists

- [ ] **User remains authenticated until logout or session expiration**
  - Test: Login → Refresh page → Still authenticated
  - Test: Logout → Should redirect to login

---

## 🎓 Next Steps After Verification

1. [ ] Code review with team
2. [ ] Fix any bugs found
3. [ ] Add unit tests
4. [ ] Add integration tests
5. [ ] Document API endpoints in Postman/Swagger
6. [ ] Prepare for production deployment
7. [ ] Set up CI/CD pipeline
8. [ ] Deploy to staging environment

---

## 📞 Common Questions

**Q: Where is the JWT token stored?**
A: In browser's localStorage with key `jwt_token`

**Q: How long does the token last?**
A: Access token: 1 hour, Refresh token: 7 days (configurable in application.yaml)

**Q: What if the token expires?**
A: Automatically refreshed using refresh token on next API call

**Q: Can I use this with mobile apps?**
A: Yes, the backend API is fully RESTful and can be used by any frontend

**Q: Is this production-ready?**
A: It's a solid foundation. For production, add HTTPS, rate limiting, and stronger security configs

---

## 📋 Team Sign-off

- [ ] Backend developer: Verified backend is working
- [ ] Frontend developer: Verified frontend is working
- [ ] QA: Verified all tests pass
- [ ] Team lead: Approved for deployment

**Date Completed**: _______________
**Notes**: _______________

---

**Status**: Ready for Testing ✅
