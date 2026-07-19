param(
    [switch]$NoCopy,
    [switch]$SkipDeploy
)

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradlew = Join-Path $rootDir "gradlew.bat"
$logFile = Join-Path $rootDir "build.log"

if (-not (Test-Path $gradlew)) {
    Write-Output "ERROR: gradlew.bat not found at $gradlew"
    exit 1
}

function Log {
    param([string]$Message, [string]$Color = "White")
    $ts = Get-Date -Format "HH:mm:ss"
    $line = "[$ts] $Message"
    Write-Output $line
    Add-Content -Path $logFile -Value $line
}

# Separator between builds
Add-Content -Path $logFile -Value "`n========================================"

$buildFile = Join-Path $rootDir "KdramaV5\build.gradle.kts"
$versionLine = Get-Content $buildFile | Select-String "^version = "
$version = if ($versionLine) { ($versionLine -split "=")[1].Trim() } else { "?" }

Log "BUILD START v$version" "Cyan"
Log "gradlew.bat KdramaV5:make --console=plain" "DarkGray"

# Daemon check — primer build del día puede tardar ~15min
$daemonStatus = & cmd.exe /c "cd /d `"$rootDir`" && gradlew.bat --status 2>&1" | Select-String "no running daemon"
if ($daemonStatus) {
    Log "WARNING: Gradle daemon cold start — primera build puede tardar ~15 min" "Yellow"
}

$startTime = Get-Date

try {
    $gradleLog = Join-Path $rootDir "build.gradle.tmp.log"
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c cd /d `"$rootDir`" && gradlew.bat KdramaV5:make --console=plain 2>&1"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $proc = [System.Diagnostics.Process]::Start($psi)

    # Show progress in real-time: live task lines + heartbeat if compiling
    $reader = $proc.StandardOutput
    while (-not $proc.HasExited) {
        if ($reader.Peek() -ge 0) {
            $line = $reader.ReadLine()
            $clean = $line -replace '\x1b\[[0-9;]*[a-zA-Z]', ''
            Add-Content -Path $gradleLog -Value $clean
            if ($clean -match '^> Task ') {
                $elapsed = (Get-Date) - $startTime
                $ts = "{0}m {1:D2}s" -f $elapsed.Minutes, $elapsed.Seconds
                Write-Output "[$ts] $clean"
            }
        } else {
            $elapsed = (Get-Date) - $startTime
            $ts = "{0}m {1:D2}s" -f $elapsed.Minutes, $elapsed.Seconds
            Write-Output "`r[$ts] compiling..." -NoNewline
            Start-Sleep -Seconds 5
        }
    }

    # Drain remaining (no heartbeat needed here, already exited)
    while ($null -ne ($line = $reader.ReadLine())) {
        $clean = $line -replace '\x1b\[[0-9;]*[a-zA-Z]', ''
        Add-Content -Path $gradleLog -Value $clean
        if ($clean -match '^> Task ') {
            $elapsed = (Get-Date) - $startTime
            $ts = "{0}m {1:D2}s" -f $elapsed.Minutes, $elapsed.Seconds
            Write-Output "[$ts] $clean"
        }
    }

    # Capture stderr
    $stderr = $proc.StandardError.ReadToEnd()
    if ($stderr) {
        Add-Content -Path $gradleLog -Value "`n--- STDERR ---"
        Add-Content -Path $gradleLog -Value $stderr
    }

    # Append gradle log to build.log
    Add-Content -Path $logFile -Value (Get-Content $gradleLog)
    Remove-Item $gradleLog -Force -ErrorAction SilentlyContinue

    $exitCode = $proc.ExitCode
    $proc.Dispose()
} catch {
    Log "ERROR: $($_.Exception.Message)" "Red"
    exit 1
}

$elapsed = (Get-Date) - $startTime
$totalMins = [math]::Floor($elapsed.TotalMinutes)
$totalSecs = $elapsed.Seconds

