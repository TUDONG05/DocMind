# Document indexing progress
Goal: show total documents, indexed/processing/failed counts and truthful per-document embedding progress in document management.
Approved context: user requests progress percent and total; implementation starts indexing after upload, adds explicit prepare/retry for existing files.
Reuse one DocumentIndexer for upload, retry and chat, serialize document preparation with coroutine Mutex/Semaphore, keep final index and INDEXED status atomic in Room.
Persist terminal/interrupted markers in existing processingStatus (no schema change); transient percent derives from successful batch chunk counts. Recreated Activity keeps ViewModel; stale running markers after process restart show interrupted, never fake 100%.
- [x] Tests RED: progress math/status, shared indexing progress/atomicity/retry and cancellation.
- [x] Extract shared indexer; route chat and uploads through it, store status and ownership-safe writes.
- [x] UI total/counts/progress/retry, Vietnamese/English, upload notice.
- [x] Build, app unit/instrumentation tests, lint and review: 45 unit tests passed / 1 skipped; 22 emulator instrumentation tests passed. Latest APK deployed. Reviewer-found upload/logout issue fixed: cancel tracked upload jobs, check cancellation during copy, clean incomplete uploads.
- Manual visual inspection could not be completed because the emulator switched to Chrome during interaction; no visual-pass claim.
No backend API changes, no secret access, no commits/pushes.
