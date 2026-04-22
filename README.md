# OmniRAG

**OmniRAG** is a domain-aware Retrieval-Augmented Generation (RAG) engine built with Java 21 and Spring Boot. Upload documents in a wide variety of formats, ask questions in natural language, and receive grounded answers with source citations — streamed token-by-token or returned in a single response.

> Built by **Rui Song**, **Nguyen Ha**, and **Siyuan Liang** · Northeastern University MSCS · Spring 2026

[![Java 21](https://img.shields.io/badge/Java-21-blue?logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3.2](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Build-Maven-red?logo=apachemaven)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Table of Contents

- [OmniRAG](#omnirag)
  - [Table of Contents](#table-of-contents)
  - [Features](#features)
  - [Architecture](#architecture)
  - [Tech Stack](#tech-stack)
  - [Supported Formats \& Domains](#supported-formats--domains)
  - [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Installation](#installation)
    - [Configuration](#configuration)
    - [Running the Server](#running-the-server)
  - [API Reference](#api-reference)
    - [`POST /api/upload`](#post-apiupload)
    - [`POST /api/query`](#post-apiquery)
    - [`GET /api/stream`](#get-apistream)
    - [`GET /api/status`](#get-apistatus)
    - [`DELETE /api/reset`](#delete-apireset)
    - [`DELETE /api/conversation/{id}`](#delete-apiconversationid)
    - [`POST /api/eval/run`](#post-apievalrun)
  - [Pipeline Deep Dive](#pipeline-deep-dive)
    - [Index Flow](#index-flow)
    - [Query Flow](#query-flow)
    - [Key Design Decisions](#key-design-decisions)
  - [Evaluation](#evaluation)
  - [Project Structure](#project-structure)
  - [Running Tests](#running-tests)
  - [Development History](#development-history)
  - [Roadmap](#roadmap)
  - [Team](#team)
  - [License](#license)

---

## Features

- **Multi-format document ingestion** — PDF, DOCX, Markdown, plain text, HTML, CSV, JSON, SRT/VTT subtitles, FDX screenplays
- **Domain-aware parsing** — dedicated adapters and LLM personas for film scripts, legal documents, subtitles, storyboards, and technical docs
- **Persistent vector store** — SQLite-backed embedding storage that survives server restarts
- **LLM re-ranking** — two-stage retrieval: cosine similarity search narrows to top-10, GPT re-ranks to top-5 before generation
- **Streaming responses** — Server-Sent Events (SSE) for real-time token-by-token output
- **Multi-turn conversations** — LRU-cached session history (up to 100 sessions) for natural follow-up questions
- **Batch embedding with caching** — cache-aware batching reduces redundant OpenAI API calls
- **Built-in evaluation pipeline** — measures retrieval hit rate and answer accuracy against a configurable test set
- **Optional API key security** — header-based auth (`X-API-Key`) controlled by a single environment variable

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                           REST API Layer                             │
│              RagController                EvalController             │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        RagOrchestrator                               │
│           (central coordinator — routes index & query flows)         │
└──────┬──────────────────────┬──────────────────────┬─────────────────┘
       │                      │                      │
       ▼                      ▼                      ▼
┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────────┐
│  DocumentAdapter│  │   VectorStore   │  │       ChatService         │
│   (interface)   │  │   (interface)   │  │  (generate + stream)      │
│  BaseDocument   │  ├─────────────────┤  ├──────────────────────────┤
│    Adapter      │  │ SQLiteVector    │  │  gpt-4o-mini (OpenAI)    │
└────────┬────────┘  │   Store ✓ prod │  │  conversation history     │
         │           │ InMemoryVector  │  │  (LRU, max 100 sessions)  │
         │           │   Store (dev)   │  └──────────────────────────┘
         │           └───────┬─────────┘
         │                   │ cosine similarity search
         │                   ▼
         │           ┌───────────────┐
         │           │  LlmReranker  │  (top-10 → top-5 via GPT)
         │           └───────────────┘
         │
         ├── domain/
         │     ├── film/
         │     │     ├── ScriptAdapter       (.fdx .docx .md  → scene chunks)
         │     │     │     ├── ScriptSplitter       (INT./EXT. boundary split)
         │     │     │     └── ScriptTextCleaner     (normalise whitespace)
         │     │     ├── SubtitleAdapter     (.srt .vtt → cue-block chunks)
         │     │     └── StoryboardAdapter   (.txt → SHOT-pattern chunks)
         │     │
         │     ├── general/
         │     │     └── GeneralAdapter      (.pdf .txt → fixed-size chunks)
         │     │           ├── TextSplitter          (overlap-aware splitting)
         │     │           └── TextCleaner           (strip noise)
         │     │
         │     └── legal/
         │           └── RentalLawAdapter    (.pdf .docx → section chunks)
         │                 ├── LegalSplitter         (§-number boundary split)
         │                 └── LegalTextCleaner      (normalise legalese)
         │
         └── format/
               ├── MarkdownAdapter   (.md  → heading-aware chunks)
               ├── HtmlAdapter       (.html → tag-stripped section chunks)
               ├── CsvAdapter        (.csv → row-batch chunks)
               └── JsonAdapter       (.json → element/key-value chunks)

Index flow:  upload → DocumentAdapter (parse+chunk) → EmbeddingService (batch+cache)
                    → VectorStore (persist)
Query flow:  question → VectorStore (top-10) → LlmReranker (top-5)
                      → domain inference (majority vote) → ChatService → answer + citations
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Embeddings | OpenAI `text-embedding-3-small` (1536-dim) |
| Generation | OpenAI `gpt-4o-mini` |
| Vector Store | SQLite via `sqlite-jdbc 3.45.3.0` |
| PDF Parsing | Apache PDFBox 3.0.2 |
| DOCX Parsing | Apache POI 5.2.5 |
| HTML Parsing | Jsoup 1.17.2 |
| HTTP Client | OkHttp3 4.12.0 |
| JSON | Gson 2.10.1 |
| Build | Maven 3.9+ |
| Tests | JUnit 5 |

---

## Supported Formats & Domains

| File Format | Domain | Chunking Strategy |
|---|---|---|
| `.pdf` | General, Rental Law | Fixed-size (1000 chars) or section-boundary splitting |
| `.docx` | Film Script, Rental Law, General | Scene-boundary (`INT.`/`EXT.`), section-number, or paragraph |
| `.md` | Film Script, General, Markdown | Scene-boundary or heading-aware (`#`, `##`, …) |
| `.txt` | General, Storyboard | Fixed-size or `SHOT N` pattern splitting |
| `.fdx` | Film Script | Plain-text extraction with scene-boundary splitting |
| `.srt` / `.vtt` | Subtitle | Cue-block splitting (timecode boundaries) |
| `.html` / `.htm` | HTML | Tag-stripped; `h1`–`h6` section awareness |
| `.csv` | CSV | Row-batch splitting (each row becomes a chunk) |
| `.json` | JSON | Array per-element or object key-value flattening |

**Domain system prompts** — each domain activates a tailored LLM persona at query time:

| Domain | Persona |
|---|---|
| `film script` | Screenplay analyst — cites scene headings and page numbers |
| `rental law` | California tenant-rights legal assistant — cites statute and section, advises in numbered steps |
| `subtitle` | Media content analyst — cites timestamp and speaker |
| `storyboard` | Visual storytelling expert — cites panel numbers and scene headings |
| `technical` | Technical documentation assistant — precise terminology, bullet-point steps |
| `general` | General document analyst — cites source and page |

Domain is inferred automatically at query time via **majority vote** over the top-5 retrieved chunks, so mixed-domain corpora work transparently without any extra configuration.

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- An [OpenAI API key](https://platform.openai.com/api-keys)

### Installation

```bash
git clone https://github.com/johnha912/rag-core-engine.git
cd rag-core-engine
mvn clean install -DskipTests
```

### Configuration

All sensitive values are read from environment variables — nothing is hardcoded.

| Environment Variable | Required | Description |
|---|---|---|
| `OPENAI_API_KEY` | **Yes** | OpenAI API key (`sk-…`) |
| `RAG_API_KEY` | No | Enables `X-API-Key` header auth. Leave unset to disable auth in development. |

Optional application-level settings in `src/main/resources/application.properties`:

```properties
server.port=8080
openai.embedding.model=text-embedding-3-small
openai.chat.model=gpt-4o-mini
sqlite.db.path=data/rag-store.db
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

### Running the Server

```bash
export OPENAI_API_KEY=sk-your-key-here

# Option A — Maven plugin
mvn spring-boot:run

# Option B — Executable JAR
mvn clean package -DskipTests
java -jar target/rag-core-engine-1.0-SNAPSHOT.jar
```

The server starts at **http://localhost:8080**.

---

## API Reference

All endpoints live under `/api`. When `RAG_API_KEY` is set, every request must include the header:

```
X-API-Key: <your-key>
```

The `/actuator/health` endpoint is always public (for load-balancer probes).

---

### `POST /api/upload`

Index a document into the vector store.

**Form parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `file` | multipart | Yes | Document to index |
| `domain` | string | No | Content domain (see table above). Defaults to `general`. |

Uploads are **additive** — each call appends chunks to the existing store. Call `DELETE /api/reset` first to start fresh.

**Example**

```bash
curl -X POST http://localhost:8080/api/upload \
  -H "X-API-Key: your-key" \
  -F "file=@/path/to/script.fdx" \
  -F "domain=film script"
```

**Response**

```
Indexed: script.fdx | domain: film script | total chunks in store: 142 | to clear all documents call DELETE /api/reset
```

---

### `POST /api/query`

Answer a question against all indexed documents.

**Form parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `question` | string | Yes | Natural-language question |
| `conversationId` | string | No | Session ID for multi-turn conversations |

**Example**

```bash
curl -X POST http://localhost:8080/api/query \
  -H "X-API-Key: your-key" \
  -d "question=What are the landlord's habitability obligations?" \
  -d "conversationId=session-42"
```

**Response**

```
Under California Civil Code § 1941, a landlord must maintain the rental unit in a habitable
condition at all times. This includes ensuring working plumbing, heating, and structural safety...

Sources: civil-code-1941.pdf (page 3), tenants-guide.pdf (page 7)
```

---

### `GET /api/stream`

Stream the answer token-by-token via Server-Sent Events.

**Query parameters**

| Parameter | Type | Required |
|---|---|---|
| `question` | string | Yes |

Each SSE event carries a Gson-encoded token string. The stream ends with the sentinel token `[DONE]`.

**Example**

```bash
curl -N "http://localhost:8080/api/stream?question=Summarize+the+opening+scene" \
  -H "X-API-Key: your-key" \
  -H "Accept: text/event-stream"
```

---

### `GET /api/status`

Return the current indexing state and total chunk count.

**Example**

```bash
curl http://localhost:8080/api/status -H "X-API-Key: your-key"
# Indexing: false | Chunks stored: 284
```

---

### `DELETE /api/reset`

Clear all indexed chunks from the vector store. Use this before switching to a new document corpus to prevent cross-domain contamination.

```bash
curl -X DELETE http://localhost:8080/api/reset -H "X-API-Key: your-key"
# Vector store cleared. Ready for new documents.
```

---

### `DELETE /api/conversation/{id}`

Clear conversation history for a given session.

```bash
curl -X DELETE http://localhost:8080/api/conversation/session-42 \
  -H "X-API-Key: your-key"
# Conversation 'session-42' cleared.
```

---

### `POST /api/eval/run`

Run the built-in RAG quality evaluation pipeline.

**Form parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `testSet` | string | No | Classpath resource name. Defaults to `eval-questions.json`. |

**Response** (JSON)

```json
{
  "totalQuestions": 10,
  "averageRetrievalHitRate": 0.87,
  "averageAnswerKeywordRate": 0.82,
  "details": [
    {
      "questionId": "q1",
      "question": "What are the landlord's habitability obligations?",
      "retrievalHitRate": 1.0,
      "answerKeywordRate": 0.9,
      "answer": "Under California Civil Code § 1941..."
    }
  ]
}
```

**`GET /api/eval/questions`** — list all questions in a test set without running evaluation.

---

## Pipeline Deep Dive

### Index Flow

```
Upload
  └─► DocumentAdapter.parse()
        ├─ Text extraction (PDFBox / POI / Jsoup / plain-text)
        ├─ Domain-specific chunking (scene, section, cue-block, fixed-size…)
        └─ Metadata stamping (domain, chunkIndex, sceneIndex, sectionNumber…)
  └─► EmbeddingService.embedBatch()
        ├─ Cache check (ConcurrentHashMap) — skips API call for already-seen text
        └─ OpenAI text-embedding-3-small, batches of ≤ 100 texts
  └─► SQLiteVectorStore.store()
        └─ Persisted to data/rag-store.db as binary BLOB columns
```

### Query Flow

```
Question
  └─► SQLiteVectorStore.search(question, k=10)
        └─ Cosine similarity over all stored embeddings → top-10 candidates
  └─► LlmReranker.rerank(question, top10)
        └─ GPT receives chunk previews (200 chars each), returns ranked indices → top-5
  └─► Domain inference
        └─ Majority vote over top-5 chunk "domain" metadata → selects system prompt
  └─► ChatService.ask(question, top5, conversationId, roleInstruction)
        ├─ Builds prompt with chunk context + prior conversation turns
        └─ Calls gpt-4o-mini → returns answer + distinct source citations
```

### Key Design Decisions

**Additive uploads** — files accumulate in the vector store across requests. The `domain` metadata is stamped into every chunk at index time so domain is inferred from content, not from a global variable.

**Two-stage retrieval** — vector search is fast but imprecise; the LLM re-ranker adds a precision pass without retrieving more embeddings.

**Majority-vote domain** — when a user asks a question over a mixed corpus (e.g., both a film script and a legal document indexed), the answer persona is chosen from whichever domain dominates the top-5 results.

**Bounded conversation history** — a `LinkedHashMap`-based LRU cache caps session count at 100. Old sessions are evicted automatically; no background cleanup job is needed.

---

## Evaluation

OmniRAG ships with a built-in evaluation harness. Add questions to `src/main/resources/eval-questions.json`:

```json
[
  {
    "id": "q1",
    "question": "What are the landlord's habitability obligations?",
    "expectedAnswer": "maintain the unit in a habitable condition",
    "expectedKeywords": ["habitable", "Civil Code", "1941"],
    "expectedSourceKeywords": ["civil-code", "tenants-guide"]
  }
]
```

Run evaluation after indexing the relevant documents:

```bash
curl -X POST http://localhost:8080/api/eval/run -H "X-API-Key: your-key"
```

**Metrics:**
- **Retrieval hit rate** — fraction of `expectedSourceKeywords` found in the retrieved chunks' source file names
- **Answer keyword rate** — fraction of `expectedKeywords` present in the generated answer

---

## Project Structure

```
src/
└── main/
    ├── java/com/ragcore/
    │   ├── RagCoreApplication.java         # Spring Boot entry point
    │   ├── config/
    │   │   ├── AppConfig.java              # Bean definitions (ChatService, Reranker, SSE executor)
    │   │   └── ApiKeyFilter.java           # Optional X-API-Key header auth
    │   ├── controller/
    │   │   ├── RagController.java          # /api/* endpoints
    │   │   └── EvalController.java         # /api/eval/* endpoints
    │   ├── service/
    │   │   ├── RagOrchestrator.java        # Central pipeline coordinator
    │   │   ├── ChatService.java            # OpenAI chat, streaming, conversation history
    │   │   ├── EmbeddingServiceImpl.java   # OpenAI embeddings with batch caching
    │   │   ├── EvalService.java            # Evaluation metrics
    │   │   ├── vector/
    │   │   │   ├── SQLiteVectorStore.java  # Persistent production store
    │   │   │   └── InMemoryVectorStore.java# Volatile store (dev / testing)
    │   │   └── rerank/
    │   │       └── LlmReranker.java        # GPT-based second-pass re-ranking
    │   ├── adapter/
    │   │   ├── BaseDocumentAdapter.java    # Abstract base — raw text extraction from PDF/DOCX/MD/TXT/FDX; subclasses add chunking
    │   │   ├── domain/
    │   │   │   ├── film/                   # ScriptAdapter, SubtitleAdapter, StoryboardAdapter
    │   │   │   ├── general/                # GeneralAdapter
    │   │   │   └── legal/                  # RentalLawAdapter
    │   │   └── format/
    │   │       ├── MarkdownAdapter.java
    │   │       ├── HtmlAdapter.java
    │   │       ├── CsvAdapter.java
    │   │       └── JsonAdapter.java
    │   └── model/
    │       ├── Chunk.java
    │       ├── EvalQuestion.java
    │       ├── EvalResult.java
    │       └── EvalReport.java
    └── resources/
        ├── application.properties
        └── eval-questions.json             # Default evaluation test set
```

---

## Running Tests

```bash
mvn test
```

28 JUnit 5 test classes cover adapters, services, controllers, and data models. Integration tests use the in-memory vector store to avoid SQLite I/O. Key test classes:

| Class | What it tests |
|---|---|
| `RagControllerTest` | Status, query, upload HTTP endpoints |
| `ChatServiceTest` | OpenAI API calls, conversation history |
| `ChatServiceConversationTest` | Multi-turn conversation flow |
| `SQLiteVectorStoreTest` | Persistence, cosine search correctness |
| `LlmRerankerTest` | LLM ranking, fallback to original order |
| `EvalServiceTest` | Retrieval hit rate, keyword matching |
| `EmbeddingServiceTest` | Cache hits, batch API grouping |
| `ScriptAdapterTest` | Scene-boundary chunking, FDX support |
| `RentalLawAdapterTest` | Section splitting, secondary chunking |
| `SubtitleAdapterTest` | SRT / VTT cue-block parsing |

---

## Development History

| Week | Focus | Highlights |
|---|---|---|
| 1 | RAG Core Engine | Chunker, embedder, `InMemoryVectorStore`, basic retrieval pipeline |
| 2 | Domain Adapters + Format Adapters | `ScriptAdapter`, `RentalLawAdapter`, `GeneralAdapter`; `HtmlAdapter`, `MarkdownAdapter`, `CsvAdapter`, `JsonAdapter`; upgraded to `gpt-4o-mini`; secondary chunking for long documents; `DELETE /api/reset` |
| 3 | Storage + Conversation + Evaluation | SQLite persistent storage; multi-turn conversation support; RAG evaluation pipeline; `.vtt` subtitle support; SSE streaming endpoint |
| 4 | Presentation + Polish | Batch embedding with cache-aware sub-batching; Gson-encoded SSE tokens; final demo |

---

## Roadmap

- [x] Core RAG pipeline (chunk → embed → retrieve → generate)
- [x] Domain-specific adapters (film, legal, subtitle, storyboard, general)
- [x] Format adapters (Markdown, HTML, CSV, JSON)
- [x] LLM re-ranking (`LlmReranker`)
- [x] `DELETE /api/reset` to prevent cross-domain contamination
- [x] Secondary chunking for long documents
- [x] Persistent storage (SQLite)
- [x] Multi-turn conversation support
- [x] RAG quality evaluation pipeline
- [x] SSE streaming endpoint
- [x] Batch embedding (cache-aware, sub-batched to 100 texts/request)
- [x] Gson-encoded SSE tokens (full control-character safety)
- [ ] Docker / container deployment (Railway / Render)
- [ ] Vector index (FAISS / pgvector) for large corpora
- [ ] Conversation session TTL and automatic eviction
- [ ] User authentication and per-user document namespacing

---

## Team

| Name | Role | Contributions |
|----|----|----|
| **Rui Song** | Architecture + Film + Frontend | System skeleton, `RagOrchestrator`, `RagController`, end-to-end integration; Film adapter suite (`ScriptAdapter`, `StoryboardAdapter`, `SubtitleAdapter`); frontend with SSE streaming |
| **Nguyen Ha** | Ingestion + Format Adapters + Evaluation | Core interfaces, `TextCleaner`, `BasePdfAdapter`, `ChatService`; `HtmlAdapter`, `MarkdownAdapter`, `CsvAdapter`, `JsonAdapter`; multi-turn conversation and evaluation pipeline |
| **Siyuan Liang** | Storage + Legal + Retrieval | `EmbeddingServiceImpl`, `InMemoryVectorStore`; legal suite (`RentalLawAdapter`, `LegalSplitter`); `SQLiteVectorStore`, `LlmReranker` |

---

## License

MIT License · © 2026 OmniRAG Team
