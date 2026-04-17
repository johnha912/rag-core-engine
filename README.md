# Script Insight RAG

A modular Retrieval-Augmented Generation (RAG) system built with **Spring Boot / Java 21**. Upload any document, ask natural-language questions, and get grounded answers with source citations — powered by OpenAI embeddings and GPT-4o-mini.

> Built by **Rui (Snow)**, **John**, and **Ash** · Northeastern University MSCS-Align · Spring 2026

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Domain Adapters](#domain-adapters)
- [Configuration](#configuration)
- [Development History](#development-history)
- [Roadmap](#roadmap)
- [Team](#team)

---

## Overview

Script Insight RAG lets users upload documents and ask natural-language questions about them. The system chunks the document, embeds the chunks using OpenAI's `text-embedding-3-small` model, retrieves the most semantically relevant chunks, re-ranks them with an LLM call, and passes the top results to GPT-4o-mini to generate a grounded, cited answer.

**Key features:**

- Upload and query any text document via REST API
- Domain-specific adapters for film scripts, legal documents, subtitles, storyboards, and general text
- Format adapters for Markdown, HTML, CSV, and JSON files
- LLM-powered re-ranking for improved retrieval quality
- Secondary chunking for long documents
- Persistent storage via SQLite (survives restarts)
- Multi-turn conversation support with per-session history
- RAG quality evaluation pipeline (retrieval hit rate + answer keyword rate)
- Server-Sent Events (SSE) streaming endpoint
- `DELETE /api/reset` to clear state between sessions

---

## Architecture

```
┌──────────────────────────────────────────────┐
│            HTTP Client / Browser             │
└──────────────────────┬───────────────────────┘
                       │
┌──────────────────────▼───────────────────────┐
│          Spring Boot REST Layer              │
│   /api/upload  /api/query  /api/stream       │
│   /api/reset   /api/status /api/eval         │
└──────────────────────┬───────────────────────┘
                       │
          ┌────────────▼────────────┐
          │      RagOrchestrator    │
          │  index flow / query flow│
          └────────────┬────────────┘
                       │
       ┌───────────────┼───────────────┐
       ▼               ▼               ▼
  DocumentAdapter  VectorStore     ChatService
  (parse + chunk)  (embed + store  (GPT-4o-mini)
                    + search)
                       │
                  LlmReranker
                  (re-rank top-10
                   → keep top-5)
```

**Index flow:** `upload → DocumentAdapter (parse + chunk) → VectorStore (embed + store)`

**Query flow:** `question → VectorStore (search top-10) → LlmReranker (re-rank → top-5) → ChatService (generate) → answer + sources`

---

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+
- OpenAI API key

### Clone & Build

```bash
git clone https://github.com/your-org/script-insight-rag.git
cd script-insight-rag
mvn clean install
```

### Set Environment Variables

```bash
export OPENAI_API_KEY=sk-...

# Optional: enable API key authentication
export RAG_API_KEY=your-secret-key
```

### Run

```bash
mvn spring-boot:run
```

The server starts at `http://localhost:8080`.

---

## API Reference

### POST /api/upload

Index a document into the vector store. Uploads are additive — multiple files can be queried together. Call `DELETE /api/reset` first to start fresh.

**Request:** `multipart/form-data`

| Parameter | Type   | Required | Description                                          |
|-----------|--------|----------|------------------------------------------------------|
| `file`    | file   | yes      | The document to upload                               |
| `domain`  | string | no       | Content domain. Options: `film script`, `rental law`, `subtitle`, `storyboard`, `technical`, `general` (default) |

**Response:** `200 OK` — plain text confirming the file name, domain, and total chunk count.

```
Indexed: screenplay.pdf | domain: film script | total chunks in store: 84 | to clear all documents call DELETE /api/reset
```

---

### POST /api/query

Ask a question against the indexed documents.

**Request:** `application/x-www-form-urlencoded`

| Parameter        | Type   | Required | Description                                      |
|------------------|--------|----------|--------------------------------------------------|
| `question`       | string | yes      | The question to ask                              |
| `conversationId` | string | no       | Session ID for multi-turn conversation support   |

**Response:** `200 OK` — plain text answer followed by source citations.

```
The second act opens with INT. HOSPITAL CORRIDOR - DAY, where ...

Sources: screenplay.pdf (Scene 12), screenplay.pdf (Scene 15)
```

---

### GET /api/stream

Stream a response token-by-token via Server-Sent Events.

**Query parameter:** `question` (required)

Each SSE event carries a single token as a JSON-encoded string. The stream ends with a `[DONE]` event.

---

### GET /api/status

Returns the current indexing state and total chunk count.

**Response:** `200 OK`

```
Indexing: false | Chunks stored: 84
```

---

### DELETE /api/reset

Clears all stored chunks from the vector store. Use this to switch to a new document corpus and prevent cross-domain contamination.

**Response:** `200 OK`

```
Vector store cleared. Ready for new documents.
```

---

### DELETE /api/conversation/{id}

Clears the conversation history for a given session ID.

**Response:** `200 OK`

```
Conversation 'session-abc' cleared.
```

---

### POST /api/eval

Runs the RAG quality evaluation pipeline on a built-in test set and returns retrieval hit rate and answer keyword rate metrics.

---

## Domain Adapters

Each domain adapter customises the chunking strategy and the LLM system prompt for its content type. The correct adapter is inferred automatically from the `domain` parameter at upload time.

| Adapter | Domain value | Supported files | Chunk strategy |
|---|---|---|---|
| `ScriptAdapter` | `film script` | `.docx`, `.md`, `.fdx` | Scene-boundary splitting |
| `SubtitleAdapter` | `subtitle` | `.vtt`, `.srt` | Cue-block splitting |
| `StoryboardAdapter` | `storyboard` | `.pdf`, `.docx` | Panel / heading splitting |
| `RentalLawAdapter` | `rental law` | `.pdf`, `.docx` | Section-number splitting + secondary chunking for long sections |
| `GeneralAdapter` | `general` | `.txt`, `.md`, `.docx` | Fixed-size with overlap |
| `MarkdownAdapter` | (format) | `.md` | Heading-aware splitting |
| `HtmlAdapter` | (format) | `.html` | Tag-stripped paragraph splitting |
| `CsvAdapter` | (format) | `.csv` | Row-batch splitting |
| `JsonAdapter` | (format) | `.json` | Object-batch splitting |

At query time, the domain is inferred automatically from the retrieved chunks using a majority vote — no global state is required, and mixed-domain corpora work correctly.

---

## Configuration

All settings live in `src/main/resources/application.properties`. Secrets are read from environment variables and are never hardcoded.

```properties
# Server
server.port=8080

# OpenAI — set via environment variable
openai.api.key=${OPENAI_API_KEY:}
openai.embedding.model=text-embedding-3-small
openai.chat.model=gpt-4o-mini

# API key authentication (leave blank to disable in development)
api.security.key=${RAG_API_KEY:}

# File upload limits
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

# SQLite persistent vector store
sqlite.db.path=data/rag-store.db
```

---

## Development History

| Week | Focus | Highlights |
|---|---|---|
| 1 | RAG Core Engine | Chunker, embedder, `InMemoryVectorStore`, basic retrieval pipeline |
| 2 | Domain Adapters | `ScriptAdapter` (Rui), `RentalLawAdapter`, `GeneralAdapter`; upgraded to `gpt-4o-mini`; secondary chunking for long documents; `/api/reset` endpoint |
| 3 | Storage + Conversation + Evaluation | SQLite persistent storage (Ash); multi-turn conversation support + RAG evaluation pipeline (John); `.vtt` subtitle support; SSE streaming endpoint |
| 4 | Presentation + Polish | Final demo, presentation deck (John), README (Ash), code walkthrough (all) |

---

## Roadmap

- [x] Core RAG pipeline (chunk → embed → retrieve → generate)
- [x] Domain-specific adapters (film, legal, subtitle, storyboard, general)
- [x] Format adapters (Markdown, HTML, CSV, JSON)
- [x] LLM re-ranking (`LlmReranker`)
- [x] `/api/reset` to prevent cross-domain contamination
- [x] Secondary chunking for long documents
- [x] Persistent storage (SQLite)
- [x] Multi-turn conversation support
- [x] RAG quality evaluation pipeline
- [x] SSE streaming endpoint
- [ ] Docker deployment (Railway / Render)
- [ ] Vector index (FAISS) for large corpora
- [ ] Conversation session TTL / eviction policy
- [ ] User authentication

---

## Team

| Name | Role | Contributions |
|---|---|---|
| **Rui (Snow)** | Adapters + Frontend | `ScriptAdapter`, `SubtitleAdapter`, `.vtt` support, HTML frontend, Docker packaging |
| **John** | Conversation + Evaluation | Multi-turn conversation, RAG quality evaluation, presentation deck |
| **Ash** | Storage + Retrieval | SQLite persistent storage, `LlmReranker`, README |

---

## License

MIT License · © 2026 Script Insight RAG Team
