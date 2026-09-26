# VedaBase Modern — Android

Native Kotlin + Jetpack Compose port of the VedaBase Modern WinUI 3 desktop
app: the same frozen, read-only canonical corpus (`prabhupada_corpus.db`,
29k+ records, FTS5 full-text search), the same personal research data model
(bookmarks, collections, character-precise highlights, notes, reading
history), and the same Supabase PostgREST sync protocol, so an existing
desktop user's cloud data works unchanged from this app.

This project was scaffolded from, and ported line-for-line where it matters,
the desktop app at `C:\VedaBaseModern2` (`VedaBaseModern.Core`) — see the
doc comments throughout for which C# source file each Kotlin file mirrors.

**The corpus database ships in this repo** at
`app/src/main/assets/prabhupada_corpus.db`, via Git LFS (see `.gitattributes`)
since it's ~270MB — over GitHub's 100MB plain-blob limit. Cloning with
`git lfs` support (installed automatically by recent Git for Windows/macOS,
or `git lfs install` once if not) gives you a build-ready app with no
separate download step.

> **Heads up on GitHub LFS bandwidth:** GitHub's free plan includes 1GB/month
> of LFS bandwidth. Since this one file is ~270MB, roughly 3–4 clones/month
> will exhaust that quota account-wide, after which further LFS fetches fail
> until the quota resets or you buy a data pack. If this repo gets cloned a
> lot, consider moving the `.db` to a GitHub Release asset instead (plain
> file download, no LFS bandwidth metering) and pointing
> `CorpusDatabaseAssetInstaller` at a one-time download instead of a bundled
> asset.

## Verified

This project builds and packages end-to-end, and has been exercised at
runtime on an Android emulator (API 34, `emulator-5554`) via ADB — not just
compiled:

```
./gradlew :app:compileDebugKotlin   # Kotlin + Hilt + Room KSP codegen
./gradlew :app:assembleDebug        # full debug APK
./gradlew testDebugUnitTest         # 57 JUnit tests, 100% passing
```

On-device, the following were driven end-to-end and confirmed working:

- **Corpus install & search**: the bundled `prabhupada_corpus.db` extracts
  from assets on first launch, and FTS5 `MATCH` queries ("krishna", "bhakti",
  "duty") return results with correctly highlighted snippets.
- **Reading mode**: verse records (Transliteration/Synonyms/Translation/
  Purports) render correctly for both Bhagavad-gītā and Śrīmad-Bhāgavatam
  records.
- **Bookmarks**: creating a bookmark from Reading mode and seeing it appear
  in the Bookmarks tab.
- **Highlights**: selecting text in a purport/translation block, choosing a
  color from the contextual bottom action bar, and confirming the highlight
  span renders immediately (no screen reload) and survives an app restart —
  including a direct-SQL insert test against `user.db`'s `highlights` table
  (WAL-checkpointed, then read back through `HighlightRenderer`) to confirm
  rendering is correct independent of the creation path.
- **Notes**: creating both scripture-anchored and general notes, editing an
  existing note, soft-deleting one, and filtering by type.

Test environment: Gradle 9.3.1, AGP 8.12.0, JDK 17.

## Before you can run it

1. **Corpus database** — already included (see above); nothing to do as long
   as your clone pulled the LFS content (`git lfs pull` if in doubt).
2. **Devanagari font (optional but recommended).** Drop a
   `noto_sans_devanagari.ttf` (Google Fonts, OFL) into `app/src/main/res/font/`
   and point `DevanagariFontFamily` in `ui/theme/Type.kt` at it. Until then,
   Devanagari renders with the platform's default font, which is usually
   Devanagari-capable but not typographically tuned for it.
3. **Gradle wrapper jar.** `gradle/wrapper/gradle-wrapper.jar` is included so
   `./gradlew` works standalone. If Android Studio ever asks to "regenerate"
   the wrapper, that's safe to accept.
