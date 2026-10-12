# Portfolio App - Backend Architecture Overview

## 1. Application Entry Point & Spring Boot Setup

### Main Application Class
**File:** [PortfolioAppApplication.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/PortfolioAppApplication.java)

```java
@SpringBootApplication
public class PortfolioAppApplication {
    public static void main(String[] args) {
        System.out.println("Portfolio App is running...");
        SpringApplication.run(PortfolioAppApplication.class, args);
    }
}
```

**What it does:**
- Standard Spring Boot entry point using the `@SpringBootApplication` annotation
- Automatically enables component scanning, configuration, and auto-configuration
- Spring discovers all components in the `com.leaping.portfolio_app` package and its sub-packages
- Starts an embedded Tomcat server (default port 8080)

### Technology Stack
- **Framework:** Spring Boot 4.1.1 with Spring Security
- **Database:** PostgreSQL (production) / H2 (testing) with Spring Data JPA
- **Database Migrations:** Flyway for version control
- **Authentication:** JWT (JSON Web Tokens) using JJWT 0.12.5
- **Java Version:** 21 (latest LTS)
- **Build Tool:** Maven 3.x with Spring Boot Maven Plugin

---

## 2. Layered Architecture

The application follows a **3-tier layered architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│         PRESENTATION LAYER (Controllers)         │
│  - InstrumentController                          │
│  - AuthController                                │
│  - Other domain controllers (Portfolio, Trade)   │
└─────────────────────────────────────────────────┘
           ↓ (HTTP Requests/Responses)
┌─────────────────────────────────────────────────┐
│      BUSINESS LOGIC LAYER (Services)             │
│  - AuthService                                   │
│  - InstrumentService                             │
│  - PortfolioService                              │
│  - TradeService                                  │
└─────────────────────────────────────────────────┘
           ↓ (Method calls)
┌─────────────────────────────────────────────────┐
│    DATA ACCESS LAYER (Repositories)              │
│  - UserRepository                                │
│  - InstrumentRepository                          │
│  - PortfolioRepository                           │
│  - TradeOrderRepository                          │
└─────────────────────────────────────────────────┘
           ↓ (SQL queries)
┌─────────────────────────────────────────────────┐
│        DATABASE LAYER (PostgreSQL)               │
│  - users, customers, analysts                    │
│  - instruments, stocks, forex, crypto            │
│  - portfolios, holdings, cash_transactions      │
│  - trade_orders, trades, execution_attempts     │
└─────────────────────────────────────────────────┘
```

### Layer Responsibilities

**1. Presentation Layer (Controllers)**
- Handles HTTP requests and responses
- Located in: `controller/` and `auth/controller/`
- No business logic - only routing and validation
- Uses DTOs (Data Transfer Objects) for request/response mapping
- Example: [InstrumentController.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/controller/InstrumentController.java)

**2. Business Logic Layer (Services)**
- Implements core business rules and workflows
- Located in: `service/` and `auth/service/`
- Coordinates between controllers and repositories
- Handles transactions and complex operations
- Example: [InstrumentService.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/service/InstrumentService.java)

**3. Data Access Layer (Repositories)**
- Abstracts database operations using Spring Data JPA
- Located in: `repository/` and `auth/repository/`
- Extends `JpaRepository<Entity, ID>` for standard CRUD operations
- Can include custom query methods using `@Query` annotation
- Example: [InstrumentRepository.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/repository/InstrumentRepository.java)

**4. Entity/Domain Layer**
- POJO classes mapped to database tables using JPA annotations
- Located in: `**/entity/` folders in each domain
- Defines the data model and relationships
- Examples: User, Customer, Instrument, Portfolio, TradeOrder

---

## 3. Key Components & Their Relationships

### Directory Structure & Domain Organization

The application is organized by **business domain** rather than by technical layer:

```
src/main/java/com/leaping/portfolio_app/
├── auth/                          # Authentication & Authorization
│   ├── controller/                # AuthController - login, register, refresh
│   ├── model/                     # User, Customer, Analyst entities
│   ├── service/                   # AuthService - authentication logic
│   ├── repository/                # UserRepository, CustomerRepository
│   ├── security/                  # SecurityConfig, JwtAuthenticationFilter, CustomUserDetailsService
│   ├── dto/                       # LoginRequest, RegisterRequest, AuthResponse
│   └── util/                      # JwtTokenProvider
│
├── instrument/                    # Financial Instruments (Stocks, Forex, Crypto)
│   ├── entity/                    # Instrument (parent), Stock, Forex, Crypto
│   └── enums/                     # InstrumentType
│
├── portfolio/                     # Trading Portfolios
│   ├── entity/                    # Portfolio, Holding, CashTransaction
│   └── enums/
│
├── trade/                         # Trade Execution
│   ├── entity/                    # TradeOrder, Trade, ExecutionAttempt
│   └── enums/                     # OrderStatus, OrderAction, OrderType, TimeInForce
│
├── market/                        # Market & Currency Data
│   ├── entity/                    # Market, Currency, Price
│   └── enums/
│
├── watchlist/                     # User Watchlists
│   └── entity/
│
├── audit/                         # Audit Trail
│   └── entity/                    # AuditEvent - tracks system operations
│
├── controller/                    # Top-level controllers (cross-domain)
├── service/                       # Top-level services
├── repository/                    # Top-level repositories
└── PortfolioAppApplication.java   # Entry point
```

### Why Domain-Driven Organization?

✅ **Cohesion:** All related code (controller, service, repository, entities) for a feature is together
✅ **Scalability:** Easy to add new domains without touching existing code
✅ **Maintainability:** Clear ownership and boundaries
✅ **Testing:** Can test entire domain in isolation

---

## 4. Authentication & Authorization Flow (JWT Implementation)

### Request Flow Diagram

```
1. Client Login
   └─→ POST /api/auth/login {email, password}
       └─→ [AuthController.login()]
           └─→ AuthService.authenticate()
               ├─ Find user by email in database
               ├─ Verify password (BCrypt comparison)
               ├─ Generate JWT access token
               ├─ Generate refresh token
               └─ Return AuthResponse with tokens

