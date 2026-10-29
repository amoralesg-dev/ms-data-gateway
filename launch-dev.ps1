# ═══════════════════════════════════════════════════════════════════════════
# LAUNCH SCRIPT: LOCAL / DEV
# ms-data-gateway
# ═══════════════════════════════════════════════════════════════════════════
# NOTA DE SEGURIDAD:
# Este script NO contiene credenciales ni contraseñas hardcodeadas.
# Las variables sensibles deben estar definidas en el entorno del usuario o sistema.
# ═══════════════════════════════════════════════════════════════════════════
param (
    [string]$ServerPort = "8080",
    [string]$Profile = "local"
)

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Iniciando ms-data-gateway [PERFIL: $($Profile.ToUpper())] " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Configuración de Entorno y Perfil
if (-not $env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE = $Profile }
$env:SERVER_PORT = $ServerPort

# 2. Validación de variables sensibles requeridas
$requiredVars = @(
    "MYSQL_GATEWAY_HOST", "MYSQL_GATEWAY_USER", "MYSQL_GATEWAY_PASSWORD",
    "API_KEY_1", "ADMIN_KEY"
)
$missingVars = @()
foreach ($v in $requiredVars) {
    if (-not [System.Environment]::GetEnvironmentVariable($v)) {
        $missingVars += $v
    }
}

if ($missingVars.Count -gt 0) {
    Write-Host "[AVISO] Variables de entorno no definidas en sesión: $($missingVars -join ', ')" -ForegroundColor Yellow
    Write-Host "Asegúrese de exportarlas antes de iniciar si el perfil activo no define valores locales." -ForegroundColor Yellow
}

if (-not $env:MYSQL_GATEWAY_PORT)     { $env:MYSQL_GATEWAY_PORT = "3306" }
if (-not $env:MYSQL_GATEWAY_DATABASE) { $env:MYSQL_GATEWAY_DATABASE = "gateway-data" }
if (-not $env:OPENEDGE_PORT)          { $env:OPENEDGE_PORT = "18111" }

Write-Host "  -> Profile: $env:SPRING_PROFILES_ACTIVE"
Write-Host "  -> OpenEdge: $($env:OPENEDGE_HOST):$($env:OPENEDGE_PORT)/$($env:OPENEDGE_DATABASE)"
Write-Host "  -> MySQL Catalog: $($env:MYSQL_GATEWAY_HOST):$($env:MYSQL_GATEWAY_PORT)/$($env:MYSQL_GATEWAY_DATABASE)"
Write-Host "  -> Port: $env:SERVER_PORT"
Write-Host "==========================================================" -ForegroundColor Cyan

$jarPath = ".\target\ms-data-gateway.jar"
if (-not (Test-Path $jarPath)) {
    $jarPathFallback = ".\target\ms-data-gateway-1.0.0.jar"
    if (Test-Path $jarPathFallback) {
        $jarPath = $jarPathFallback
    } else {
        Write-Host "[ALERTA] Archivo $jarPath no encontrado. Ejecutando mvn package..." -ForegroundColor Yellow
        mvn package -DskipTests
    }
}

java -jar $jarPath
