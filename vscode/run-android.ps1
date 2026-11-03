$ErrorActionPreference = "Stop"

# Se placer à la racine du projet
Set-Location (Join-Path $PSScriptRoot "..")

# Utiliser Java 17
$jdk = Get-ChildItem "C:\Program Files\Microsoft" -Directory -Filter "jdk-17*" |
    Select-Object -First 1

if (-not $jdk) {
    throw "Java 17 introuvable."
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Utiliser Gradle 8.13 déjà installé
$gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.13-bin" `
    -Filter gradle.bat -Recurse |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $gradle) {
    throw "Gradle 8.13 introuvable."
}

# Vérifier la connexion de l'émulateur
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

$devices = & $adb devices

if (-not ($devices | Select-String "\sdevice$")) {
    throw "Aucun émulateur ou téléphone Android connecté."
}

# Compiler et installer
Write-Host "Compilation et installation de Hors Cadre..."

& $gradle :app:installDebug --offline

if ($LASTEXITCODE -ne 0) {
    throw "Échec de la compilation."
}

# Lancer l'application
Write-Host "Lancement de Hors Cadre..."

& $adb shell am start -n fr.horscadre/.MainActivity

if ($LASTEXITCODE -ne 0) {
    throw "Impossible de lancer Hors Cadre."
}

Write-Host "Hors Cadre est prêt !"