# Verify output
$cs3Path = Join-Path $rootDir "KdramaV5\build\KdramaV5.cs3"
$cs3Exists = Test-Path $cs3Path
$status = if ($exitCode -eq 0 -and $cs3Exists) { "SUCCESS" } else { "FAILED" }
$statusColor = if ($status -eq "SUCCESS") { "Green" } else { "Red" }

# DONE marker — always last line
Log "BUILD $status | Exit: $exitCode | Duration: ${totalMins}m ${totalSecs}s | v$version" $statusColor

if ($status -eq "SUCCESS") {
    $fileSize = (Get-Item $cs3Path).Length
    $sizeKB = [math]::Round($fileSize / 1KB, 1)
    $hash = (Get-FileHash $cs3Path -Algorithm SHA256).Hash
    Log "Output: KdramaV5.cs3 ($sizeKB KB) SHA: $hash" "Gray"

    if (-not $NoCopy) {
        $backupDir = Join-Path $rootDir "backups"
        if (-not (Test-Path $backupDir)) {
            New-Item -ItemType Directory -Path $backupDir | Out-Null
        }
        $backupPath = Join-Path $backupDir "KdramaV5_v$version.cs3"
        Copy-Item $cs3Path $backupPath -Force
        Log "Backup: backups\KdramaV5_v$version.cs3" "Yellow"
    }

    if (-not $SkipDeploy) {
        # --- Deploy to builds branch ---
        $tempCs3 = Join-Path $env:TEMP "KdramaV5_v${version}_deploy.cs3"
        Copy-Item $cs3Path $tempCs3 -Force

        $currentBranch = & git -C $rootDir rev-parse --abbrev-ref HEAD
        Log "Deploying to builds branch (current: $currentBranch)" "Cyan"

        # Stash untracked files so checkout doesn't complain
        Push-Location $rootDir
        $stashed = $false
        if ((& git -C $rootDir status --porcelain -u)) {
            & git -C $rootDir stash push --include-untracked -q
            $stashed = $true
        }

        try {
            & git -C $rootDir checkout builds 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Failed to checkout builds branch" }

            Copy-Item $tempCs3 (Join-Path $rootDir "KdramaV5.cs3") -Force

            # Update plugins.json
            $pluginsPath = Join-Path $rootDir "plugins.json"
            if (Test-Path $pluginsPath) {
                $raw = Get-Content $pluginsPath -Raw
                $plugins = $raw | ConvertFrom-Json
                # Ensure we have an array (ConvertFrom-Json unwraps single-element arrays)
                if ($raw -match '^\s*\[') {
                    $plugins = @($plugins)
                } else {
                    $plugins = @($plugins)
                }
                $plugins[0].version = [int]$version
                $plugins[0].fileSize = $fileSize
                $plugins[0].fileHash = "sha256-$($hash.ToLower())"
                # Force array output: wrap manually since ConvertTo-Json in PS5.1 has no -AsArray
                $json = $plugins | ConvertTo-Json -Depth 10
                if ($json -notmatch '^\s*\[') {
                    $json = "[$json]"
                }
                Set-Content $pluginsPath -Value $json -Encoding UTF8
                Log "Updated plugins.json: v$version, $fileSize bytes, sha256-$($hash.ToLower().Substring(0,16))..." "Gray"
            }

            & git -C $rootDir add KdramaV5.cs3 plugins.json
            $commitMsg = "Build $(& git -C $rootDir log --format='%h' origin/master -1)"
            & git -C $rootDir commit --amend -m $commitMsg 2>&1 | Out-Null
            & git -C $rootDir push --force origin builds 2>&1 | Out-Null
            if ($LASTEXITCODE -eq 0) {
                Log "Pushed to builds branch: $commitMsg" "Green"
            } else {
                Log "WARNING: Push to builds branch failed" "Yellow"
            }
        } finally {
            & git -C $rootDir checkout $currentBranch 2>&1 | Out-Null
            if ($stashed) { & git -C $rootDir stash pop -q 2>&1 | Out-Null }
            Pop-Location
        }
        # --- End deploy ---
    }
}

exit $exitCode
