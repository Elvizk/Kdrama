# KdramaV5 - Guia del Repositorio

Archivo COMMITEADO en GitHub. Punto de referencia para opencode y desarrolladores.

---

## Archivos Locales (no en GitHub)

| Archivo | Proposito |
|---------|-----------|
| AGENTS.md | Instrucciones de contexto para opencode. NO ELIMINAR. |
| BUG_LOG.md | Checklist pre-bump + lecciones de errores pasados |
| PENDING_FEATURES.md | Mejoras pendientes priorizadas |
| SYNC_STATE.md | Tracking de upstream sync con SaurabhKaperwan/CSX |
| build.ps1 | Script local para build + backup + deploy |

---

## Branches

| Branch | Proposito | Contenido |
|--------|-----------|-----------|
| master | Codigo fuente | .kt, build.gradle.kts, GUIDE.md |
| builds | Distribucion | KdramaV5.cs3, plugins.json, repo.json |

### .gitignore
Ambos branches comparten el MISMO .gitignore:
Ignoran: build.ps1, AGENTS.md, BUG_LOG.md, PENDING_FEATURES.md, SYNC_STATE.md, backups/, local.properties, .gradle, **/build

---

## Backups
Cada build genera un backup en backups/KdramaV5_v{version}.cs3
El directorio backups/ existe solo localmente (gitignored).

