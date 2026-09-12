
# Industrial Material Exchange (IME)

![Java](https://img.shields.io/badge/Java-21-orange.svg?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.4-6DB33F.svg?style=for-the-badge&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14%2B-4169E1.svg?style=for-the-badge&logo=postgresql)
![Security](https://img.shields.io/badge/Security-Spring_Security_%2B_JWT-red.svg?style=for-the-badge)


## 📖 Project Description
Industrial Material Exchange (IME) is a B2B platform making it easier for factories and material suppliers to trade with one another. If you have surplus inventory to clear or specific raw materials you need to buy, IME connects you directly to the right trading partners to reduce waste.


<img width="1917" height="1033" alt="Screenshot 2026-08-14 103514" src="https://github.com/user-attachments/assets/a5469fed-474e-4166-ac1a-0ddd5370592c" />

### Versions 
| Technology | Implementation & Purpose |
| :--- | :--- |
| **Java 21** | Core language. Uses record patterns and virtual threads for clean type safety and high-concurrency scaling. |
| **Spring Boot 3.5.4** | Application framework. Handles backend MVC architecture, dependency injection, and data access. |
| **Spring Security & JWT** | Authentication layer. Provides stateless JWT tokens for APIs alongside standard web session security. |
| **PostgreSQL** | Primary database. Manages relational data, ACID transactions, and spatial indexing for location features. |
| **Spring Data JPA** | ORM layer using Hibernate. Automates data queries and enforces soft-deletes via `@SQLDelete`. |
| **Leaflet.js & OSM** | Frontend mapping. Renders lightweight, interactive map visualizations for material sourcing. |
| **Vanilla CSS** | UI styling. Uses a custom CSS variable design system to avoid heavy third-party framework overhead. |

---

## ⚙️ How to Install and Run the Project

### Prerequisites

Ensure you have the following installed on your machine:
- **JDK 21** or higher (`java -version`)
- **Apache Maven 3.8+** (or use the included `mvnw` wrapper)
- **PostgreSQL 14+** running locally or accessible via network

---

### 1. Database Setup

Create a PostgreSQL database named `ime`:

```sql
CREATE DATABASE ime;
```

*(Optional)* Create a dedicated database user or use the default `postgres` user.

---

### 2. Clone & Build

Clone the repository:

```bash
git clone https://github.com/Hruthik08-tech/industrial-material-exchange.git
cd industrial-material-exchange
```

Build the project using Maven wrapper:

```bash
# On Linux/macOS
./mvnw clean compile

# On Windows
mvnw.cmd clean compile
```

---

### 3. Configuration

Update `src/main/resources/application.properties` with your PostgreSQL database credentials and JWT secret:

```properties
spring.application.name=IME

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/ime
spring.datasource.username=postgres
spring.datasource.password=YOUR_POSTGRES_PASSWORD

# Hibernate DDL Auto & Logging
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# JWT Configuration
jwt.secret=JWT_SECRET
jwt.expirationMs=86400000
```

---

### 4. Run the Application

Launch the Spring Boot dev server:

```bash
# On Linux/macOS
./mvnw spring-boot:run

# On Windows
mvnw.cmd spring-boot:run
```

Once started, access the application in your browser at:
`http://localhost:8080`

---

## 🚀 How to Use the Project

### User Workflow

1. **Registration:**
   - Navigate to `/auth/signup` to register an organisation with contact number, location coordinates (lat/lng), address, and password.
2. **Post Supply / Demand:**
   - Log in and navigate to `/supply/create` or `/demand/create` to post available materials or requirement requests.
3. **Dashboard:**
   - View your posted supplies, demands, and active marketplace status on `/dashboard`.
4. **Interactive Discovery:**
   - Visit `/discover` to inspect live supply markers on the Leaflet interactive map, view match percentage scores, and connect with verified partners.

---

### Test Credentials

For quick evaluation, pre-configured organisation accounts are available:

| Contact Number | Password | Role / Organisation | Location |
| :--- | :--- | :--- | :--- |
| `9845000001` | `password` | Karnataka Steel Fab | Kolar, KA |
| `9845000002` | `password` | Hoskote Timber Mills | Mysore, KA |
| `9845000003` | `password` | Vellore Tannery & Textiles | Anantapur, AP |
| `9845000004` to `9845000050` | `password` | Test Organisations 4-50 | Karnataka / AP / TN |

---

## 🏗️ Project Architecture & Design Principles

```
src/main/java/com/hruthikesh/ime/
├── controller/         # Web Controllers (Auth, Dashboard, Demand, Supply, Discovery)
├── dto/                # Request & Response Transfer Objects
├── entity/             # JPA Entities (Organisation, Supply, Demand, Category)
│   └── enums/          # Status & Unit Enums
├── repository/         # Spring Data JPA Repositories
├── security/           # Spring Security, JWT Filters & UserDetails
└── service/            # Business Logic & Matching Algorithm Engine
```

---


