param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendDir = Join-Path $Root "backend"
$FrontendDir = Join-Path $Root "frontend"
$EnvFile = Join-Path $Root ".env"

function Write-Step($Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Require-Command($Name, $Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        Write-Host "Missing command: $Name" -ForegroundColor Red
        Write-Host $Hint -ForegroundColor Yellow
        exit 1
    }
}

function Load-DotEnv($Path) {
    if (-not (Test-Path $Path)) {
        Write-Host "No .env file found. Using current shell environment and backend defaults." -ForegroundColor Yellow
        Write-Host "Tip: copy .env.example to .env and set MySQL / LLM values before production use." -ForegroundColor Yellow
        return
    }

    Get-Content $Path | ForEach-Object {
        $Line = $_.Trim()
        if (-not $Line -or $Line.StartsWith("#")) {
            return
        }
        $Parts = $Line -split "=", 2
        if ($Parts.Count -ne 2) {
            return
        }
        $Key = $Parts[0].Trim()
        $Value = $Parts[1].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($Key, $Value, "Process")
    }
    Write-Host "Loaded environment variables from .env" -ForegroundColor Green
}

function Start-DevProcess($Title, $WorkingDirectory, $Command) {
    $EscapedDir = $WorkingDirectory.Replace("'", "''")
    $EscapedCommand = $Command.Replace("'", "''")
    $Script = "Set-Location '$EscapedDir'; `$Host.UI.RawUI.WindowTitle = '$Title'; $EscapedCommand"
    Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $Script
}

Write-Host "smartDataQuerying dev launcher" -ForegroundColor Green
Load-DotEnv $EnvFile

if (-not $FrontendOnly) {
    Write-Step "Checking backend runtime"
    Require-Command "java" "Install Java 21 and make sure java is available in PATH."
    Require-Command "mvn" "Install Maven and make sure mvn is available in PATH."
}

if (-not $BackendOnly) {
    Write-Step "Checking frontend runtime"
    Require-Command "npm" "Install Node.js/npm and make sure npm is available in PATH."
    if (-not $SkipInstall -and -not (Test-Path (Join-Path $FrontendDir "node_modules"))) {
        Write-Step "Installing frontend dependencies"
        Push-Location $FrontendDir
        npm install
        Pop-Location
    }
}

if (-not $FrontendOnly) {
    Write-Step "Starting Spring Boot backend"
    Start-DevProcess "smartDataQuerying backend" $BackendDir "mvn spring-boot:run"
}

if (-not $BackendOnly) {
    Write-Step "Starting Vite frontend"
    Start-DevProcess "smartDataQuerying frontend" $FrontendDir "npm run dev -- --host 127.0.0.1"
}

Write-Host ""
Write-Host "Started requested services." -ForegroundColor Green
if (-not $FrontendOnly) {
    Write-Host "Backend:  http://localhost:8080" -ForegroundColor Gray
}
if (-not $BackendOnly) {
    Write-Host "Frontend: http://127.0.0.1:5173" -ForegroundColor Gray
}
Write-Host ""
Write-Host "Keep the opened PowerShell windows running. Close them to stop the services." -ForegroundColor Yellow

