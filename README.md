# DocuInsight

> An AI-powered document intelligence platform. Upload a PDF or CSV, extract its text, and generate a structured AI report in seconds — Executive Summary, Technical Analysis, or a fully custom prompt.

---

## What It Does

Most document analysis tools make you read the whole thing yourself. DocuInsight sends your document to an AI model and returns a structured, professional report. You get the insight without the reading time.

**Three report types:**
- **Executive Summary** — business-focused, key findings, recommendations
- **Technical Analysis** — deep-dive into architecture, data, and technical details
- **Custom** — you write the instruction, the AI follows it

**Smart caching:** Executive and Technical reports for the same file are only generated once. Every subsequent request returns the cached result instantly, saving API costs and time.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Security | Spring Security 7.0.5 + JWT (jjwt 0.12.3) |
| Database | PostgreSQL 16 |
| ORM | Hibernate / Spring Data JPA |
| AI Provider | Groq API — LLaMA 3.3 70B Versatile |
| PDF Extraction | Apache PDFBox 3.0.1 |
| CSV Extraction | OpenCSV 5.9 |
| HTTP Client | Spring WebFlux WebClient |
| Validation | Jakarta Bean Validation |
| Build | Maven |

---

## Project Structure

```
src/main/java/com/docuinsight/docuinsight/
│
├── controller/
│   ├── AuthController.java          # POST /api/auth/register, /login
│   ├── FileController.java          # Upload, list, extract endpoints
│   ├── ReportController.java        # Generate, list, get, delete endpoints
│   └── GlobalExceptionHandler.java  # Centralised error responses
│
├── service/
│   ├── AuthService.java             # Register + login logic, BCrypt hashing
│   ├── FileService.java             # File upload, storage, type validation
│   ├── TextExtractionService.java   # PDFBox + OpenCSV extraction with DB cache
│   ├── LLMService.java              # Groq API call via WebClient
│   ├── PromptTemplateService.java   # Builds EXECUTIVE / TECHNICAL / CUSTOM prompts
│   ├── ReportService.java           # Orchestrates extraction → prompt → LLM → save
│   └── ReportExportService.java     # PDF and DOCX export via PDFBox and Apache POI
│
├── model/
│   ├── User.java                    # Users table entity
│   ├── UploadedFile.java            # uploaded_files table entity
│   ├── Report.java                  # reports table entity (ReportType + ReportStatus enums)
│   ├── RegisterRequest.java         # Validated request body for registration
│   ├── LoginRequest.java            # Validated request body for login
│   ├── ReportRequest.java           # Validated request body for report generation
│   ├── AuthResponse.java            # { message, token }
│   ├── FileUploadResponse.java      # { fileId, fileName, fileType, fileSize, message }
│   ├── ExtractionResponse.java      # { fileId, fileName, extractedText, characterCount, message }
│   └── ReportResponse.java          # Full report DTO returned to client
│
├── repository/
│   ├── UserRepository.java
│   ├── UploadedFileRepository.java
│   └── ReportRepository.java
│
└── security/
    ├── SecurityConfig.java          # Stateless JWT filter chain, public routes
    ├── JwtService.java              # Token generation + validation (HS256)
    ├── JwtFilter.java               # Per-request token extraction + auth
    └── WebClientConfig.java         # WebClient bean configuration
```

---

## Features Built

- **JWT Authentication** — stateless, 24-hour tokens, BCrypt password hashing
- **File Upload** — PDF and CSV support, 10 MB size limit, files saved to `uploads/` folder
- **Text Extraction** — PDFBox for PDFs page by page, OpenCSV row by row; extracted text cached in PostgreSQL so re-extraction is instant
- **AI Report Generation** — three report types, per-user caching, `forceRegenerate` flag to bypass cache
- **Full Report Lifecycle** — generate, list all, list by file, get single, delete
- **Report Export** — download any completed report as PDF or DOCX
- **Ownership Isolation** — every file and report is tied to its owner; one user cannot access another user's data
- **Global Error Handling** — every exception returns a clean JSON error with the correct HTTP status code; no raw stack traces exposed to clients

---

## Database Schema

```
users
  id (PK), name, email (unique), password (BCrypt), created_at

uploaded_files
  id (PK), user_id (FK), file_name, file_type, file_path, file_size,
  extracted_text (TEXT, nullable — null until /extract is called), uploaded_at

reports
  id (PK), file_id (FK), user_id (FK), report_type (EXECUTIVE|TECHNICAL|CUSTOM),
  report_content (TEXT), custom_prompt (TEXT), status (PENDING|PROCESSING|COMPLETED|FAILED),
  error_message, created_at, completed_at, token_count
```

---

## API Reference

All protected endpoints require: `Authorization: Bearer <token>`

### Authentication

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register a new user |
| POST | `/api/auth/login` | Login and receive JWT |

**Register request body**
```json
{
  "name": "Alice Smith",
  "email": "alice@example.com",
  "password": "password123"
}
```

**Login response**
```json
{
  "message": "Login Successful",
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

---

### Files

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/files/upload` | Yes | Upload PDF or CSV (multipart/form-data, key = `file`) |
| GET | `/api/files/my-files` | Yes | List all files uploaded by the logged-in user |
| POST | `/api/files/{fileId}/extract` | Yes | Extract text from a file; cached after first call |

**Upload response**
```json
{
  "fileId": 3,
  "fileName": "quarterly_report.pdf",
  "fileType": "application/pdf",
  "fileSize": 204800,
  "message": "File uploaded successfully"
}
```

