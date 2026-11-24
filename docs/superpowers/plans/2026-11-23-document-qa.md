# Document QA Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Hỏi đáp tài liệu thực tế bằng RAG + Gemini.
**Architecture:** Android extracts and indexes documents in Room, retrieves cosine top-6, Kotlin/Ktor backend embeds and generates grounded structured answers.
**Tech Stack:** Kotlin/Compose/Room, PDFBox Android, Ktor JVM, Gemini REST.
**Spec:** ../specs/2026-11-23-document-qa-design.md

## Global Constraints
- API key server-side only, local backend binds loopback.
- No silent omission of failed documents; no fake responses or fake sources.
- Preserve existing user edits; no commits/pushes requested.

## Review Focus
- Corrupt/empty/oversized input: clear error before indexing.
- Wrong user or deleted conversation: no read/write or late answer.
- Model changes/invalid vectors: rebuild, never mix spaces.
- Failed request/retry: no duplicate user message.
- Unknown source IDs: reject ungrounded response.

### Task 1: Pure RAG primitives
Files: app/.../data/qa/RagMath.kt and app/src/test/.../RagMathTest.kt.
Interfaces: split(text,page): List<TextChunk>; encode/decode FloatArray; retrieve(query,candidates,limit=6).
- [x] Write tests for overlap, vector round-trip, invalid vectors, ranking and model mismatch.
- [x] Run tests RED with stubs.
- [x] Implement bounded chunks and cosine selection; run GREEN.

### Task 2: Kotlin backend
Files: backend/build.gradle.kts, backend/src/main/kotlin/.../{Application,GeminiGateway}.kt, backend/src/test/kotlin/.../BackendTest.kt.
Interfaces: GET /health, POST /embeddings {texts,task} => {model,dimensions,vectors}; POST /answer {question,history,sources} => {answer,sourceIds,grounded}.
- [x] Write route/Gemini fake HTTP tests; watch RED.
- [x] Implement bounds, timeouts, structured output validation, server-side env key.
- [x] Run backend suite GREEN.

### Task 3: Extraction and persistence
Files: app/.../data/qa/{DocumentTextExtractor,QaApiClient,DocumentQaService}.kt; DAOs/repository.
Interfaces: prepare owned conversation, atomically replace compatible embeddings, persist one pending user message and grounded assistant reply.
- [x] Write Room/instrumentation tests for ownership, indexing and retry.
- [x] Run RED, implement IO extraction PDF/DOCX/XLSX/TXT with limits and immutable source IDs.
- [x] Run GREEN.

### Task 4: UI and verification
Files: DocMindViewModel, DocMindApp, ChatDetailScreen, ConversationsScreen, strings.xml (vi/en).
- [x] Replace simulated reply with service; retain request ID on retry, cancel logout/deletion.
- [x] Show progress/error/retry and sources, disable duplicate sends; require documents.
- [x] Build debug, run full unit/backend suites and lint, instrumentation with available device.
- [x] Fresh final review, fix material findings and rerun relevant suites.
- [x] Document startup/configuration and actual verification results; no real Gemini claims without key.

## Execution ledger
- Approved: Kotlin/Ktor backend replaces Node.js.
- Native implementation authorized by user's request to implement and final OK.
- Workspace remains feature/document-qa for Android Studio buffer safety.
- Task 1: RED 4 failed -> GREEN; full app unit suite 41 passed, 1 skipped before review fixes.
- Task 2: routes RED 3 failed -> GREEN; Gemini gateway RED 3 failed -> GREEN (6 tests); input validation follow-up RED 1 failed, fix applied.
- Task 3: Room RED 2 failed -> GREEN; extraction RED 2 failed -> GREEN; service RED 1 failed -> GREEN. Full instrumentation suite before review: 14 passed.
- Final fresh review: retain pre-persistence questions, guard cancelled callbacks, preserve filenames and reject invalid shared-string references.
- Review regression tests observed RED (2 unit + 3 instrumentation) -> GREEN. Final verification: 43 Android unit tests passed, 1 skipped; 8 backend tests passed; 18 instrumentation tests passed on emulator-5554. assembleDebug, lintDebug and backend:installDist successful.
- Actual HTTP client tests use a local loopback server on the emulator; validate vector contract, unknown citation rejection, HTTP error mapping and release HTTP rejection.
- Deployed debug APK and confirmed application launches. No live Gemini request: GEMINI_API_KEY is not configured in the environment.
- git diff --check passed. Existing user .gitignore and IDE changes retained. No commit/push/PR performed.
- Ruling: isolate request lifecycle in QaRequestCoordinator to test cancellation races without Android UI timing. Small extra class; prevents stale jobs unlocking newer requests.
- Ruling: backend remains local and unauthenticated by design; public deployment and server identities are not included.