4. **Supabase project** (only needed for cloud sync):
   1. Create a Supabase project, then run the same `supabase_schema.sql` the
      desktop app uses against it (SQL Editor → paste → Run). This creates
      `vb_bookmark_collections`, `vb_bookmarks`, `vb_highlights`, `vb_notes`,
      and `vb_schema_info`, plus Row Level Security policies scoped to
      `auth.uid()`.
   2. In the app, go to Settings → Cloud Sync and enter your project URL
      (`https://<ref>.supabase.co`) and anon/public API key (Project
      Settings → API in the Supabase dashboard). Optionally add an email/
      password if you want RLS-scoped per-user sync rather than the anon
      role.
   3. Tap "Test Connection" before syncing — it distinguishes a bad URL/key
      (HTTP 401/403) from tables not yet created (HTTP 404, with a link back
      to step 1) so a misconfiguration is diagnosable from the UI alone.
   4. Every sync takes a local WAL-checkpointed snapshot of `user.db` into
      `filesDir/backups/` *before* pulling or merging anything remote, so a
      bad merge is always recoverable by restoring that file — see
      `UserDataBackupService`. Only the newest 5 snapshots are kept (oldest
      pruned automatically after each successful one, via `BackupRetention`)
      so this doesn't grow unbounded over time. Pruning failures are
      swallowed and never block the snapshot or the sync itself — deliberate,
      since the whole point of this service is to make sync strictly safer,
      never a new way for it to fail. A 401 mid-sync triggers one
      re-authenticate-and-retry; a 429 or 5xx triggers one fixed
      2-second-delay retry; anything else fails the sync without advancing
      the checkpoint (see `ResearchSyncService.executeWithRecovery`). All
      Supabase HTTP calls are bounded by a 30s request / 15s connect timeout
      (`AppModule`'s `HttpTimeout` plugin) so a dead server can't hang a sync
      indefinitely.

## Why `requery:sqlite-android` is a dependency

Android's OS-provided SQLite build does **not** reliably include the FTS5
extension across devices/versions — it's the single biggest silent-failure
risk for a search feature built on `RecordsFts MATCH ?`. `com.github.requery:
sqlite-android` bundles its own native SQLite compiled with
`-DSQLITE_ENABLE_FTS5`, used as a drop-in replacement for
`android.database.sqlite.SQLiteDatabase` in `CorpusDatabaseAssetInstaller`/
`CorpusRepository` only (the Room-managed `user.db` has no FTS5 dependency,
so it still uses the platform's own SQLite via Room as normal).

## Search snippets are generated in Kotlin, not by FTS5

FTS5's own `snippet()` auxiliary function reproducibly trips
`SQLITE_CORRUPT_VTAB` (error 267) against this corpus's external-content
table under requery's bundled SQLite, regardless of query shape — so it's not
used at all. Instead, `data/corpus/SnippetGenerator.kt` builds the preview
text in pure Kotlin, over the small, already-fetched set of result rows
(bounded by the search page size, never the full 50k+-record corpus):

1. Tokenize the query into plain search terms, dropping FTS5 boolean
   keywords (`AND`/`OR`/`NOT`/`NEAR`) and single-character tokens.
2. Fold IAST diacritics to their plain-ASCII equivalent (`kṛṣṇa` → `krsna`)
   on a 1:1 character basis — never removing or expanding a character — so a
   folded index always lines up with the same offset in the original,
   diacritic-bearing string.
3. Search a prioritized field list (Translation before Purport, matching the
   desktop app's own field priority) for the first token match, and return a
   window of ~50 characters before / ~70 after it, with `...` ellipses where
   the window was clipped and the match itself wrapped in `«...»` sentinels.
4. `ui/common/SnippetText.kt` turns those sentinels into a bolded
   `AnnotatedString` span for display in `SearchScreen`.

Covered by 13 unit tests in `SnippetGeneratorTest.kt` (windowing/ellipsis
placement, diacritic tolerance, field-priority fallback, blank/no-match
handling).

## Architecture map

| Layer | Where |
|---|---|
| Canonical corpus (read-only, FTS5) | `data/corpus/` — `CorpusRepository`, `FtsQueryParser`, `IastSearchHelper`, `ChapterTitleDeriver`, `SnippetGenerator` |
| User research data (Room, soft-delete) | `data/user/` — entities, DAOs, `UserRepository`, `LwwMerge` (pure LWW decision logic) |
| Highlight rendering & offset math | `highlight/HighlightRenderer.kt` |
| Cloud sync (Ktor, PostgREST) | `data/sync/` — `SupabaseSyncProvider`, `ResearchSyncService`, `SyncWorker`, `UserDataBackupService` |
| Settings (DataStore) & credentials (Keystore) | `data/settings/` |
| Screens & ViewModels | `ui/reading`, `ui/library`, `ui/search`, `ui/bookmarks`, `ui/notes`, `ui/settings` |
| Selection UX shared by Reading/Notes | `ui/common/` — `SelectionActionBar` (contextual bottom bar), `NoOpTextToolbar`, `SnippetText` |
| Navigation | `ui/navigation/NavGraph.kt` |
| DI | `data/di/AppModule.kt` (Hilt) |
| Unit tests | `app/src/test/` — `FtsQueryParserTest`, `HighlightRendererTest`, `UserRepositoryLwwTest`, `SnippetGeneratorTest` (57 tests) |

## Known simplifications vs. the desktop app

These were deliberate scope cuts to ship a working, buildable app rather than
partial coverage of everything — flagged here rather than silently dropped:

- **Canonical chapter titles**: Bhagavad-gītā's 18 chapter titles are ported
  in full; Śrīmad-Bhāgavatam and Caitanya-caritāmṛta fall back to a bare
  "Canto C Chapter N" / "Chapter N" instead of the desktop's full named-title
  lookup table (`CanonicalChapterTitles.cs`, ~300 entries) — exactly what the
  desktop app itself falls back to when that table doesn't have an entry.
- **Highlight rendering is content-addressed, not position-addressed**: it
  searches for a highlight's `selectedText` snapshot inside the *displayed*
  text and falls back to `startOffset`/`length` only if that search fails.
  This matches what the desktop app's own WebView reader actually does
  (see `HighlightRenderer.kt`'s doc comment for why) and is what makes a
  highlight created on one client still land correctly when synced to and
  displayed on the other, even though each client's displayed text differs
  slightly (hard-wrap artifacts removed differently, or not at all).
- **Text selection uses a custom bottom action bar, not the OS text
  toolbar**: mounting a `Popup`-based `TextToolbar` over a selected
  `BasicTextField` steals focus back from the field, which the field reads
  as "selection lost" and hides the popup — an infinite show/hide loop.
  `LocalTextToolbar` is overridden with a no-op (`NoOpTextToolbar`) and
  selection state drives a plain `AnimatedVisibility` bottom bar
  (`ui/common/SelectionActionBar.kt`) instead, which eliminates the focus
  race entirely and is a more natural touch target than a floating popup.
- **Bookmark collection reordering** is not drag-and-drop; collections sort
  by a plain `sortOrder` field with no reordering UI yet.
- **Personal research search** (Notes tab in Search) is a plain substring
  match over the bounded, locally-owned Notes table, not FTS5 — appropriate
  given its size versus the 50k+-record corpus.
- **PDF-imported books** (`BookNode.isPdf`/`pdfPath` on the desktop) have no
  PDF viewer counterpart here.
