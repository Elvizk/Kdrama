# KdramaV5 â€” GuÃ­a del Repositorio

Archivo COMMITEADO en GitHub. Punto de referencia para open

---

## Archivos Locales (no en GitHub)

| Archivo | Proposito |
|---------|-----------|
| AGENTS.md | Instrucciones de contexto para opencode. NO ELIMINAR. |
| BUG_LOG.md | Checklist pre-bump + lecciones de errores pasados |
| PENDING_FEATURES.md | Mejoras pendientes priorizadas |
| SYNC_STATE.md | Tracking de upstream sync con SaurabhKaperwan/CSX |
| build.ps1 | Script local para build + backup + deploy |


## Branches

| Branch | Proposito | Contenido |
|--------|-----------|-----------|
| master | Codigo fuente | .kt, build.gradle.kts, GUIDE.md |
| builds | Distribucion | KdramaV5.cs3, plugins.json, repo.json |

### .gitignore
Ambos branches comparten el MISMO .gitignore:
- Ignoran: build.ps1, AGENTS.md, BUG_LOG.md, PENDING_FEATURES.md, SYNC_STATE.md, backups/, local.properties, .gradle, **/build



## Backups
Cada build genera un backup en backups/KdramaV5_v{version}.cs3
El directorio backups/ existe solo localmente (gitignored).



---

## Backup de AGENTS.md

Si AGENTS.md se pierde localmente, recrearlo desde este bloque:

# KdramaV5 Ã¢â‚¬â€ CloudStream Plugin

## Build & Deploy

- **Build**: `.\build.ps1` (recomendado, muestra progreso + escribe `build.log`) o `./gradlew KdramaV5:make`
- **Build flags**: `.\build.ps1 -NoCopy` para no copiar a backups; `.\build.ps1 -SkipDeploy` para saltar deploy a builds branch
- **Build sin script**: `./gradlew KdramaV5:make --console=plain` (output limpio)
- **Log**: `build.log` en project root Ã¢â‚¬â€ cada build agrega timestamp + resultado. Revisar `build.gradle.tmp.log` si hay errores de Gradle. Build.ps1 ya no satura stdout con output de Gradle (lo escribe directo a archivo), solo muestra progreso compacto + resumen final.
- **Version**: `KdramaV5/build.gradle.kts` (`version = X`)
- **Entry point**: `CineStream.kt` registers `CineTmdbProvider()` + all extractors
- **Two branches**: `master` (source) + `builds` (distribution)
- **builds branch**: `KdramaV5.cs3`, `plugins.json`, `repo.json`
- **CI** (`.github/workflows/build.yml`): builds on push to master, force-pushes to `builds`
- **plugins.json** `fileHash` must match the `.cs3` SHA-256; update on every rebuild
- **ALWAYS bump version** before building: `version = X+1` in `build.gradle.kts`. CloudStream auto-detects version changes and re-downloads the plugin without manual reinstall
- **Auto commit+push**: Al terminar cada cambio significativo, hacer commit + push automÃƒÂ¡ticamente sin pedir confirmaciÃƒÂ³n
- **Pre-bump**: Antes de hacer bump + push, leer `BUG_LOG.md` (checklist + lecciones de errores pasados)
- **build.ps1 auto-deploys**: After build, build.ps1 automatically switches to builds branch, copies .cs3, updates plugins.json, and pushes. CI also deploys on push to master (belt + suspenders)
- **Verify CI triggered**: After `git push origin master`, run `gh run list --limit 1 --json event,createdAt,headSha` Ã¢â‚¬â€ should show `push` event with matching `headSha` within 2 min. If CI didn't trigger: `gh workflow run Build --ref master` (manual fallback)
- **gh default repo**: Must be set to `Elvizk/Kdrama` (not upstream `SaurabhKaperwan/CSX`). Verify with `gh repo set-default --view`. If unset or wrong: `gh repo set-default Elvizk/Kdrama`. Push trigger is broken on our fork Ã¢â‚¬â€ always trigger CI manually after push.

## CI Monitoring

- **Nunca usar `gh run watch`** Ã¢â‚¬â€ genera output masivo cada 3s que satura el contexto
- **En su lugar:** esperar ~3 min tras push, luego consultar una sola vez: `gh run view <id> --json status,conclusion`
- **Trigger manual (fallback):** `gh workflow run Build --ref master` Ã¢â€ â€™ imprime URL del run
- **Verificar resultado:** `gh run view <id> --json conclusion` Ã¢â€ â€™ `"success"` o `"failure"`
- **Root cause del bug v478**: El push trigger de CI nunca funcionÃƒÂ³ en este fork (todos los runs eran `workflow_dispatch`). Siempre verificar que CI se activÃƒÂ³ con `gh run list --limit 1` tras cada push a master