2. Authenticated Request
   └─→ GET /api/instruments
       + Header: "Authorization: Bearer <JWT_TOKEN>"
       └─→ [JwtAuthenticationFilter]
           ├─ Extract token from Authorization header
           ├─ Validate token signature & expiration
           ├─ Extract email from token claims
           ├─ Load user details from database (including role)
           ├─ Create Spring SecurityContext with user & authorities
           └─→ [InstrumentController]
               └─→ [InstrumentService]
                   └─→ [InstrumentRepository]
                       └─→ Database query

3. Unauthorized Request (No token)
   └─→ GET /api/instruments
       (No Authorization header)
       └─→ [JwtAuthenticationFilter - skips auth]
           └─→ [SecurityFilterChain]
               └─→ 401 Unauthorized (endpoint requires authentication)
```

### Components in Auth Flow

**1. JwtTokenProvider** ([auth/util/JwtTokenProvider.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/auth/util/JwtTokenProvider.java))
- Generates JWT tokens with email as subject
- Access token: 1 hour expiration
- Refresh token: 7 days expiration, marked with `"type": "refresh"` claim
- Signs tokens using HS512 algorithm with a secret key (min 256 bits recommended)
- Validates token signature and expiration

**2. JwtAuthenticationFilter** ([auth/security/JwtAuthenticationFilter.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/auth/security/JwtAuthenticationFilter.java))
- Runs on **every HTTP request** (extends `OncePerRequestFilter`)
- Operates BEFORE Spring's standard authentication filters
- Steps:
  1. Extract token from `Authorization: Bearer <token>` header
  2. Validate token signature and expiration
  3. Extract email from token claims
  4. Load user details from database (includes role/authorities)
  5. Create `UsernamePasswordAuthenticationToken` with user and authorities
  6. Set authentication in `SecurityContextHolder` (makes user "logged in" for this request)
  7. Continue filter chain (request now has access to authenticated user)

**3. CustomUserDetailsService** ([auth/security/CustomUserDetailsService.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/auth/security/CustomUserDetailsService.java))
- Implements Spring's `UserDetailsService` interface
- Called during JWT validation to load user from database
- Converts our `User` entity to Spring's `UserDetails`
- Maps `UserRole` enum to Spring `GrantedAuthority` (adds "ROLE_" prefix)
  - Example: `ADMIN` → `ROLE_ADMIN`, `CUSTOMER` → `ROLE_CUSTOMER`

**4. SecurityConfig** ([auth/security/SecurityConfig.java](backend/portfolio-app/src/main/java/com/leaping/portfolio_app/auth/security/SecurityConfig.java))
- Central security configuration using `@EnableWebSecurity`
- Creates security filter chain with:
  - **CORS configuration:** Allows frontend (localhost:4200, 3000) to make cross-origin requests
  - **CSRF disabled:** Not needed for stateless JWT auth
  - **Route authorization:**
    - `/api/auth/**` and `/auth/**` - Public (login, register, validate)
    - `/api/public/**` - Public endpoints
    - All other routes - Require authentication
  - **Filter order:** JWT filter inserted BEFORE `UsernamePasswordAuthenticationFilter`
  - **Password encoder:** BCrypt (industry standard)

### User Role Hierarchy

```
User (Base entity in users table)
├── ADMIN (role = 'ADMIN')
│   └── No specific fields; admin_id table exists but empty
│
├── CUSTOMER (role = 'CUSTOMER')
│   └── Customer (1:1 via user_id PK=FK)
│       ├── date_of_birth (required)
│       ├── tax_id (unique)
│       └── Portfolio (1:1)
│           ├── cash_balance_usd
│           ├── Holdings (1:N)
│           └── TradeOrders (1:N)
│
└── ANALYST (role = 'ANALYST')
    └── Analyst (1:1 via user_id PK=FK)
        └── Can analyze system-wide data
```

### Authentication Endpoints

| Endpoint | Method | Auth Required | Purpose |
|----------|--------|---------------|---------|
| `/api/auth/login` | POST | ❌ No | Login with email/password, returns access & refresh tokens |
| `/api/auth/register` | POST | ❌ No | Create new customer account |
| `/api/auth/validate` | GET | ❌ No | Check if a token is valid (with Authorization header) |
| `/api/auth/refresh` | POST | ❌ No | Exchange refresh token for new access token |
| `/api/auth/logout` | POST | ✅ Yes | Logout (currently just returns success) |
| `/api/auth/admin/test` | GET | ✅ Yes (ADMIN only) | Test endpoint to verify role-based access |

---

## 5. Database Entities & Relationships

### Entity-Relationship Model

```
USERS (Master entity for all user types)
├─── 1:1 ──→ CUSTOMERS (Customer-specific data)
│            └─── 1:1 ──→ PORTFOLIOS (Trading account)
│                         ├─── 1:N ──→ CASH_TRANSACTIONS
│                         ├─── 1:N ──→ HOLDINGS (Current positions)
│                         │            └─── 1:1 ──→ INSTRUMENTS
│                         │
│                         └─── 1:N ──→ TRADE_ORDERS (Buy/sell requests)
│                                      ├─── 1:1 ──→ INSTRUMENTS
│                                      └─── 1:N ──→ TRADES (Actual executions)
│                                           └─── 1:N ──→ EXECUTION_ATTEMPTS
│
├─── 1:1 ──→ ADMINS (Admin-specific data - currently empty)
│
└─── 1:1 ──→ ANALYSTS (Analyst-specific data)


INSTRUMENTS (Financial assets)
├─── 1:1 ──→ STOCKS (Stock-specific fields)
├─── 1:1 ──→ FOREX (Foreign exchange pairs)
├─── 1:1 ──→ CRYPTO (Cryptocurrency-specific fields)
├─── 1:N ──→ MARKETS (Which market it trades on)
└─── 1:N ──→ CURRENCY (Price denomination)


MARKETS (Trading venues)
└─── 1:N ──→ INSTRUMENTS


CURRENCIES (Currency codes)
└─── 1:N ──→ INSTRUMENTS (as price currency)
```

### Key Entities Explained

**USER** (Users table)
```
- user_id (PK): Auto-generated Long
- email: Unique, can be NULL (for imports)
- password_hash: Bcrypt hashed (can be NULL initially)
- first_name, last_name
- user_type: ENUM(ADMIN, CUSTOMER, ANALYST) - determines role
- status: ENUM(ACTIVE, INACTIVE, LOCKED) - controls login
- created_at, updated_at, last_login: Timestamps
```

**CUSTOMER** (Customers table - 1:1 with Users)
```
- user_id (PK + FK): Same as parent User
- date_of_birth: Required for KYC
- tax_id: Unique identifier (SSN, VAT number, etc.)
```

**PORTFOLIO** (Portfolios table - 1:1 with Customer)
```
- portfolio_id (PK): Auto-generated
- customer_id (FK, UNIQUE): One portfolio per customer
- cash_balance_usd: Available USD cash (decimal)
- created_at, updated_at
- Constraint: cash_balance_usd >= 0 (no short selling cash)
```

**INSTRUMENT** (Instruments table - Parent for Stock/Forex/Crypto)
```
- instrument_id (PK): Auto-generated
- market_id (FK): Which market it trades on
- symbol: Ticker symbol (e.g., "AAPL", "EUR/USD", "BTC")
- name: Full name
- instrument_type: ENUM(STOCK, FOREX, CRYPTO)
- price_currency_code (FK): Currency of pricing
- is_tradeable: Boolean (can orders be placed?)
- is_active: Boolean (is it listed?)
- created_at, updated_at
- Unique constraint: (market_id, symbol) - no duplicate tickers per market
```

**STOCK** (Stocks table - extends Instrument 1:1)
```
- instrument_id (PK + FK): Foreign key to Instrument
- sector: Industry sector (e.g., "Technology")
- industry: More specific category
- country: Company country
```

**TRADE_ORDER** (Trade_orders table - Buy/Sell requests)
```
- order_id (PK): Auto-generated
- portfolio_id (FK): Which portfolio this order belongs to
- instrument_id (FK): What to buy/sell
- order_action: ENUM(BUY, SELL)
- order_type: ENUM(MARKET, LIMIT) - immediate or conditional
- order_status: ENUM(PENDING, ACCEPTED, REJECTED, FILLED, CANCELLED, EXPIRED)
- time_in_force: ENUM(DAY, GTC) - how long to keep order active
- quantity: Amount to buy/sell (decimal with precision for crypto)
- limit_price_usd: For LIMIT orders only (price per unit)
- submitted_at, accepted_at, filled_at, rejected_at, cancelled_at
```

**TRADE** (Trades table - Execution of orders)
```
- trade_id (PK): Auto-generated
- trade_order_id (FK): Which order was executed
- executed_price_usd: Actual fill price
- filled_quantity: How much was actually filled
- executed_at: When the trade happened
- settlement_at: When cash/security will be settled
```

**EXECUTION_ATTEMPT** (Execution_attempts table - Audit trail)
```
- attempt_id (PK)
- trade_id (FK)
- broker: Which broker executed (e.g., "SIMULATION", "IB")
- status: Attempt result
- error_message: If it failed
- attempted_at: When attempted
```

### Why This Design?

✅ **Single Responsibility:** Each table has a clear purpose
✅ **Normalization:** No data duplication (reduces inconsistencies)
✅ **Extensibility:** Can add new instrument types (NFTs, Options) without schema changes
✅ **Audit Trail:** Execution attempts track every action
✅ **Constraints:** Database enforces business rules (cash >= 0, unique emails)
✅ **Partitioning Ready:** Large tables (trades, execution_attempts) can be partitioned by date

---

## 6. API Endpoints & Routes

### Authentication Endpoints
**Base URL:** `/api/auth`

```
POST /login
├─ Request: { email, password }
├─ Response: { token, email, role, refreshToken, message, success }
└─ Status: 200 OK or 401 Unauthorized

POST /register
├─ Request: { email, password, confirmPassword, firstName, lastName, 
│            dateOfBirth, taxId }
├─ Response: { userId, email, message, success }
└─ Status: 201 Created or 400 Bad Request

POST /refresh
├─ Request: { refreshToken }
├─ Response: { token, email, role, message, success }
└─ Status: 200 OK or 401 Unauthorized

GET /validate
├─ Header: Authorization: Bearer <token>
├─ Response: { token, email, message, success }
└─ Status: 200 OK or 401 Unauthorized

POST /logout
├─ Response: { message, success }
└─ Status: 200 OK

GET /admin/test (ADMIN role required)
├─ Response: "ADMIN access confirmed - RBAC is working!"
└─ Status: 200 OK
```

### Instrument Endpoints
**Base URL:** `/api/instruments`

```
GET /
├─ Response: List<Instrument> { instrumentId, market, symbol, name, 
│                                instrumentType, priceCurrency, ... }
└─ Status: 200 OK (requires authentication)

GET /{id}
├─ Response: Instrument (single object)
└─ Status: 200 OK or 404 Not Found
```

### Protected vs. Public Routes

**Public (no authentication required):**
- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /api/auth/validate`
- `POST /api/auth/refresh`
- `GET /api/auth/health`
- `GET /api/public/**`

**Protected (authentication required):**
- All other endpoints including:
  - `/api/instruments/**`
  - `/api/portfolios/**`
  - `/api/trades/**`
  - etc.

---

## 7. Configuration & Dependency Injection

### Spring Bean Management

**SecurityConfig** (Defines security beans):
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(); // Costs 10 rounds
}

@Bean
public JwtAuthenticationFilter jwtAuthenticationFilter(
    JwtTokenProvider jwtTokenProvider,
    UserDetailsService userDetailsService) {
    return new JwtAuthenticationFilter(...);
}

@Bean
public CorsConfigurationSource corsConfigurationSource() {
    // Configure which origins can make cross-origin requests
}

@Bean
public SecurityFilterChain securityFilterChain(
    HttpSecurity http,
    JwtAuthenticationFilter jwtAuthenticationFilter,
    CorsConfigurationSource corsConfigurationSource) {
    // Configure filter chain, route authorization, etc.
}
```

### Dependency Injection Pattern

All classes use **constructor injection** (Spring best practice):

```java
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    // Constructor injection - dependencies passed in
    public AuthService(UserRepository userRepository,
                      JwtTokenProvider jwtTokenProvider,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }
}
```

**Advantages:**
- Immutable fields (`final`)
- Required dependencies explicit in constructor
- Easy to test (can mock dependencies)
- No hidden dependencies or NullPointerException risks

### Configuration Properties

**JWT Configuration** (in application-local.yaml or environment variables):
```yaml
jwt:
  secret: "YOUR_JWT_SECRET_KEY_HERE_..."  # Min 256 bits for HS512
  expiration: 3600000                      # 1 hour in ms
  refresh-expiration: 604800000            # 7 days in ms

spring:
  datasource:
    username: postgres
    password: YOUR_PASSWORD

  jpa:
    hibernate:
      ddl-auto: validate                   # Use Flyway for migrations

  flyway:
    locations: classpath:db/migration    # Migration scripts location
```

### Component Scanning

Spring automatically discovers and registers:
- `@Configuration` classes → Beans
- `@Service` classes → Service beans
- `@Repository` classes → Data access beans
- `@RestController` classes → HTTP endpoints
- `@Component` classes → Generic beans

All within `com.leaping.portfolio_app` package and subpackages.

---

## 8. Request Lifecycle: End-to-End Flow

### Example: Getting All Instruments

```
1. CLIENT REQUEST
   GET /api/instruments
   Header: Authorization: Bearer eyJhbGc...

2. SPRING DISPATCHES TO FILTER CHAIN
   ↓
3. JwtAuthenticationFilter.doFilterInternal()
   ├─ Extract token from Authorization header
   ├─ JwtTokenProvider.validateToken() → checks signature & expiration
   ├─ JwtTokenProvider.getEmailFromToken() → returns email
   ├─ CustomUserDetailsService.loadUserByUsername(email)
   │  └─ UserRepository.findByEmail() → finds User with role
   ├─ Create Authentication with GrantedAuthority("ROLE_CUSTOMER")
   ├─ SecurityContextHolder.setAuthentication() → user now "logged in"
   └─ filterChain.doFilter() → continue to next filter/handler

4. SecurityFilterChain evaluates route
   └─ /api/instruments requires authentication ✓ (user is authenticated)

5. SPRING ROUTES TO CONTROLLER
   InstrumentController.getAllInstruments()

6. CONTROLLER CALLS SERVICE
   InstrumentService.getAllInstruments()

7. SERVICE CALLS REPOSITORY
   InstrumentRepository.findAll()

8. REPOSITORY EXECUTES SQL
   SELECT * FROM instruments

9. DATABASE RETURNS RESULTS
   └─ List of Instrument rows

10. RESPONSE LAYERS BUILD
    Repository → List<Instrument> entities
    Service → List<Instrument> 
    Controller → ResponseEntity<List<Instrument>>

11. SPRING SERIALIZES TO JSON
    @RestController automatically converts entities to JSON

12. HTTP RESPONSE
    Status: 200 OK
    Body: [
      {
        "instrumentId": 1,
        "symbol": "AAPL",
        "name": "Apple Inc.",
        "instrumentType": "STOCK",
        ...
      },
      ...
    ]
```

### Example: User Login

```
1. CLIENT REQUEST
   POST /api/auth/login
   Body: { "email": "user@example.com", "password": "secret123" }

2. SPRING ROUTES TO CONTROLLER
   AuthController.login(LoginRequest)

3. CONTROLLER CALLS SERVICE
   AuthService.authenticate(loginRequest)

4. SERVICE VALIDATES
   ├─ Check email & password not empty ✓
   ├─ UserRepository.findByEmail() → User
   ├─ Check user.isActive() ✓
   ├─ PasswordEncoder.matches(password, user.getPassword())
   │  └─ Compares input against BCrypt hash ✓
   └─ All checks pass!

5. SERVICE GENERATES TOKENS
   ├─ JwtTokenProvider.generateToken(email)
   │  └─ Creates JWT with email as subject, HS512 signature, 1hr expiry
   ├─ JwtTokenProvider.generateRefreshToken(email)
   │  └─ Creates JWT with email, "type":"refresh" claim, 7-day expiry
   └─ Tokens generated successfully

6. SERVICE RETURNS AuthResponse
   {
     "token": "eyJhbGc...",
     "refreshToken": "eyJhbGc...",
     "email": "user@example.com",
     "role": "CUSTOMER",
     "message": "Authentication successful",
     "success": true
   }

7. CONTROLLER RETURNS HTTP RESPONSE
   Status: 200 OK
   Body: (AuthResponse JSON above)

8. CLIENT STORES TOKENS
   ├─ Access token in memory
   └─ Refresh token in localStorage (or secure httpOnly cookie)

9. CLIENT SUBSEQUENT REQUESTS
   GET /api/instruments
   Header: Authorization: Bearer eyJhbGc...
   (Uses access token from step 8)
```

---

## 9. Security Considerations

### Password Security
- **Storage:** BCrypt with 10 rounds of hashing
- **Validation:** Never log passwords, use `PasswordEncoder.matches()`
- **Strength:** No validation enforced (frontend should validate)

### JWT Token Security
- **Signature:** HS512 (HMAC with SHA-512)
- **Secret:** Stored in environment variable (not in source code)
- **Expiration:** 1 hour for access token (minimize damage if leaked)
- **Refresh Token:** 7-day expiration for generating new access tokens
- **Transport:** Must use HTTPS in production (tokens sent in header)

### CORS Security
- **Allowed Origins:** Explicitly configured (not wildcard in production)
- **Credentials:** Allowed for authenticated requests
- **Methods:** GET, POST, PUT, DELETE, PATCH, OPTIONS
- **Headers:** Content-Type, Authorization, X-Requested-With

### Authentication Flow Security
- **Filter Order:** JWT filter runs BEFORE standard auth filter
- **Stateless:** No sessions stored on server (JWT is self-contained)
- **Role-Based Access:** `@PreAuthorize("hasRole('ADMIN')")` on methods
- **Role Binding:** Roles loaded fresh from database on each request

### SQL Injection Prevention
- **JPA Parameterized Queries:** Repositories use `@Query` with `:param` binding
- **Spring Data Auto-Query:** `findByEmail()` generates safe SQL
- **No String Concatenation:** Never build queries by concatenating strings

---

## 10. Data Flow Summary

### Write Operation (Register New Customer)

```
Frontend (Angular)
  ↓ POST /api/auth/register
    ↓ AuthController.register()
      ↓ AuthService.register()
        ├─ Validate input (email, password, DOB, taxId)
        ├─ Check for duplicate email
        ├─ Hash password with BCrypt
        ├─ UserRepository.save(newUser) → INSERT into users table
        ├─ CustomerRepository.save(newCustomer) → INSERT into customers table
        ├─ PortfolioRepository.save(newPortfolio) → INSERT into portfolios table
        └─ Return RegisterResponse with userId
      ↓
Backend Database (PostgreSQL)
  ├─ new row in users table
  ├─ new row in customers table
  └─ new row in portfolios table
```

### Read Operation (Get All Instruments)

```
Frontend (Angular) with JWT token
  ↓ GET /api/instruments + Bearer token
    ↓ JwtAuthenticationFilter (validates JWT, loads user)
      ↓ InstrumentController.getAllInstruments()
        ↓ InstrumentService.getAllInstruments()
          ↓ InstrumentRepository.findAll()
            ↓ SQL: SELECT * FROM instruments
            ↓
Backend Database (PostgreSQL)
  Scans instruments table, returns all rows
            ↓
Hibernate maps rows → Instrument entities
↓ Service returns List<Instrument>
↓ Controller returns ResponseEntity with JSON
↓
Frontend (Angular)
  Receives JSON array of instruments
  Parses and displays in UI
```

---

## 11. Extending the Application

### Adding a New Domain

1. **Create folder structure:**
   ```
   src/main/java/com/leaping/portfolio_app/newdomain/
   ├── entity/
   ├── enums/
   ├── repository/
   ├── service/
   └── (optional) controller/ and dto/
   ```

2. **Create Entity with JPA annotations:**
   ```java
   @Entity
   @Table(name = "new_domain")
   public class NewEntity {
       @Id
       @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;
       // fields...
   }
   ```

3. **Create Repository (extends JpaRepository):**
   ```java
   @Repository
   public interface NewEntityRepository 
       extends JpaRepository<NewEntity, Long> {
       // custom queries if needed
   }
   ```

4. **Create Service:**
   ```java
   @Service
   public class NewEntityService {
       private final NewEntityRepository repository;
       public NewEntityService(NewEntityRepository repository) {
           this.repository = repository;
       }
       // business logic methods
   }
   ```

5. **Create Controller (if exposing APIs):**
   ```java
   @RestController
   @RequestMapping("/api/newentity")
   public class NewEntityController {
       private final NewEntityService service;
       // endpoints...
   }
   ```

6. **Add Database Migration:**
   ```sql
   -- src/main/resources/db/migration/VN__description.sql
   CREATE TABLE new_domain (
       id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
       // column definitions
   );
   ```

### Adding Role-Based Access Control

```java
@PreAuthorize("hasRole('CUSTOMER')")
public ResponseEntity<?> customerEndpoint() {
    // Only customers can access
}

@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminEndpoint() {
    // Only admins can access
}

@PreAuthorize("hasAnyRole('ADMIN', 'ANALYST')")
public ResponseEntity<?> analyticEndpoint() {
    // Admins and analysts can access
}
```

---

## Summary

The Portfolio App is a **well-architected Spring Boot application** that demonstrates:

✅ **Separation of Concerns:** Clear layers (controller → service → repository → entity)
✅ **Domain-Driven Design:** Organized by business domains, not technical layers
✅ **Security:** JWT tokens, BCrypt passwords, role-based access control
✅ **Scalability:** Use of Spring Data JPA for ORM, Flyway for migrations
✅ **Maintainability:** Constructor injection, immutable fields, clear naming
✅ **Extensibility:** Easy to add new domains, entities, or endpoints
✅ **Database Integrity:** Constraints, foreign keys, normalization, audit trails

The request flow is straightforward:
1. Client sends HTTP request with JWT token
2. Filter validates token and loads user from database
3. Request routed to appropriate controller
4. Controller delegates to service for business logic
5. Service uses repositories for data access
6. Repositories execute JPA queries translated to SQL
7. Database returns results
8. Response flows back through layers to client as JSON
