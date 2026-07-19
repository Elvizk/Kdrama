param(
    [switch]$NoCopy
)

$rootDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradlew = Join-Path $rootDir "gradlew.bat"
$logFile = Join-Path $rootDir "build.log"

if (-not (Test-Path $gradlew)) {
    Write-Host "ERROR: gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 1
}

function Log {
    param([string]$Message, [string]$Color = "White")
    $ts = Get-Date -Format "HH:mm:ss"
    $line = "[$ts] $Message"
    Write-Host $line -ForegroundColor $Color
    Add-Content -Path $logFile -Value $line
}

# Separator between builds
Add-Content -Path $logFile -Value "`n========================================"

$buildFile = Join-Path $rootDir "KdramaV5\build.gradle.kts"
$versionLine = Get-Content $buildFile | Select-String "^version = "
$version = if ($versionLine) { ($versionLine -split "=")[1].Trim() } else { "?" }

Log "BUILD START v$version" "Cyan"
Log "gradlew.bat KdramaV5:make --console=plain" "DarkGray"

$startTime = Get-Date

try {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c cd /d `"$rootDir`" && gradlew.bat KdramaV5:make --console=plain 2>&1"
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $proc = [System.Diagnostics.Process]::Start($psi)

    while (-not $proc.HasExited) {
        $line = $proc.StandardOutput.ReadLine()
        if ($null -ne $line) {
            $clean = $line -replace '\x1b\[[0-9;]*[a-zA-Z]', ''
            Write-Host $clean
            Add-Content -Path $logFile -Value $clean
        }
    }

    # Drain remaining stdout
    while ($null -ne ($line = $proc.StandardOutput.ReadLine())) {
        $clean = $line -replace '\x1b\[[0-9;]*[a-zA-Z]', ''
        Write-Host $clean
        Add-Content -Path $logFile -Value $clean
    }

    # Capture stderr
    $stderr = $proc.StandardError.ReadToEnd()
    if ($stderr) {
        Add-Content -Path $logFile -Value "`n--- STDERR ---"
        Add-Content -Path $logFile -Value $stderr
    }

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
}

exit $exitCode
