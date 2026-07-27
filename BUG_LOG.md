# Bug Log — Lecciones Aprendidas

> Archivo local NO gitignored. Se lee solo al preparar una nueva versión (ver AGENTS.md: Pre-bump).

## Checklist Pre-bump

Antes de hacer bump + push, verificar cada punto:

- [ ] **plugins.json es arreglo** — `git show origin/builds:plugins.json | Select -First 1` debe empezar con `[`
- [ ] **Tipos bridge normalizados** — Si conectas 2 APIs, los tipos coinciden (ej: MDbList `"show"` → TMDB `"tv"`). Buscar en código: `data.type`, `mediaType`, `type =` en la ruta del bridge
- [ ] **build.ps1 output completo** — Ejecutar y verificar que el resumen final incluya: BUILD SUCCESS, Output, Backup, Pushed
- [ ] **builds branch actualizado** — `git fetch origin builds && git log origin/builds --oneline -1`
- [ ] **CI trigger** — `gh run list --repo Elvizk/Kdrama --limit 1 --json event` debe mostrar `"push"`. Si no, trigger manual: `gh workflow run Build --ref master`
- [ ] **Probar en Cloudstream** — Pull-to-refresh, probar cada categoría, verificar episodios carguen

---

## Lecciones por Error

### 1. plugins.json debe ser arreglo, no objeto
- **Síntoma:** Cloudstream no detecta el plugin tras deploy
- **Causa:** `ConvertFrom-Json` en PS5.1 desenvuelve arreglos de 1 elemento; `ConvertTo-Json` serializa como `{...}`
- **Fix:** Forzar wrapping manual: `if ($json -notmatch '^\[') { $json = "[$json]" }`
- **Regla:** Verificar formato del JSON remoto post-deploy

### 2. Normalizar tipos entre APIs
- **Síntoma:** "Coming soon" en episodios solo en categorías MDbList shows
- **Causa:** MDbList devuelve `mediaType = "show"`, TMDB espera `"tv"` en `/tv/{id}/season/...`
- **Fix:** `val normalizedType = if (mediaType == "movie") "movie" else "tv"`
- **Regla:** Todo valor crudo de API externa usado como parámetro de otra API debe normalizarse explícitamente

### 3. Output post-build invisible
- **Síntoma:** Build exitoso pero tool parece "colgado" — no muestra status, backup ni deploy
- **Causa:** Gradle output masivo satura stdout del tool; `Write-Host` no va a stdout
- **Fix:** Gradle escribe a `build.gradle.tmp.log`; solo dots de progreso en stdout; `Write-Output` en `Log`
- **Regla:** Scripts que lanzan procesos con output masivo deben log a archivo, no saturar stdout

### 4. Push trigger de CI no funciona en forks
- **Síntoma:** Pushes a master no disparan CI automático
- **Causa:** GitHub Actions en forks requiere Settings > Actions > General > "Allow all actions"
- **Workaround:** `gh workflow run Build --ref master` manual tras cada push
- **Regla:** Verificar CI tras primer push de la sesión. Si no es `"push"`, trigger manual

### 5. Deploy manual vs automático a builds branch
- **Síntoma:** Código nuevo en master pero Cloudstream no lo refleja
- **Causa:** Cloudstream lee del branch `builds`, no de `master`
- **Fix:** `build.ps1` auto-deploy. CI también deploya en `workflow_dispatch`.
- **Regla:** Siempre verificar ambos branches: `git log origin/master --oneline -1` + `git log origin/builds --oneline -1`

### 6. Cloudstream cachea datos entre versiones
- **Síntoma:** Fix aplicado pero usuario ve comportamiento viejo
- **Causa:** Cloudstream cachea search responses + show data localmente
- **Workaround:** Pull-to-refresh en categoría; forzar detención de app; reinstalar plugin si persiste
- **Regla:** Tras cambiar formato de datos, informar al usuario que debe refrescar
