# KdramaV5 - Guía del Repositorio

Plugin de CloudStream 3 para streaming de dramas coreanos y chinos. Este archivo es COMMITEADO en GitHub (punto de referencia para opencode y desarrolladores). Replica la estructura de `docs/` del proyecto ListasKdrama.

## Arquitectura

- `KdramaV5/src/main/kotlin/com/megix/CineStream.kt` — Punto de entrada (registra provider + extractores)
- `CineTmdbProvider.kt` — Provider principal (`name = "Kdrama TMDB"`)
- `CineStreamExtractors.kt` — Extractores de fuentes de video
- `CineStreamParser.kt` — Data classes + parsing JSON (Jackson `@JsonProperty`)
- `CineStreamUtils.kt` — Helpers (getLanguage, mySubtitleCallback, CF bypass, timeouts)
- `ProviderRegistry.kt` — Registro de providers/extractores
- `settings/` — Settings de CloudStream

### Categorías (v492)
7 categorías: Latest Kdrama (MyDramaList API), Korean Drama - Recent/Top Rated, Chinese Drama - Recent/Top Rated, Korean Movies (MDbList), Chinese Movies (MDbList, filtered).

## Branches

| Branch | Propósito | Contenido |
|--------|-----------|-----------|
| master | Código fuente | .kt, build.gradle.kts, GUIDE.md, docs/ |
| builds | Distribución | KdramaV5.cs3, plugins.json, repo.json |

**CI**: `.github/workflows/build.yml` — build on push a master, force-push a `builds`. Push trigger NO funciona en este fork (lección 4) → siempre trigger manual `gh workflow run Build --ref master`.

## Sistema de Backup

`build.ps1` (local) genera `backups/KdramaV5_v{version}.cs3` en cada build. `backups/` es gitignored.

## Archivos Locales (no en GitHub)

| Archivo | Propósito |
|---------|-----------|
| `AGENTS.md` | Instrucciones de contexto para opencode. NO ELIMINAR. |
| `docs/LECCIONES_APRENDIDAS.md` | Lecciones de errores pasados + checklist pre-bump (migrado de BUG_LOG.md) |
| `docs/MEJORAS_PENDIENTES.md` | Mejoras pendientes priorizadas (migrado de PENDING_FEATURES.md) |
| `SYNC_STATE.md` | Tracking de upstream sync con SaurabhKaperwan/CSX |
| `build.ps1` | Script local para build + backup + deploy |
| `local.properties` | Secrets (TMDB_KEY, TRAKT_CLIENT_ID, MDBLIST_API_KEY). NO borrar. |

## Reglas de Git

- NUNCA usar `git add -A` o `git add .`
- En `builds` branch: agregar SOLO `KdramaV5.cs3 plugins.json` (lección 9)
- Archivos commiteables (master): `KdramaV5/src/`, `KdramaV5/build.gradle.kts`, `gradle.properties`, `GUIDE.md`, `docs/GUIDE.md`, `docs/API.md`, `docs/integration-cloudstream.md`, `.github/`, `.gitignore`, `README.md`
- Archivos locales ignorados: `AGENTS.md`, `docs/LECCIONES_APRENDIDAS.md`, `docs/MEJORAS_PENDIENTES.md`, `SYNC_STATE.md`, `BUG_LOG.md`, `PENDING_FEATURES.md`, `build.ps1`, `build.log`, `local.properties`, `backups/`

## .gitignore (nota)

El `.gitignore` raíz ignora los archivos locales. Al migrar `BUG_LOG.md`→`docs/LECCIONES_APRENDIDAS.md` y `PENDING_FEATURES.md`→`docs/MEJORAS_PENDIENTES.md`, ambos están gitignored como archivos locales (ver sección Archivos Locales).

## Backups
Cada build genera un backup en `backups/KdramaV5_v{version}.cs3`. El directorio `backups/` existe solo localmente (gitignored).
