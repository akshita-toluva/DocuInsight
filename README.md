# DocuInsight

**AI document assistant with grounded answers.** Upload a PDF, Word, text, CSV or image file — DocuInsight extracts the text, generates an AI report (Executive Summary, Technical Analysis or Custom), and answers questions about the document **with the page each answer came from**. If the answer is not in the document, it says so instead of making one up.

> **Live demo:** https://docu-insight-omega.vercel.app — free tier: the backend sleeps when idle, so the first request can take **1–2 minutes**. Heavy extraction can exceed the free 512 MB server (see [Limits](#known-limits)); the full app runs locally with Docker Compose.

---

## Features

| | |
|---|---|
| **Auth** | Register / log in, BCrypt passwords, stateless JWT on every request |
| **Upload & extract** | PDF (page by page), DOCX, TXT, CSV, images; scanned / image-only pages read by a vision model |
| **AI reports** | Executive Summary, Technical Analysis, Custom prompt; cached per file + type |
| **Ask the document (RAG)** | Answers cite numbered passages; sources show page + snippet; refuses when the document has no answer |
| **Compare** | One report across several documents |
| **Export** | Download any report as PDF or Word |
| **Fair use** | Per-user daily limits (5 reports, 30 questions) → HTTP 429 + `Retry-After`; remaining usage shown in the UI |
| **API docs** | Swagger UI with a JWT "Authorize" button (`/swagger-ui.html`) |

## How it works

Documents are split into 800-character chunks (page numbers kept), embedded locally with all-MiniLM-L6-v2, and stored in PostgreSQL + pgvector. 
For each question, the most relevant chunks of the user's file are sent to the LLM, which must cite them or say the answer isn't in the document.
Every vector search filters on both `fileId` and `userId`, so users can only ever retrieve their own documents.

## Quality — measured, not assumed

A 30-question evaluation (facts, multi-part, paraphrases, questions not in the document, summaries) on a 12-page research paper:

| Version | Correct | Made-up answers |
|---|---|---|
| Retrieval v1 | 17 / 30 | 0 |
| Tuned | **20 / 30** | **0** |

Every question whose answer is not in the document was refused.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Security + JWT (jjwt 0.12), Spring AI 2.0.1 |
| Database | PostgreSQL + pgvector (HNSW index, cosine distance), Hibernate |
| LLM | Groq — `openai/gpt-oss-120b` (reports, Q&A), `qwen/qwen3.8-27b` (vision) |
| Embeddings | all-MiniLM-L6-v2 run locally with ONNX Runtime + HuggingFace tokenizer (no external embedding API) |
| Documents | PDFBox 3.0.7, Apache POI 5.3, OpenCSV 5.9, openhtmltopdf (PDF export) |
| Frontend | React 19, Vite, React Router, Axios |
| DevOps | Docker (multi-stage, non-root), Docker Compose, Swagger / springdoc |
| Hosting (free) | Vercel (frontend), Render (backend container), Neon (Postgres + pgvector) |

## Run locally

**Prerequisites:** Docker Desktop, Node 20+, a free [Groq API key](https://console.groq.com), and the model files `model.onnx` + `tokenizer.json` from [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) in a local folder.

1. Create a `.env` file in the project root (git-ignored):
   ```
   DB_PASSWORD=choose-a-password
   JWT_SECRET=a-long-random-base64-string
   GROQ_API_KEY=your-groq-key
   ```
2. Build and start backend + database:
   ```
   docker build --build-context models=<path-to-model-folder> -t docuinsight-backend .
   docker compose up -d
   ```
   Without `--build-context`, the image downloads the model files from Hugging Face during the build.
3. Start the frontend:
   ```
   cd frontend
   npm install
   npm run dev
   ```
4. Open http://localhost:5173 — API docs at http://localhost:8080/swagger-ui.html

### Configuration (environment variables)

| Variable | Required | Default |
|---|---|---|
| `DB_URL` | no | `jdbc:postgresql://localhost:5433/docuInsight_db` |
| `DB_USERNAME` / `DB_PASSWORD` | password yes | `postgres` |
| `JWT_SECRET` | **yes** | — |
| `GROQ_API_KEY` | **yes** | — |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` |
| `EMBEDDING_MODEL_DIR` | no | local model folder |
| `REPORTS_PER_DAY` / `QUESTIONS_PER_DAY` | no | `5` / `30` |
| `PORT` | no | `8080` |

Secrets have no defaults: the app refuses to start if one is missing.

## API overview

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` |
| Files | `POST /api/files/upload`, `GET /api/files/my-files`, `POST /api/files/{fileId}/extract` |
| Reports | `POST /api/reports/generate/{fileId}`, `GET /api/reports/my-reports`, `GET /api/reports/{reportId}`, `GET /api/reports/file/{fileId}`, `DELETE /api/reports/{reportId}`, `GET /api/reports/{reportId}/export` |
| Q&A | `POST /api/reports/{reportId}/ask` |
| Compare | `POST /api/multi-reports/generate`, `GET /api/multi-reports/...` |
| User | `GET/PUT /api/users/profile`, `PUT /api/users/changePassword`, `GET /api/users/usage` |

Full, interactive list: `/swagger-ui.html`.

## Known limits

- **Memory:** the backend (Spring + local embedding model) uses about **430–470 MiB**; Render's free tier allows 512 MB, so heavy extraction can be killed there. Tuned with a fixed heap, SerialGC, C1-only JIT, capped metaspace/code cache, ONNX arena off, and embedding in batches of 8.
- **Cold start:** the free backend sleeps after 15 minutes; waking takes ~1–2 minutes on 0.1 CPU.
- **LLM quota:** one shared free Groq key ≈ 8K tokens/minute — roughly one report per minute across all users. Vision is limited to 5 text-poor pages per file; failed image calls are skipped, not fatal.
- **Uploads** are stored on the container disk (not persistent on the free tier); extracted text lives in the database.
- **Ranking:** pure vector search can still miss exact numbers in dense documents.
- **Reports** on very large documents use the first 30,000 characters.

## Scaling roadmap

1. **Queue AI work** — reports and ingestion as background jobs (status `PENDING` → UI polls), so provider limits become waiting time instead of errors.
2. **Global daily budget** next to per-user limits; **plans** (free / paid) mapped to quotas — the paid tier pays for a paid LLM tier on the same org key.
3. **Object storage** (S3 / R2) for uploads → stateless servers → several instances behind a load balancer (JWT is already stateless).
4. **Hybrid search** (PostgreSQL full-text + vector) and reports built from retrieved chunks instead of the first 30k characters.
5. **Load testing** (k6) and monitoring to find the next bottleneck.

## Project structure

```
src/main/java/com/docuinsight/docuinsight/
  config/       OpenAPI (Swagger) setup
  controller/   REST controllers + global exception handler
  embedding/    Local ONNX embedding model, self-check
  model/        Entities and DTOs
  security/     JWT filter, Spring Security config, CORS
  service/      Extraction, chunking, ingestion, retrieval, LLM, reports, quotas
frontend/src/   React pages, components, API client
Dockerfile, docker-compose.yml
```