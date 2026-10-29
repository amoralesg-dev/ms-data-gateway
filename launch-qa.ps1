# ═══════════════════════════════════════════════════════════════════════════
# LAUNCH SCRIPT: QA (Control de Calidad)
# ms-data-gateway
# ═══════════════════════════════════════════════════════════════════════════
param (
    [string]$ServerPort = "8080"
)

Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host "   Iniciando ms-data-gateway [PERFIL: QA]                 " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow

$env:SPRING_PROFILES_ACTIVE = "qa"
$env:SERVER_PORT = $ServerPort

# Validación estricta de variables obligatorias (sin secretos hardcodeados)
$requiredVars = @(
    "MYSQL_GATEWAY_HOST", "MYSQL_GATEWAY_USER", "MYSQL_GATEWAY_PASSWORD",
    "OPENEDGE_HOST", "OPENEDGE_USER", "OPENEDGE_PASSWORD", "OPENEDGE_DATABASE",
    "API_KEY_1", "API_KEY_2", "ADMIN_KEY"
)
$missingVars = @()

foreach ($v in $requiredVars) {
    if (-not [System.Environment]::GetEnvironmentVariable($v)) {
        $missingVars += $v
    }
}

if ($missingVars.Count -gt 0) {
    Write-Host "[ERROR] Faltan variables de entorno requeridas para QA: $($missingVars -join ', ')" -ForegroundColor Red
    Write-Host "Defina las variables antes de ejecutar launch-qa.ps1" -ForegroundColor Red
    exit 1
}

if (-not $env:OPENEDGE_PORT)          { $env:OPENEDGE_PORT = "18111" }
if (-not $env:MYSQL_GATEWAY_PORT)     { $env:MYSQL_GATEWAY_PORT = "3306" }
if (-not $env:MYSQL_GATEWAY_DATABASE) { $env:MYSQL_GATEWAY_DATABASE = "gateway-data" }

Write-Host "  -> Profile: $env:SPRING_PROFILES_ACTIVE"
Write-Host "  -> OpenEdge: $($env:OPENEDGE_HOST):$($env:OPENEDGE_PORT)/$($env:OPENEDGE_DATABASE)"
Write-Host "  -> MySQL: $($env:MYSQL_GATEWAY_HOST):$($env:MYSQL_GATEWAY_PORT)/$($env:MYSQL_GATEWAY_DATABASE)"
Write-Host "  -> Port: $env:SERVER_PORT"
Write-Host "==========================================================" -ForegroundColor Yellow

$jarPath = if (Test-Path ".\target\ms-data-gateway.jar") { ".\target\ms-data-gateway.jar" } else { ".\target\ms-data-gateway-1.0.0.jar" }
java -jar $jarPath
