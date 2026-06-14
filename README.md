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