pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // requery:sqlite-android (bundled SQLite compiled with FTS5 - see
        // app/build.gradle.kts for why the OS-provided SQLite can't be
        // trusted to have FTS5 compiled in) is only published via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VedaBaseModern"
include(":app")
