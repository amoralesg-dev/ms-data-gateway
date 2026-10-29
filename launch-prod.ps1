# ═══════════════════════════════════════════════════════════════════════════
# LAUNCH SCRIPT: PROD (Producción)
# ms-data-gateway
# ═══════════════════════════════════════════════════════════════════════════
param (
    [string]$ServerPort = "8080"
)

Write-Host "==========================================================" -ForegroundColor Red
Write-Host "   Iniciando ms-data-gateway [PERFIL: PROD]               " -ForegroundColor Red
Write-Host "==========================================================" -ForegroundColor Red

$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SERVER_PORT = $ServerPort

# En producción TODAS las credenciales son obligatorias
$requiredVars = @(
    "OPENEDGE_HOST", "OPENEDGE_PORT", "OPENEDGE_DATABASE", "OPENEDGE_USER", "OPENEDGE_PASSWORD",
    "API_KEY_1", "API_KEY_2", "ADMIN_KEY"
)
$missingVars = @()

foreach ($v in $requiredVars) {
    if (-not [System.Environment]::GetEnvironmentVariable($v)) {
        $missingVars += $v
    }
}

if ($missingVars.Count -gt 0) {
    Write-Host "[FATAL] En producción es obligatorio definir: $($missingVars -join ', ')" -ForegroundColor Red
    exit 1
}

# Validación condicional de MySQL si se encuentra configurado
if ($env:MYSQL_GATEWAY_HOST) {
    Write-Host "  -> MySQL Catálogo Habilitado en Producción: $env:MYSQL_GATEWAY_HOST:$env:MYSQL_GATEWAY_PORT/$env:MYSQL_GATEWAY_DATABASE"
}
if ($env:MYSQL_HOST) {
    Write-Host "  -> MySQL Datos Habilitado en Producción: $env:MYSQL_HOST:$env:MYSQL_PORT/$env:MYSQL_DATABASE"
}

Write-Host "  -> Profile: $env:SPRING_PROFILES_ACTIVE"
Write-Host "  -> OpenEdge: $env:OPENEDGE_HOST:$env:OPENEDGE_PORT/$env:OPENEDGE_DATABASE"
Write-Host "  -> Port: $env:SERVER_PORT"
Write-Host "==========================================================" -ForegroundColor Red

# Parámetros JVM de producción recomendados
$jvmOpts = "-Xms512m -Xmx2048m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

$jarPath = if (Test-Path ".\target\ms-data-gateway.jar") { ".\target\ms-data-gateway.jar" } else { ".\target\ms-data-gateway-1.0.0.jar" }
java $jvmOpts -jar $jarPath
