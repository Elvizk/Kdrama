# KdramaV5 — CloudStream Plugin

## Build & Deploy

- **Build**: `./gradlew KdramaV5:make` → `KdramaV5/build/KdramaV5.cs3`
- **Version**: `KdramaV5/build.gradle.kts` (`version = X`)
- **Entry point**: `CineStream.kt` registers `CineTmdbProvider()` + all extractors
- **Two branches**: `master` (source) + `builds` (distribution)
- **builds branch**: `KdramaV5.cs3`, `plugins.json`, `repo.json`
- **CI** (`.github/workflows/build.yml`): builds on push to master, force-pushes to `builds`
- **plugins.json** `fileHash` must match the `.cs3` SHA-256; update on every rebuild

## Local Build Setup

- `local.properties` (gitignored) required:
  ```
  sdk.dir=C:/Users/Elvizk_XML/AppData/Local/Android/Sdk
  TMDB_KEY=831c1cc36c02ab393ea30b9b0a967211
  ```
- Lost on `git checkout`; recreate after every branch switch
- Windows: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"`

## Architecture

- **One provider**: `CineTmdbProvider` (`name = "Kdrama TMDB"`)
- **Categories**: `mainPageOf(...)` in `CineTmdbProvider.kt` — `discover/tv` + `with_original_language=ko|zh` + `with_genres=18`
- **API key**: `BuildConfig.TMDB_KEY` via `buildConfigField`; never hardcoded
- **JSON parsing**: use `tryParseJson<T>()` (Jackson `@JsonProperty`); NOT `parsed<T>()`
- **Data classes**: `@param:JsonProperty("name")` annotations

## Current Categories

6: Korean Drama - Recent/Top Rated, Chinese Drama - Recent/Top Rated, Korean/Chinese Movies

## Gotchas

- `.gitignore` with `**/build` must exist on `builds` branch or build artifacts get committed
- CI uses `git commit --amend` + `--force` on `builds` — exactly one commit
- `TvType.Anime` and `TvType.Torrent` intentionally excluded
- Empty TMDB_KEY → silent API failure
