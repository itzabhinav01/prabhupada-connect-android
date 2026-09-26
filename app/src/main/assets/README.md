# Corpus asset

`prabhupada_corpus.db` is the read-only canonical corpus (Bhagavad-gītā,
Śrīmad-Bhāgavatam, Caitanya-caritāmṛta, and more — 29k+ records with an
FTS5 index) checked into this repo via Git LFS (see `.gitattributes` at the
repo root) so cloning the repo gives you a build-ready app with no separate
download step.

It is loaded by `CorpusDatabaseAssetInstaller` on first launch, which copies
it byte-for-byte into the app's private `databases/` directory (SQLite
cannot open a database packaged inside a compressed APK/AAB directly) and
never writes to it afterward. `android:extractNativeLibs`/APK compression is
disabled for `*.db` via `androidResources.noCompress` in
`app/build.gradle.kts`, so the copy is a plain byte stream, not a
decompression pass.

Same file the WinUI desktop app (`C:\VedaBaseModern2`) ships from
`Database/prabhupada_corpus.db` - an independently-versioned data artifact,
not application source code, which is exactly why it's tracked through LFS
rather than as a normal blob.
