# Meme-ji — Agent Instructions

## Build & Run

```bash
./gradlew assembleRelease           # unsigned release APK
./gradlew assembleDebug             # debug APK
```

APK output: `app/build/outputs/apk/<variant>/`.

Signing (optional, via env or `gradle.properties`):
`SIGNING_STORE_FILE`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

Tests run via the default instrumentation runner (`AndroidJUnitRunner`):
```bash
./gradlew app:connectedCheck        # on a device/emulator
```
No unit tests beyond the generated placeholder (JUnit 4 + Espresso).

## Architecture

- **Single module** (`:app`), package `com.elejar.memeji`.
- **MVVM**: `MemeViewModel` (extends `AndroidViewModel`) with `LiveData`/`MediatorLiveData`. No Room — all meme data is fetched from the network and cached in-memory by `MemeRepository`.
- **ViewBinding** enabled (`dataBinding = false`).
- **Navigation**: Jetpack Navigation component with Safe Args. 5 destinations defined in `res/navigation/nav_graph.xml`:
  - `homeFragment`, `categoriesFragment`, `moreFragment` (bottom nav tabs)
  - `categoryMemesFragment` (passes `categoryName` arg)
  - `settingsFragment`
- **Networking**: Retrofit + Gson + OkHttp. `ApiService` points at `https://raw.githubusercontent.com/ele-jar/meme-database/main/`.
- **Image loading**: Glide (via `kapt`).
- **Download**: `MediaStore.Downloads` on API 29+, `WRITE_EXTERNAL_STORAGE` fallback for < Q.
- **Share**: `FileProvider` (authority `${applicationId}.provider`).

## Key Build Details

- Kotlin 2.0.0, Gradle 8.9, AGP 8.5.0, compileSdk/targetSdk 34, minSdk 24.
- Version catalog: `gradle/libs.versions.toml`.
- ProGuard enabled for release (`proguard-rules.pro`).
- `kotlin-parcelize` and `kotlin-kapt` plugins applied.
- No local.properties or API keys needed (data is public GitHub raw JSON).

## Cutie Mode

When enabled, memes tagged `"18+"` are hidden from display, sharing, and download. The tag constant is `MemeRepository.SENSITIVE_TAG`.

## Style Notes

- Fragments communicate through the shared `MemeViewModel` (scoped to `MainActivity`).
- Layouts use `<include>`, `merge`, and `SwipeRefreshLayout` patterns. Check existing fragments for conventions.
- `strings.xml` is the single source of truth for user-facing text — avoid hardcoded strings.
