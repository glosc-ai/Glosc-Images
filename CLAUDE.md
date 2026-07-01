# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

GloscAI Images — a native Android AI image-generation app (Kotlin + Android **Views**, no Jetpack Compose). Single `app` module. All UI is built programmatically in code; there are no XML layouts. Targets the Glosc AI One API (`https://one.gloscai.com/`), an OpenAI-compatible endpoint.

## Build & test

The host has no system Java — use the JBR bundled with Android Studio for every Gradle invocation:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug                  # build debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest              # JVM unit tests (Robolectric + MockWebServer)
./gradlew connectedAndroidTest           # instrumented tests (needs a device/emulator)
./gradlew testDebugUnitTest --tests "com.glosc.images.data.api.OpenAiImageGenerationClientTest"   # single test class
./gradlew testDebugUnitTest --tests "*.OpenAiImageGenerationClientTest.generateDownloadsUrlImagesWhenBase64IsMissing"  # single test
```

Screen-size regression tests run on Gradle managed devices (defined in `app/build.gradle.kts` under `testOptions.managedDevices`): the `screenSizeRegression` group spans `compactPhoneApi35` (Pixel 2) and `expandedTabletApi35` (Pixel Tablet). These need SDK 35 AOSP ATD system images and an AVD; they are slow — prefer plain `testDebugUnitTest` for logic changes.

`local.properties` (gitignored) holds the Android SDK path. Unit tests run with `isIncludeAndroidResources = true` and Robolectric (`@Config(sdk = [35])`).

## Architecture

Layered single-module app (`com.glosc.images`), with manual DI — no Hilt/Dagger. `GloscImagesApp` (the `Application`) is the composition root: it constructs the single `AppRepository` wired to Room, file storage, `ApiKeyStore`, the image client, and the update client. `MainViewModel` obtains it by casting `application` to `GloscImagesApp`.

```
ui/        MainViewModel (StateFlow + UiState) and MainActivity (imperative UI)
domain/    Plain data models + enums + thin use cases
data/      repository, api (Retrofit), db (Room), storage, update (GitHub releases)
core/      security (ApiKeyStore), common (UiState/AppException), ui (Design DSL)
```

### UI rendering model — important

`MainActivity` is one large class that builds the entire view tree **imperatively**. Every screen (`renderOnboarding`, `renderStudio`, `renderDetail`, `renderSettings`) reconstructs the view hierarchy from scratch by calling `render()`, which `removeAllViews()` and re-adds. `render()` is re-invoked on every collected `StateFlow` update (screen, images, tasks, providers, messages, operation/chat/settings/update state). Local UI state (e.g. `promptValue`, `selectedSize`, `settingsKey`) lives as `MainActivity` fields and survives across renders.

The UI is assembled from a DSL of extension functions in `core/ui/Design.kt`: `column`, `row`, `addSpaced` (which reads the container's `tag` as a gap and inserts spacers), `card`, `label`, `title`, `bodyText`, `mono`, `input`, `chip`, `primaryButton`/`ghostButton`/`dangerButton`, `roundedBg`, `dashedBg`, `loadAsset` (Glide), `artPlaceholder`. Colors and text sizes live as constants in the `Design` object. When adding UI, follow this DSL rather than introducing XML or Compose.

i18n is inline via `tr(en, zh)`; language preference is persisted in the `ui_preferences` SharedPreferences (`language` key: `auto`/`en`/`zh`). `Auto` follows `resources.configuration.locales`.

### Onboarding gate

On launch `MainViewModel.init` calls `repository.bootstrap()` then sets the screen to `Generate` only if `repository.isInitialized()` is true, otherwise `Onboarding`. `isInitialized()` requires the active provider to have a saved API key **and** at least one image model. The onboarding flow (save key → fetch models → pick default → "Start Creating") is the only way past this screen. `bootstrap()` also seeds a default `Glosc AI` provider and sample placeholder images on first run.

### Model discovery & filtering

`OpenAiImageGenerationClient.test()` calls `/v1/models` and keeps only models whose `categories` field contains an "image" token (the `containsImageCategory` helper walks primitives/arrays/objects). `AppRepository` additionally:

- Hard-blocks `knownIncompatibleImageModels` (e.g. `alibaba/qwen-image-2.0`, `google/gemini-*`, `gpt-image-2`) — filtered out before persistence and rejected if explicitly requested.
- Prefers models in `preferredImageModelFallbacks` when choosing a default.
- Stores image models as a comma-joined string in `api_providers.imageModels` (migration 1→2 added this column).

### Image generation

Two paths in `OpenAiImageGenerationClient.generate()`:
- No source images → `POST /v1/images/generations` (JSON body, `n` count, `output_format: png`).
- With source images (up to 16) → multipart `POST /v1/images/edits` with `image[]` parts.
Responses may return `b64_json` or `url`; URLs are downloaded and base64-encoded. Errors are parsed for a provider `error.message` and surfaced via `AppException`. `baseUrl` is normalized to end with `/v1/` (see `normalizedBaseUrl()`).

Each generation creates a `GenerationTask` row (Running → Success/Failed), and each returned image becomes an `ImageAssetEntity` saved via `ImageFileStorage` to `filesDir/images/<source>/<yyyy/MM/dd>/<uuid>.png`. Chat generation and edits funnel through `generateImage()` too.

### API key security

`ApiKeyStore` (Android Keystore, AES/GCM/NoPadding) stores keys per-provider under alias `glosc.images.<providerId>`, with the IV kept in the `secure_api_keys` SharedPreferences. Keys are referenced in Room only by `apiKeyAlias`, never by value, and are never logged. `save()` skips values containing `••` (masked placeholders from the UI).

### In-app updates

`GitHubReleaseUpdateClient` queries `https://api.github.com/repos/glosc-ai/Glosc-Images/releases/latest`, picks the best `.apk` asset (preferring `release` > `universal` > `debug`, then size), compares versions numerically, downloads to `filesDir/updates/`, and triggers a system install via `FileProvider` (`${applicationId}.fileprovider`) requiring `REQUEST_INSTALL_PACKAGES` (gated by `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`). Schema dir is `app/schemas/`; `exportSchema = true`.

## Release & signing

`applicationId` is `com.gloscai.Images` (note: differs from the code namespace `com.glosc.images` — the FileProvider authority and package queries use the applicationId). Version comes from Gradle properties `-PversionName` / `-PversionCode` (default `0.1.0` / `1`).

Release signing is enabled only when **all four** env vars are present: `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` (the key password falls back to the store password if unset). `minifyEnabled` is currently false; ProGuard rules only keep `data.api.**` for Retrofit/Gson.

CI (`.github/workflows/release.yml`) runs on a published GitHub release: it sets `versionName` from the tag (stripped `v`), `versionCode` from `GITHUB_RUN_NUMBER`, decodes a base64 keystore from secrets, runs `testDebugUnitTest assembleRelease bundleRelease`, and uploads `glosc-images-<version>-{signed|unsigned}.{apk,aab}` to the release.