**Extract response (first call)**
```json
{
  "fileId": 3,
  "fileName": "quarterly_report.pdf",
  "extractedText": "Q3 revenue was $4.2M...",
  "characterCount": 8421,
  "message": "Extraction Successful"
}
```

**Extract response (subsequent calls — cached)**
```json
{
  "message": "Already extracted returning cached text"
}
```

---

### Reports

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/reports/generate/{fileId}` | Yes | Generate a report for a file |
| GET | `/api/reports/my-reports` | Yes | List all reports for the logged-in user |
| GET | `/api/reports/file/{fileId}` | Yes | List all reports for a specific file |
| GET | `/api/reports/{reportId}` | Yes | Get a single report by ID |
| DELETE | `/api/reports/{reportId}` | Yes | Delete a report |
| GET | `/api/reports/{reportId}/export?format=pdf` | Yes | Export report as PDF |
| GET | `/api/reports/{reportId}/export?format=docx` | Yes | Export report as DOCX |

**Generate request body — Executive or Technical**
```json
{
  "reportType": "EXECUTIVE"
}
```

**Generate request body — Custom**
```json
{
  "reportType": "CUSTOM",
  "customPrompt": "List all financial risks mentioned in this document.",
  "forceRegenerate": false
}
```

- `reportType` — required, one of `EXECUTIVE`, `TECHNICAL`, `CUSTOM`
- `customPrompt` — required when `reportType` is `CUSTOM`, must be 10–2000 characters
- `forceRegenerate` — optional boolean, default `false`. Set to `true` to skip the cache and call the AI again

**Generate response**
```json
{
  "reportId": 10,
  "fileId": 3,
  "fileName": "quarterly_report.pdf",
  "reportType": "EXECUTIVE",
  "status": "COMPLETED",
  "reportContent": "## Executive Summary\n\n## Overview\n...",
  "errorMessage": null,
  "createdAt": "2026-06-07T14:30:00",
  "completedAt": "2026-06-07T14:30:08",
  "cached": false
}
```

**List my reports response**
```json
{
  "reports": [ { ...report objects... } ],
  "count": 4
}
```

---

## How to Run Locally

### Prerequisites

- Java 21
- Maven
- PostgreSQL 16 running locally
- A [Groq API key](https://console.groq.com) (free tier available)

### 1. Clone the repository

```bash
git clone https://github.com/your-username/docuinsight.git
cd docuinsight
```

### 2. Create the database

```sql
CREATE DATABASE "docuInsight_db";
```

### 3. Set environment variables

The application reads secrets from environment variables. Set these in your system or in a `.env` file loaded by your IDE:

```
SPRING_DATASOURCE_PASSWORD=your_postgres_password
JWT_SECRET=a_long_random_string_at_least_64_characters
GROQ_API_KEY=your_groq_api_key
```

Or in IntelliJ, go to **Run → Edit Configurations → Environment variables** and add them there.

### 4. Configure application.properties

`src/main/resources/application.properties` already references the environment variables:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/docuInsight_db
spring.datasource.username=postgres
spring.datasource.password=${spring.datasource.password}
jwt.secret=${jwt.secret}
jwt.expiration=86400000
groq.api.key=${groq.api.key}
groq.api.model=llama-3.3-70b-versatile
file.upload-dir=uploads
spring.servlet.multipart.max-file-size=10MB
```

### 5. Run

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

## End-to-End Flow

```
POST /api/auth/register        → Create account
POST /api/auth/login           → Get JWT token
POST /api/files/upload         → Upload PDF/CSV  → returns fileId
POST /api/files/{fileId}/extract → Extract text  → text cached in DB
POST /api/reports/generate/{fileId} → Send text to Groq → AI generates report
GET  /api/reports/{reportId}   → Read the report
GET  /api/reports/{reportId}/export?format=pdf  → Download as PDF
GET  /api/reports/{reportId}/export?format=docx → Download as DOCX
DELETE /api/reports/{reportId} → Delete the report
```

---

## Error Handling

All errors return a consistent JSON shape:

```json
{ "error": "Descriptive error message" }
```

| Scenario | Status Code |
|---|---|
| Validation failure (`@NotBlank`, `@Email`, `@Size`) | `400 Bad Request` |
| Duplicate email on register | `400 Bad Request` |
| Wrong password / unknown email | `401 Unauthorized` |
| No or invalid JWT token | `401` / `403` |
| Accessing another user's resource | `403 Forbidden` |
| File or report not found | `404 Not Found` |
| Unsupported file type | `400 Bad Request` |
| File exceeds 10 MB | `413 Payload Too Large` |
| Groq rate limit | `429 Too Many Requests` |
| Unexpected server error | `500 Internal Server Error` |

---

## What Is Coming Next

| Feature | Status |
|---|---|
| DOCX and TXT file support | Planned |
| User profile update (name, password) | Planned |
| Rate limiting (daily report quota per user) | Planned |
| Public report sharing via link | Planned |
| React frontend dashboard | Planned |
| Docker containerisation | Planned |
| Swagger / OpenAPI documentation | Planned |

---

## Testing

A full Postman collection (52 test cases) is included in the repository:

- `DocuInsight_Postman_Collection_v2.json` — import into Postman
- `DocuInsight_Postman_Environment_v2.json` — import as the active environment

The collection covers all happy-path scenarios, negative validation cases, security isolation tests (two-user ownership), and edge cases. Test scripts run automatically after each request and show pass/fail in the Tests tab.

**Run order:** TC-A-06 Login → TC-F-01 Upload → TC-F-09 Extract → TC-R-01 Generate → Security setup (Register Bob, Login Bob) → remaining tests in any order.

---

## License

This project is for learning and portfolio purposes.
