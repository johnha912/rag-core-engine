# RAG Core Engine

A Retrieval-Augmented Generation (RAG) backend built with Java 21 and Spring Boot.
Upload PDF or TXT documents, ask questions in natural language, and get AI-generated answers with source citations.

> Northeastern University · CS5004 Final Project · Spring 2026

---

## What is RAG?

RAG stands for **Retrieval-Augmented Generation**. Instead of relying solely on the AI's memory,
the system first *retrieves* relevant passages from your own documents, then passes those passages
to the AI so it can generate a *grounded* answer based on your actual content.

Think of it like an open-book exam: the AI looks up the answer in your documents rather than guessing.

---

## Team

| Member | Days | Responsibilities |
|---|---|---|
| Nguyen "John" Ha | Day 1 | Text cleaning, PDF parsing, ChatService |
| Rui "Snow" Song | Day 2 & Day 6 | REST API, orchestrator, final wiring |
| Siyuan "Ash" Liang | Day 3–5 | Vector embeddings, cosine similarity search |

---

## Architecture

```
User uploads a file
        │
        ▼
  RagController          ← REST API layer (Snow)
        │
        ▼
  RagOrchestrator        ← Pipeline coordinator (Snow)
        │
   ┌────┴────────────────────┐
   ▼                         ▼
BasePdfAdapter          EmbeddingServiceImpl  ──► OpenAI Embeddings API
(John: parse text)      (Ash: text → vector)      (text-embedding-ada-002)
   │                         │
   └──────────┬──────────────┘
              ▼
      InMemoryVectorStore   ← (Ash) store chunks + cosine similarity search
              │
  User asks a question
              │
              ▼
      EmbeddingServiceImpl  (Ash: embed the question)
              │
              ▼
      InMemoryVectorStore   (Ash: find top-5 most similar chunks)
              │
              ▼
       ChatService  ──────► OpenAI Chat API (gpt-3.5-turbo)
       (John: built, Snow: wired in Day 6)
              │
              ▼
      Answer + Sources
```

---

## Ash's Modules

### `EmbeddingServiceImpl`
Calls the OpenAI `text-embedding-ada-002` API to convert text into a 1536-dimensional float vector.
Texts about similar topics produce vectors that point in similar directions, enabling semantic search.
Results are cached in a `ConcurrentHashMap` so identical inputs never trigger a second API call.

### `InMemoryVectorStore`
Stores document chunks and their embedding vectors in a thread-safe `CopyOnWriteArrayList`.
At query time, the question is embedded and compared against every stored chunk using
**cosine similarity**, implemented manually without any external math library.

### `AppConfig`
A Spring `@Configuration` class that registers `ChatService` as a bean.
`ChatService` requires an API key at construction time, so Spring cannot instantiate it
automatically via component scanning — this class provides the explicit factory method.

### Cosine Similarity

Two vectors are similar if they point in the same direction, regardless of magnitude.
Cosine similarity measures the cosine of the angle between them:

- **1.0** — identical direction (highly relevant)
- **0.0** — perpendicular (unrelated)
- **−1.0** — opposite direction (opposite meaning)

Formula: `cos(θ) = (A · B) / (|A| × |B|)`

---

## Prerequisites

- Java 21+
- Maven 3.6+
- An OpenAI API key

---

## Setup

Set your OpenAI API key as an environment variable before starting the app:

```bash
export OPENAI_API_KEY=sk-your-key-here
```

---

## Running the App

```bash
mvn spring-boot:run
```

The server starts on port 8080 by default.

---

## API Endpoints

### Upload a document
```bash
curl -X POST http://localhost:8080/api/upload \
     -F "file=@your-document.pdf"
```

### Ask a question
```bash
curl -X POST http://localhost:8080/api/query \
     -H "Content-Type: application/json" \
     -d '{"question": "What is this document about?"}'
```

### Check system status
```bash
curl http://localhost:8080/api/status
```

---

## Running Tests

```bash
mvn test
```

All tests use mocks — no real OpenAI API key is needed to run them.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Build | Maven |
| PDF parsing | Apache PDFBox 3.0.2 |
| HTTP client | OkHttp 4.12.0 |
| JSON | Gson 2.10.1 |
| Testing | JUnit 5 + Mockito |
| AI | OpenAI API (text-embedding-ada-002 + gpt-3.5-turbo) |

---

## License

MIT License — © 2026 Ash Liang, Snow Song, John Ha — Northeastern University CS5004
