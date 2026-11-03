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
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $deviceLines = & $adb devices
        $adbDevicesExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($adbDevicesExitCode -ne 0) {
        throw "ADB n'a pas pu lister les appareils connectés (code $adbDevicesExitCode)."
    }

    $emulatorRows = @($deviceLines | Where-Object { $_ -match '^emulator-\S+\s+\S+' })
    $readySerial = $emulatorRows |
        Where-Object { $_ -match '^emulator-\S+\s+device(?:\s|$)' } |
        ForEach-Object { ([regex]::Match($_, '^(emulator-\S+)')).Groups[1].Value } |
        Select-Object -First 1

    if ($readySerial) {
        $emulatorSerial = $readySerial
        Write-Host "Émulateur déjà connecté, réutilisation : $emulatorSerial"
    }
    else {
        # An emulator can be starting before ADB exposes its serial. In that case,
        # wait for it instead of opening a second emulator window.
        $runningEmulator = @(Get-Process -Name 'emulator', 'qemu-system-*' -ErrorAction SilentlyContinue | Select-Object -First 1)
        if ($emulatorRows.Count -gt 0) {
            Write-Host 'Un émulateur apparaît déjà dans ADB; attente de sa connexion...'
        }
        elseif ($runningEmulator.Count -gt 0) {
            Write-Host 'Un processus émulateur est déjà actif; attente de sa connexion sans en démarrer un autre...'
        }
        else {
            if (-not (Test-Path -LiteralPath $emulatorExe -PathType Leaf)) {
                throw "Émulateur Android introuvable : $emulatorExe"
            }
            Write-Host "Démarrage de l’AVD $avdName dans une fenêtre indépendante..."
            try {
                Start-Process -FilePath $emulatorExe -ArgumentList @('-avd', $avdName) -WindowStyle Normal | Out-Null
            }
            catch {
                throw "Impossible de démarrer l’AVD $avdName : $($_.Exception.Message)"
            }
        }

        Write-Host 'Attente de la connexion ADB et du démarrage complet d Android (maximum 120 secondes)...'
        $emulatorSerial = $null
        while ($startupTimer.Elapsed.TotalSeconds -lt 120) {
            $savedPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $deviceLines = & $adb devices
                $adbDevicesExitCode = $LASTEXITCODE
            }
            finally {
                $ErrorActionPreference = $savedPreference
            }
            if ($adbDevicesExitCode -eq 0) {
                $emulatorSerial = $deviceLines |
                    Where-Object { $_ -match '^emulator-\S+\s+device(?:\s|$)' } |
                    ForEach-Object { ([regex]::Match($_, '^(emulator-\S+)')).Groups[1].Value } |
                    Select-Object -First 1
            }

            if ($emulatorSerial) {
                $savedPreference = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                try {
                    $bootCompleted = & $adb -s $emulatorSerial shell getprop sys.boot_completed 2>$null
                }
                finally {
                    $ErrorActionPreference = $savedPreference
                }
                if (($bootCompleted | Out-String).Trim() -eq '1') { break }
            }

            Start-Sleep -Seconds 3
        }
        if (-not $emulatorSerial -or ($bootCompleted | Out-String).Trim() -ne '1') {
            throw "L’émulateur $avdName n’a pas terminé son démarrage dans le délai de 120 secondes. Vérifiez la fenêtre de l’émulateur et ADB."
        }
        Write-Host "Android est prêt sur $emulatorSerial."
    }

    # ADB can report a connected emulator before Android has completed booting.
    if ($readySerial) {
        Write-Host 'Vérification que le démarrage Android est terminé...'
        $bootCompleted = $null
        while ($startupTimer.Elapsed.TotalSeconds -lt 120) {
            $savedPreference = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                $bootCompleted = & $adb -s $emulatorSerial shell getprop sys.boot_completed 2>$null
            }
            finally {
                $ErrorActionPreference = $savedPreference
            }
            if (($bootCompleted | Out-String).Trim() -eq '1') { break }
            Start-Sleep -Seconds 3
        }
        if (($bootCompleted | Out-String).Trim() -ne '1') {
            throw "Android sur $emulatorSerial n’a pas terminé son démarrage dans le délai de 120 secondes."
        }
        Write-Host "Android est prêt sur $emulatorSerial."
    }

    Write-Host 'Compilation et installation hors ligne de Hors Cadre...'
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $gradle --offline :app:installDebug
        $gradleExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($gradleExitCode -ne 0) {
        throw "Échec de Gradle (code $gradleExitCode). L'application ne sera pas lancée."
    }

    Write-Host 'Lancement de Hors Cadre...'
    $savedPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $adb -s $emulatorSerial shell am start -n 'fr.horscadre/.MainActivity'
        $launchExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedPreference
    }
    if ($launchExitCode -ne 0) {
        throw "Impossible de lancer Hors Cadre sur $emulatorSerial (code $launchExitCode)."
    }
    Write-Host 'Hors Cadre est installé et lancé.'
}
catch {
    Write-Error "run-android : $($_.Exception.Message)"
    exit 1
}
