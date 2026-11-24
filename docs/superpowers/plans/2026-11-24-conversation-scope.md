# Conversation document scope implementation plan
> Native execution in this session. User approved default all indexed documents and optional custom scope.
**Goal:** New chat opens immediately, all indexed owned files by default; editable persisted custom scope inside chat.
**Architecture:** Room v2 migration adds allDocuments flag and scopeRevision, preserving existing selected links. Repository resolves all scope dynamically from owned INDEXED documents, custom links explicitly. Revision guards answer completion after scope changes. Streaming top-6 retrieval removes ten-file cap for all scope.
**Tech stack:** Kotlin, Room, Compose, coroutines.
## Constraints and review focus
- No destructive migration: v1 accounts/docs/chat history and custom links survive.
- Other users' documents never enter scope; deleting last selected file never broadens to all.
- All includes future indexed files; pending/failed files are excluded.
- All supports >10 files, backend receives only top six sources; bounded candidate memory.
- Scope save disabled while answering; repository revision check protects races; don't reuse old-scope chat history after changing scope.
## Tasks
- [x] Tests RED: dynamic all/selected/ownership/empty/revision, >10 retrieval, migration retains data.
- [x] Room migration + owned repository scope create/update/resolve and reactive conversation details.
- [x] Service top-six retrieval and scope revision guards.
- [x] Immediate create chat, in-chat scope dialog with All default/custom file list, ready-only selection, empty message and scope-saving state.
- [x] Unit + instrumentation + lint/build: 49 unit passed, 1 skipped; 29 emulator tests passed. Migration and scope regressions verified. Review findings fixed and re-reviewed. Latest APK deployed; app currently requires sign-in, so no signed-in visual-flow pass claimed. No commit or PR.
