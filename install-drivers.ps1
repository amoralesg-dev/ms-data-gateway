#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Instala los drivers JDBC de Progress OpenEdge en el repositorio Maven local.

.DESCRIPTION
    Este script extrae los JARs del archivo java.zip (incluido en el proyecto)
    e instala openedge.jar y progress.jar en el repositorio Maven local (~/.m2).
    
    Debe ejecutarse UNA VEZ antes del primer 'mvn compile' en una nueva máquina.
    
    Los JARs son:
    - openedge.jar  → Driver JDBC DataDirect para Progress OpenEdge
                      Clase: com.ddtek.jdbc.openedge.OpenEdgeDriver
    - progress.jar  → Librería base Progress (SSL/TLS, utilidades)
                      REQUERIDA para conexiones con encryptionMethod=ssl

.EXAMPLE
    .\install-drivers.ps1
    
.NOTES
    Requiere Maven en el PATH.
    En Windows: ejecutar con PowerShell 5.1+ o PowerShell Core.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$LibsDir = Join-Path $ProjectRoot "libs"
$JavaZip = Join-Path $ProjectRoot "java.zip"

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║       MS Data Gateway — Instalación de Drivers OpenEdge         ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# 1. Verificar Maven
try {
    $mvnVersion = mvn --version 2>&1 | Select-String "Apache Maven"
    Write-Host "✓ Maven: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ Maven no encontrado en PATH. Instalar Maven antes de continuar." -ForegroundColor Red
    exit 1
}

# 2. Verificar java.zip
if (-not (Test-Path $JavaZip)) {
    Write-Host "✗ No se encontró java.zip en: $JavaZip" -ForegroundColor Red
    exit 1
}
Write-Host "✓ java.zip encontrado: $JavaZip" -ForegroundColor Green

# 3. Extraer JARs si no existen
$OpenEdgeJar = Join-Path $LibsDir "openedge.jar"
$ProgressJar = Join-Path $LibsDir "progress.jar"

if (-not (Test-Path $LibsDir)) {
    New-Item -ItemType Directory -Path $LibsDir | Out-Null
}

if (-not (Test-Path $OpenEdgeJar) -or -not (Test-Path $ProgressJar)) {
    Write-Host "Extrayendo JARs de java.zip..." -ForegroundColor Yellow
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JavaZip)
    foreach ($entry in $zip.Entries) {
        if ($entry.Name -in @("openedge.jar", "progress.jar")) {
            $destPath = Join-Path $LibsDir $entry.Name
            $stream = $entry.Open()
            $fileStream = [System.IO.File]::Create($destPath)
            $stream.CopyTo($fileStream)
            $fileStream.Close()
            $stream.Close()
            Write-Host "  Extraído: $($entry.Name) ($([Math]::Round($entry.Length / 1KB, 1)) KB)" -ForegroundColor Gray
        }
    }
    $zip.Dispose()
} else {
    Write-Host "✓ JARs ya extraídos en libs/" -ForegroundColor Green
}

# 4. Crear POM temporal mínimo para evitar depender del POM del proyecto
$TempPom = Join-Path $env:TEMP "temp-pom-gateway.xml"
@"
<?xml version="1.0"?>
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>temp</groupId>
  <artifactId>temp-installer</artifactId>
  <version>1.0</version>
</project>
"@ | Out-File -FilePath $TempPom -Encoding UTF8

# 5. Instalar openedge.jar
Write-Host ""
Write-Host "Instalando openedge.jar en repositorio Maven local..." -ForegroundColor Yellow
mvn install:install-file `
    "-f" $TempPom `
    "-Dfile=$OpenEdgeJar" `
    "-DgroupId=com.progress.datadirect" `
    "-DartifactId=openedge" `
    "-Dversion=6.0.0" `
    "-Dpackaging=jar" `
    "-DgeneratePom=true" `
    "-DcreateChecksum=true" `
    "-q"

$OEPath = "$env:USERPROFILE\.m2\repository\com\progress\datadirect\openedge\6.0.0\openedge-6.0.0.jar"
if (Test-Path $OEPath) {
    Write-Host "✓ openedge-6.0.0.jar instalado en .m2 ($([Math]::Round((Get-Item $OEPath).Length / 1KB, 1)) KB)" -ForegroundColor Green
} else {
    Write-Host "✗ Falló la instalación de openedge.jar" -ForegroundColor Red
    exit 1
}

# 6. Instalar progress.jar
Write-Host ""
Write-Host "Instalando progress.jar en repositorio Maven local..." -ForegroundColor Yellow
mvn install:install-file `
    "-f" $TempPom `
    "-Dfile=$ProgressJar" `
    "-DgroupId=com.progress" `
    "-DartifactId=progress" `
    "-Dversion=12.0.0" `
    "-Dpackaging=jar" `
    "-DgeneratePom=true" `
    "-DcreateChecksum=true" `
    "-q"

$PGPath = "$env:USERPROFILE\.m2\repository\com\progress\progress\12.0.0\progress-12.0.0.jar"
if (Test-Path $PGPath) {
    Write-Host "✓ progress-12.0.0.jar instalado en .m2 ($([Math]::Round((Get-Item $PGPath).Length / 1KB, 1)) KB)" -ForegroundColor Green
} else {
    Write-Host "✗ Falló la instalación de progress.jar" -ForegroundColor Red
    exit 1
}

# Limpiar POM temporal
Remove-Item $TempPom -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║  ✓ Drivers instalados correctamente. Puedes ejecutar:           ║" -ForegroundColor Green
Write-Host "║                                                                  ║" -ForegroundColor Green
Write-Host "║    mvn clean compile                                             ║" -ForegroundColor Green
Write-Host "║    mvn spring-boot:run                                           ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "Variables de entorno requeridas antes de ejecutar:" -ForegroundColor Yellow
Write-Host "  OPENEDGE_USER     = <usuario de OpenEdge>"
Write-Host "  OPENEDGE_PASSWORD = <contraseña>"
Write-Host "  API_KEY_1         = <clave API del servicio>"
Write-Host ""
