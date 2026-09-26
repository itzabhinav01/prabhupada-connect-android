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

This project builds and packages end-to-end as-is:

```
./gradlew :app:compileDebugKotlin   # Kotlin + Hilt + Room KSP codegen
./gradlew :app:assembleDebug        # full debug APK
```

Both succeeded during development (Gradle 9.3.1, AGP 8.12.0, JDK 17). There
is no emulator/device in this environment, so the UI itself has not been
exercised at runtime — only compiled and packaged.

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
4. **Supabase project** (only needed for cloud sync): run the same
   `supabase_schema.sql` the desktop app uses against your Supabase project,
   then connect from Settings → Cloud Sync with your project URL and anon key.

## Why `requery:sqlite-android` is a dependency

Android's OS-provided SQLite build does **not** reliably include the FTS5
extension across devices/versions — it's the single biggest silent-failure
risk for a search feature built on `RecordsFts MATCH ?`. `com.github.requery:
sqlite-android` bundles its own native SQLite compiled with
`-DSQLITE_ENABLE_FTS5`, used as a drop-in replacement for
`android.database.sqlite.SQLiteDatabase` in `CorpusDatabaseAssetInstaller`/
`CorpusRepository` only (the Room-managed `user.db` has no FTS5 dependency,
so it still uses the platform's own SQLite via Room as normal).

## Architecture map

| Layer | Where |
|---|---|
| Canonical corpus (read-only, FTS5) | `data/corpus/` — `CorpusRepository`, `FtsQueryParser`, `IastSearchHelper`, `ChapterTitleDeriver` |
| User research data (Room, soft-delete) | `data/user/` — entities, DAOs, `UserRepository` (includes the LWW sync merge) |
| Highlight rendering & offset math | `highlight/HighlightRenderer.kt` |
| Cloud sync (Ktor, PostgREST) | `data/sync/` — `SupabaseSyncProvider`, `ResearchSyncService`, `SyncWorker` |
| Settings (DataStore) & credentials (Keystore) | `data/settings/` |
| Screens & ViewModels | `ui/reading`, `ui/library`, `ui/search`, `ui/bookmarks`, `ui/notes`, `ui/settings` |
| Navigation | `ui/navigation/NavGraph.kt` |
| DI | `data/di/AppModule.kt` (Hilt) |

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
- **Bookmark collection reordering** is not drag-and-drop; collections sort
  by a plain `sortOrder` field with no reordering UI yet.
- **Personal research search** (Notes tab in Search) is a plain substring
  match over the bounded, locally-owned Notes table, not FTS5 — appropriate
  given its size versus the 50k+-record corpus.
- **PDF-imported books** (`BookNode.isPdf`/`pdfPath` on the desktop) have no
  PDF viewer counterpart here.
