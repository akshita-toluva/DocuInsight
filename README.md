# DocuInsight

DocuInsight is an AI-powered document intelligence platform. Upload a PDF, CSV, DOCX, TXT file, or an image, and the platform extracts its content, sends it to an AI model, and returns a structured, AI-generated report — instead of reading the whole document yourself.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| Security | Spring Security 7 + JWT |
| Database | PostgreSQL 16 |
| ORM | Hibernate 7.2.12 |
| AI Provider | Groq (LLaMA 3.3 70B) |
| PDF Processing | Apache PDFBox 3.0.1 |
| CSV Processing | OpenCSV 5.9 |
| DOCX Processing | Apache POI 5.3.0 |
| HTTP Client | Spring WebFlux WebClient |

## Features

- **Authentication** — register and log in with JWT-based sessions, passwords hashed with BCrypt
- **File upload** — supports PDF, CSV, DOCX, TXT, and images (PNG/JPEG/WEBP), scoped to each user
- **Text extraction** — pulls text from PDFs, CSVs, and DOCX files; falls back to a vision model for scanned or image-heavy PDFs and for image uploads; caches results so re-extraction is instant
- **AI report generation** — Executive Summary, Technical Analysis, or a Custom prompt, generated via Groq's LLaMA 3.3 70B, with results cached per file and report type
- **Multi-document reports** — generate one combined report synthesizing several uploaded files at once
- **Ask a question** — Q&A against the source document behind a completed report
- **Export** — download any report as a PDF or Word document
- **User profile** — view and update profile details, change password
- **Security & error handling** — every endpoint is JWT-protected, ownership checks prevent access to other users' files and reports, and errors return clean JSON with correct status codes