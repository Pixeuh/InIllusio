param(
    [ValidateRange(1, 3600)]
    [int]$EmulatorTimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'

try {
    $projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
    Set-Location -LiteralPath $projectRoot

    # Prefer an existing Java 17 JAVA_HOME, then search Microsoft's installed JDKs.
    $javaCandidates = @()
    if ($env:JAVA_HOME) {
        $javaCandidates += (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }
    $microsoftRoot = 'C:\Program Files\Microsoft'
    if (Test-Path -LiteralPath $microsoftRoot) {
        $javaCandidates += Get-ChildItem -LiteralPath $microsoftRoot -Directory -Filter 'jdk-17*' |
            ForEach-Object { Join-Path $_.FullName 'bin\java.exe' }
    }

    $javaExe = $null
    foreach ($candidate in ($javaCandidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) { continue }
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $versionText = (& $candidate -version 2>&1 | Out-String)
        }
        finally {
            $ErrorActionPreference = $savedPreference
        }
        if ($versionText -match 'version "17\.') {
            $javaExe = $candidate
            break
        }
    }
    if (-not $javaExe) {
        throw 'Java 17 introuvable. Vérifiez C:\Program Files\Microsoft\jdk-17* ou JAVA_HOME.'
    }
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $javaExe)
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    Write-Host "Java 17 détecté : $javaExe"

    $gradleCache = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.13-bin'
    $gradle = Get-ChildItem -LiteralPath $gradleCache -Filter 'gradle.bat' -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq 'bin' } |
        Select-Object -First 1 -ExpandProperty FullName
    if (-not $gradle) {
        throw "Gradle 8.13 introuvable dans le cache attendu : $gradleCache"
    }
    Write-Host "Gradle 8.13 détecté : $gradle"

    $sdkCandidates = @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)
    if ($env:LOCALAPPDATA) {
        $sdkCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    }
    $localProperties = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) {
            $sdkPath = ($sdkLine -replace '^sdk\.dir=', '') -replace '\\\\', '\'
            $sdkCandidates += $sdkPath
        }
    }
    $sdk = $sdkCandidates |
        Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe') -PathType Leaf) } |
        Select-Object -First 1
    if (-not $sdk) {
        throw 'Android SDK introuvable (adb.exe absent de platform-tools).'
    }
    $env:ANDROID_SDK_ROOT = $sdk
    $env:ANDROID_HOME = $sdk
    $adb = Join-Path $sdk 'platform-tools\adb.exe'
    Write-Host "Android SDK détecté : $sdk"

    $avdName = 'Medium_Phone_API_37.0'
    $emulatorExe = Join-Path $sdk 'emulator\emulator.exe'
    $startupTimer = [System.Diagnostics.Stopwatch]::StartNew()
    $emulatorSerial = $null
    $lastAdbState = 'inconnu'
    $lastBootState = 'non vérifié'
    $lastMessage = ''
    $lastStatusSeconds = -15
    $launchAttempted = $false

    while ($startupTimer.Elapsed.TotalSeconds -lt $EmulatorTimeoutSeconds) {
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $deviceLines = & $adb devices 2>&1
            $adbDevicesExitCode = $LASTEXITCODE
        }
        finally {
            $ErrorActionPreference = $savedPreference
        }
        if ($adbDevicesExitCode -ne 0) {
            $lastAdbState = "erreur adb devices (code $adbDevicesExitCode)"
            $deviceLines = @()
        }

        $emulatorRows = @($deviceLines | Where-Object { $_ -match '^emulator-\S+\s+\S+' })
        $deviceRow = $emulatorRows | Where-Object { $_ -match '^emulator-\S+\s+device(?:\s|$)' } | Select-Object -First 1
        $offlineRows = @($emulatorRows | Where-Object { $_ -match '^emulator-\S+\s+offline(?:\s|$)' })
        if ($deviceRow) {
            $emulatorSerial = ([regex]::Match($deviceRow, '^(emulator-\S+)')).Groups[1].Value
            $lastAdbState = "$emulatorSerial device"
            $savedPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $bootCompleted = & $adb -s $emulatorSerial shell getprop sys.boot_completed 2>$null
                $bootExitCode = $LASTEXITCODE
            }
            finally {
                $ErrorActionPreference = $savedPreference
            }
            $lastBootState = if ($bootExitCode -eq 0) { ($bootCompleted | Out-String).Trim() } else { "erreur adb shell (code $bootExitCode)" }
            if ($lastBootState -eq '1') { break }
            $statusMessage = "ADB: $lastAdbState; sys.boot_completed=$lastBootState"
        }
        elseif ($offlineRows.Count -gt 0) {
            $lastAdbState = (($offlineRows | ForEach-Object { $_.Trim() }) -join ', ')
            $lastBootState = 'indisponible tant que ADB est offline'
            $statusMessage = "Émulateur détecté mais offline dans ADB ($lastAdbState)."
        }
        elseif ($adbDevicesExitCode -ne 0) {
            $lastBootState = 'non vérifié'
            $statusMessage = "ADB n'a pas pu lister les appareils (code $adbDevicesExitCode)."
        }
        else {
            $lastAdbState = if ($emulatorRows.Count -eq 0) { 'aucun émulateur dans ADB' } else { (($emulatorRows | ForEach-Object { $_.Trim() }) -join ', ') }
            $lastBootState = 'non vérifié'
            $statusMessage = "Attente de la connexion ADB (état: $lastAdbState)."
        }

        $runningEmulator = @(Get-Process -Name 'emulator', 'qemu-system-*' -ErrorAction SilentlyContinue | Select-Object -First 1)
        if (-not $launchAttempted -and $adbDevicesExitCode -eq 0 -and $emulatorRows.Count -eq 0 -and $runningEmulator.Count -eq 0) {
            if (-not (Test-Path -LiteralPath $emulatorExe -PathType Leaf)) {
                throw "Émulateur Android introuvable : $emulatorExe"
            }
            Write-Host "Démarrage de l'AVD $avdName avec -no-snapshot-load -gpu software -noaudio..."
            try {
                Start-Process -FilePath $emulatorExe -ArgumentList @('-avd', $avdName, '-no-snapshot-load', '-gpu', 'software', '-noaudio') -WindowStyle Normal | Out-Null
                $launchAttempted = $true
            }
            catch {
                throw "Impossible de démarrer l'AVD $avdName : $($_.Exception.Message)"
            }
        }

        if ($statusMessage -ne $lastMessage -or $startupTimer.Elapsed.TotalSeconds - $lastStatusSeconds -ge 15) {
            Write-Host $statusMessage
            $lastMessage = $statusMessage
            $lastStatusSeconds = [int]$startupTimer.Elapsed.TotalSeconds
        }
        Start-Sleep -Seconds 3
    }

    if (-not $emulatorSerial -or $lastBootState -ne '1') {
        $runningEmulator = @(Get-Process -Name 'emulator', 'qemu-system-*' -ErrorAction SilentlyContinue | Select-Object -First 1)
        $processState = if ($runningEmulator.Count -gt 0) { 'présent' } else { 'absent' }
        $reason = if ($lastAdbState -match 'offline') { "ADB voit un émulateur offline; il peut être bloqué ou ADB n’a pas fini de s’y connecter." } elseif ($lastAdbState -eq 'aucun émulateur dans ADB' -and $processState -eq 'présent') { "Le processus existe, mais aucun appareil émulateur ne s’est connecté à ADB." } elseif ($lastBootState -ne '1') { "ADB est connecté, mais Android n’a pas confirmé la fin de son démarrage." } else { "ADB n’a pas pu fournir un état exploitable." }
        throw "Délai dépassé après $EmulatorTimeoutSeconds secondes pour l’AVD $avdName. État ADB: $lastAdbState; processus émulateur: $processState; sys.boot_completed: $lastBootState. $reason Aucun second émulateur n’a été lancé. Vérifiez la fenêtre de l’émulateur et la sortie de 'adb devices'; arrêtez-le manuellement après confirmation si nécessaire."
    }
    Write-Host "Android est prêt sur $emulatorSerial (ADB: device, sys.boot_completed=1)."
    Write-Host 'Compilation et installation hors ligne de In Illusio...'
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $gradleOutput = & $gradle --offline :app:installDebug 2>&1
        $gradleExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    $gradleOutput | ForEach-Object { Write-Host $_ }
    if ($gradleExitCode -ne 0) {
        if (($gradleOutput | Out-String) -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
            throw "Installation refusée : INSTALL_FAILED_UPDATE_INCOMPATIBLE indique que la version déjà installée a une signature différente. Ses données sont préservées. Désinstallez-la manuellement uniquement si vous acceptez de perdre ses données, puis relancez cette tâche."
        }
        throw "Échec de Gradle (code $gradleExitCode). L'application ne sera pas lancée."
    }

    Write-Host 'Lancement de In Illusio...'
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $adb -s $emulatorSerial shell am start -n 'fr.inillusio/.MainActivity'
        $launchExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($launchExitCode -ne 0) {
        throw "Impossible de lancer In Illusio sur $emulatorSerial (code $launchExitCode)."
    }
    Write-Host 'In Illusio est installé et lancé.'
}
catch {
    Write-Error "run-android : $($_.Exception.Message)"
    exit 1
}