## Local Build Setup

- `local.properties` (gitignored) required:
  ```
  sdk.dir=C:/Users/Elvizk_XML/AppData/Local/Android/Sdk
  TMDB_KEY=<from_github_secrets>
  TRAKT_CLIENT_ID=<from_github_secrets>
  MDBLIST_API_KEY=<from mdblist.com/preferences/#api>
  ```
- **NOT** deleted by git checkout (it's gitignored). **NEVER manually delete** `local.properties`. It contains secrets (TMDB_KEY, TRAKT_CLIENT_ID, MDBLIST_API_KEY) Ã¢â‚¬â€ if deleted, must ask user for them again
- Windows: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"`
- **Gradle Daemon cold start**: Primera vez despuÃƒÂ©s de un rato: ~15min (daemon + dependencies + compilation). Builds subsecuentes con daemon warm: ~2-5min. La optimizaciÃƒÂ³n de `gradle.properties` (parallel, config-cache, -Xmx4096m) reduce tiempos. `build.ps1` ya incluye un check automÃƒÂ¡tico: si el daemon estÃƒÂ¡ frÃƒÂ­o, muestra advertencia antes de empezar.
- **gradle.properties optimizations** (v479+): `-Xmx4096m` (2GBÃ¢â€ â€™4GB), `parallel=true`, `configuration-cache=true`, `enableJetifier=false`

## Architecture

- **One provider**: `CineTmdbProvider` (`name = "Kdrama TMDB"`)
- **Categories**: `mainPageOf(...)` in `CineTmdbProvider.kt`
  - MDbList-based (3): "Latest Kdrama" (snoak), "Korean Movies" (an-kah), "Chinese Movies" (thedeterminist8)
  - TMDB-based (4): Korean Drama Recent/Top Rated, Chinese Drama Recent/Top Rated
- **MDbList**: used for 3 curated lists (replaced Trakt in v478)
  - `"mdblist/snoak/latest-kdrama-shows/items/show"` Ã¢â€ â€™ "Latest Kdrama"
  - `"mdblist/an-kah/popular-korean-movies/items/movie"` Ã¢â€ â€™ "Korean Movies"
  - `"mdblist/thedeterminist8/chinese-movies/items/movie"` Ã¢â€ â€™ "Chinese Movies" (filtered)
  - Base URL: `https://api.mdblist.com/lists/{username}/{listname}/items/{mediatype}`
  - Auth: `apikey` query parameter (`BuildConfig.MDBLIST_API_KEY`)
  - Pagination: `?limit=50&offset=X&append_to_response=poster` Ã¢â‚¬â€ single request returns items + poster URLs
  - Rate limit: 1,000 req/day (Free tier). Check `X-RateLimit-Remaining` header. HTTP 429 handled gracefully (returns empty list, no crash)
  - Rate limit headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
  - MDbList items include `adult` field (Int: 0 or 1) used for Chinese Movies filter
- **Trakt**: still available via `BuildConfig.TRAKT_CLIENT_ID` but no longer used for home page lists
- **TMDB**: used for discover, search, detail, episodes, recommendations
- **API keys**: `BuildConfig.TMDB_KEY`, `BuildConfig.TRAKT_CLIENT_ID`, `BuildConfig.MDBLIST_API_KEY` via `buildConfigField`; never hardcoded
- **JSON parsing**: use `tryParseJson<T>()` (Jackson `@JsonProperty`); NOT `parsed<T>()`
- **Data classes**: `@param:JsonProperty("name")` annotations

## Current Categories (v478)

7: Latest Kdrama (MDbList), Korean Drama - Recent/Top Rated, Chinese Drama - Recent/Top Rated, Korean Movies (MDbList), Chinese Movies (MDbList, filtered)

## Gotchas

- `.gitignore` with `**/build` must exist on `builds` branch or build artifacts get committed
- CI uses `git commit --amend` + `--force` on `builds` Ã¢â‚¬â€ exactly one commit
- `TvType.Anime` and `TvType.Torrent` intentionally excluded
- Empty TMDB_KEY Ã¢â€ â€™ silent API failure
- **Jackson**: nullable fields in data classes must use `Boolean?` not `Boolean` to handle `"field": null` from JSON APIs (CloudStream pre-release changed null handling)
- **Siempre consultar antes de implementar**: Antes de cualquier cambio en el cÃƒÂ³digo, pasar a modo plan y presentar el plan al usuario. No implementar sin aprobaciÃƒÂ³n explÃƒÂ­cita. Si se estÃƒÂ¡ en modo build, indicar "cambiemos a modo plan" antes de proceder.
- Para upstream sync: existe `SYNC_STATE.md` (local) con tracking del ultimo SHA sincronizado y decisiones de providers
- Para mejoras futuras: existe `PENDING_FEATURES.md` (local) con lista de features pendientes

## Local Backups

- `backups/` directory at project root stores a copy of every build `.cs3`:
  ```
  backups/KdramaV5_v471.cs3
  backups/KdramaV5_v472.cs3
  backups/KdramaV5_v473.cs3
  backups/KdramaV5_v474.cs3
  backups/KdramaV5_v475.cs3
  backups/KdramaV5_v476.cs3
  backups/KdramaV5_v477.cs3
  backups/KdramaV5_v478.cs3
  ```
- Auto-copy on every build: `Copy-Item build/KdramaV5.cs3 backups/KdramaV5_v<version>.cs3`
- `backups/` is in `.gitignore` Ã¢â‚¬â€ never pushed to GitHub

## Content Filters

- **Chinese Movies (v476+):** Filtro de contenido adulto activo usando MDbList `adult` field
  - Detecta contenido +18 mediante el campo `adult` (Int: 0 o 1) del API de MDbList
  - Aplica solo a la lista `thedeterminist8/chinese-movies`
  - Nota: Antes usaba TMDB `adult` flag (Boolean) Ã¢â‚¬â€ migrado a MDbList `adult` field (Int) en v478

## Pendiente: Fix "Skip loading(10)" (futuro)

- `runLimitedAsync` en `CineStreamUtils.kt:~562` no tiene `withTimeout` Ã¢â€ â€™ un provider lento bloquea todo el pipeline porque `awaitAll()` espera a que TODOS terminen
- **SoluciÃƒÂ³n:** envolver `task()` con `withTimeout(15_000L)` dentro de `runLimitedAsync`
- **Complemento:** reducir timeouts excesivos en `CineStreamExtractors.kt`:
  - `invokeBollywood` ~3241: `300000` (5 min) Ã¢â€ â€™ `15000` (15s)
  - Stremio globals ~3459/3519/3550: `100000L` (100s) Ã¢â€ â€™ `15000L` (15s)
  - `invokeStremioStreams` ~819: `50000L` (50s) Ã¢â€ â€™ `15000L` (15s)
  - `invokeDahmerMovies` ~866: `60L` (60s) Ã¢â€ â€™ `15L` (15s)
- **Importante:** timeout es por provider individual, no por todo el proceso de bÃƒÂºsqueda. Providers rÃƒÂ¡pidos no se ven afectados
- **Retomar:** cuando el usuario indique que el stuck sigue siendo problema

## Bug Fix v477 (documentado)

- **Error:** `invokeCtgMovies` en `CineStreamExtractors.kt:5212` fallÃƒÂ³ con "Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'String?'"
- **Causa:** `val playUrl = link.hlsUrl ?: link.url` es `String?` (ambos campos son nullable en `CtgLink`), pero se usÃƒÂ³ sin null check
- **Fix:** Cambiar a `val playUrl = link.hlsUrl ?: link.url ?: return@forEach` y `"CTGMovies ${link.source ?: "default"}"` para manejar nulls
- **LecciÃƒÂ³n:** Al copiar cÃƒÂ³digo del upstream, siempre verificar nullability de campos en data classes nuevas

## Pendiente: MigraciÃƒÂ³n a repo privado (standby)

- Crear repo nuevo `Elvizk/KdramaV5` como privado independiente
- Push de `master` y `builds`
- Cambiar referencias a `SaurabhKaperwan/CSX` por `Elvizk/KdramaV5` en `build.gradle.kts` y `KdramaV5/build.gradle.kts`
- Configurar secrets del nuevo repo
- Verificar CI y probar antes de eliminar el fork viejo
- **Retomar:** cuando el usuario lo indique

## Historial de Cambios

- **v478 (2026-07-18):** MigraciÃƒÂ³n Trakt Ã¢â€ â€™ MDbList API para las 3 listas curadas. Ventajas: 1 request por pÃƒÂ¡gina (vs 51 con Trakt), posters incluidos en respuesta, rate limit validation con HTTP 429 handling. Added MDbList API key field in settings.
- **v477 (2026-07-14):** Sync upstream: +Vidup y CtgMovies providers, -16 providers anime puros
- **v476 (2026-07-10):** Adult content filter for Chinese Movies (TMDB adult flag)
- **v475 (2026-07-10):** Chinese Movies cambiado de TMDB discover a Trakt list (thedeterminist8/chinese-movies)
- **v474 (2026-07-10):** Korean Movies cambiado de TMDB discover a Trakt list (an-kah/popular-korean-movies)
```
