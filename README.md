# Leaping Portfolio

A full-stack online trading platform application for portfolio management and market analysis.

## 🎯 Features

### ✅ Authentication & Authorization (Completed)
- **JWT-based Authentication**: Secure token generation and validation
- **User Registration**: New user account creation with validation
- **User Login**: Email/password authentication with error handling
- **Session Management**: Automatic token refresh and expiration handling
- **Protected Routes**: Dashboard and authenticated endpoints secured with route guards
- **Material UI Design**: Professional, responsive UI components

### 📱 Frontend (Angular)
- Modern single-page application built with Angular 17
- Material-UI components for consistent design
- Form validation and error handling
- Reactive forms with real-time validation
- JWT token interceptor for automatic authentication header injection
- Route guards for protected pages

### 🔧 Backend (Spring Boot)
- RESTful API with JWT token support
- User authentication endpoints (login, register, refresh, me)
- Spring Security integration
- Database schema with Flyway migrations
- CORS configuration for development and production

## 🚀 Quick Start

### Prerequisites
- **Backend**: Java 21, Maven 3.6+, MySQL 8.0+
- **Frontend**: Node.js v18+, npm or yarn

### Backend Setup
```bash
cd backend/portfolio-app
mvn clean install
mvn spring-boot:run
# Backend runs on http://localhost:8080
```

### Frontend Setup
```bash
cd frontend
npm install
npm start
# Frontend runs on http://localhost:4200
```

### Access Application
- **Registration**: http://localhost:4200/register
- **Login**: http://localhost:4200/login
- **Dashboard**: http://localhost:4200/dashboard

## 📁 Project Structure

```
The Leaping Project/
├── backend/
│   └── portfolio-app/       # Spring Boot application
│       ├── src/
│       │   ├── main/java/com/leaping/portfolio_app/
│       │   │   ├── controller/    # REST API endpoints
│       │   │   ├── service/       # Business logic
│       │   │   ├── entity/        # Database entities
│       │   │   ├── repository/    # Data access layer
│       │   │   ├── security/      # JWT token handling
│       │   │   ├── dto/           # Request/response DTOs
│       │   │   └── config/        # Spring configuration
│       │   └── resources/
│       │       ├── application.yaml
│       │       └── db/migration/  # Flyway migrations
│       ├── pom.xml
│       └── BACKEND_AUTH_GUIDE.md
├── frontend/                # Angular application
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/    # Login, Register, Dashboard
│   │   │   ├── services/      # Authentication service
│   │   │   ├── interceptors/  # JWT interceptor
│   │   │   ├── guards/        # Route guards
│   │   │   ├── app.routes.ts  # Routing config
│   │   │   └── app.component.ts
│   │   ├── index.html
│   │   ├── main.ts
│   │   └── styles.scss
│   ├── package.json
│   ├── angular.json
│   ├── tsconfig.json
│   └── FRONTEND_SETUP.md
├── database/                # Database configuration
├── docker/                  # Docker configuration
├── infrastructure/          # Infrastructure setup
├── AUTHENTICATION_SETUP.md  # Complete integration guide
├── IMPLEMENTATION_SUMMARY.md# Feature implementation summary
├── ENVIRONMENT_CONFIG.md    # Environment and Docker config
├── Jenkinsfile             # CI/CD pipeline definition
└── README.md               # This file
```

## 📚 Documentation

- **[Authentication Setup Guide](./AUTHENTICATION_SETUP.md)** - Complete end-to-end setup and integration guide
- **[Implementation Summary](./IMPLEMENTATION_SUMMARY.md)** - Overview of completed features
- **[Frontend Setup Guide](./frontend/FRONTEND_SETUP.md)** - Angular frontend configuration and setup
- **[Backend Authentication Guide](./backend/portfolio-app/BACKEND_AUTH_GUIDE.md)** - Spring Boot backend implementation
- **[Environment Configuration](./ENVIRONMENT_CONFIG.md)** - Configuration for different environments

## 🔐 Security Features

- ✅ Password encryption with bcrypt
- ✅ JWT token-based authentication
- ✅ Automatic token refresh mechanism
- ✅ Route guards for protected pages
- ✅ CORS configuration
- ✅ HTTP interceptor for token injection
- ✅ Session expiration handling

## 🧪 Acceptance Criteria (All Met ✅)

- ✅ User can authenticate using valid credentials
- ✅ Invalid credentials display an error message
- ✅ Successful authentication redirects user to dashboard
- ✅ Session or JWT token is generated
- ✅ User remains authenticated until logout or session expiration

## 🔄 Branching Strategy

### Trunk-Based Development

Trunk-based development works well for a short 12-week project because everyone integrates into one main branch frequently, which reduces merge conflicts and keeps progress visible in real time. It also helps to deliver working features continuously instead of spending time managing long-lived branches.

## 👥 Team

- Sarah Berhe
- Satvik Movva
- Maanya Naveen
- Andrew Oh
- Alveena Rehman
- Miguel Trejo Cruz

## 📖 Technologies Used

### Frontend
- Angular 17
- Angular Material
- RxJS
- TypeScript
- SCSS

### Backend
- Spring Boot 4.1.1
- Spring Security
- Spring Data JPA
- JWT (JSON Web Tokens)
- MySQL

## 🚢 Deployment

See [Environment Configuration](./ENVIRONMENT_CONFIG.md) for Docker and production deployment setup.

## 📝 Development Workflow

1. Create a feature branch from `main`
2. Implement the feature
3. Create a pull request with detailed description
4. Code review and approval
5. Merge to `main` using rebase strategy
6. Automatically deploy via Jenkins pipeline

## 🤝 Contributing

Please refer to the team's development guidelines and ensure all code follows the project's coding standards.

## 📞 Support

For setup issues or questions:
- **Frontend**: See `frontend/FRONTEND_SETUP.md`
- **Backend**: See `backend/portfolio-app/BACKEND_AUTH_GUIDE.md`
- **Integration**: See `AUTHENTICATION_SETUP.md`

