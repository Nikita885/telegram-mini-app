# Путь к проекту
$PROJECT_DIR = "G:\telegram-mini-app"
$TUNNEL_URL = "http://localhost:8000"
$TUNNEL_URL_FILE = "$PROJECT_DIR\tunnel-url.txt"

# Цвета для логов
function Write-Server { Write-Host "[DOCKER] $args" -ForegroundColor Green }
function Write-Tunnel { Write-Host "[TUNNEL] $args" -ForegroundColor Cyan }
function Write-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }
function Write-Success { Write-Host "[SUCCESS] $args" -ForegroundColor Yellow }

# Запуск Docker
function Start-DockerCompose {
    Write-Server "Starting Docker containers..."
    cd $PROJECT_DIR
    docker-compose up -d
    Start-Sleep -Seconds 10
}

# Запуск туннеля и извлечение URL
function Start-Tunnel {
    Write-Tunnel "Starting Cloudflare tunnel..."
    
    # Создаём временный файл для лога
    $logFile = "$env:TEMP\cloudflared-$(Get-Random).log"
    
    # Запускаем cloudflared с перенаправлением вывода
    $proc = Start-Process -FilePath "cloudflared" `
        -ArgumentList "tunnel", "--url", $TUNNEL_URL, "--protocol", "quic", "--retries", "5" `
        -PassThru `
        -RedirectStandardError $logFile `
        -WindowStyle Hidden
    
    # Ждём появления URL в логах
    Start-Sleep -Seconds 5
    
    for ($i = 0; $i -lt 10; $i++) {
        if (Test-Path $logFile) {
            $content = Get-Content $logFile -Raw
            if ($content -match 'https://[a-z0-9\-]+\.trycloudflare\.com') {
                $url = $matches[0]
                
                # Сохраняем в файл
                $url | Out-File -FilePath $TUNNEL_URL_FILE -Encoding UTF8
                
                # Красиво выводим
                Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Green
                Write-Host "║                  CLOUDFLARE TUNNEL ACTIVE                      ║" -ForegroundColor Green
                Write-Host "╠════════════════════════════════════════════════════════════════╣" -ForegroundColor Green
                Write-Host "║  URL: " -NoNewline -ForegroundColor Green
                Write-Host "$url" -ForegroundColor Yellow -NoNewline
                Write-Host " ║" -ForegroundColor Green
                Write-Host "╚════════════════════════════════════════════════════════════════╝`n" -ForegroundColor Green
                
                # Копируем в буфер обмена
                $url | Set-Clipboard
                Write-Success "URL copied to clipboard!"
                
                break
            }
        }
        Start-Sleep -Seconds 1
    }
    
    return $proc
}

# Проверка Docker
function Test-DockerAlive {
    try {
        $status = docker-compose -f "$PROJECT_DIR\docker-compose.yml" ps --services --filter "status=running"
        $running = $status | Measure-Object | Select-Object -ExpandProperty Count
        return $running -ge 2
    } catch {
        return $false
    }
}

# Проверка сервера
function Test-ServerAlive {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8000" -TimeoutSec 3 -UseBasicParsing
        return $true
    } catch {
        return $false
    }
}

# Остановка при Ctrl+C
$cleanup = {
    Write-Host "`n[INFO] Stopping all services..." -ForegroundColor Yellow
    if ($script:tunnelProc -and !$script:tunnelProc.HasExited) {
        Stop-Process -Id $script:tunnelProc.Id -Force
    }
    cd $PROJECT_DIR
    docker-compose down
    exit
}
Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action $cleanup

# ══════════════════════════════════════════════════════════════════════════════
# ОСНОВНОЙ ЦИКЛ
# ══════════════════════════════════════════════════════════════════════════════

Write-Host "`n═══════════════════════════════════════════════════════" -ForegroundColor Yellow
Write-Host "  Telegram Mini App - Auto Restart Manager" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════`n" -ForegroundColor Yellow

# Первый запуск
Start-DockerCompose
$script:tunnelProc = Start-Tunnel

Write-Host "`n[INFO] Monitoring started. Press Ctrl+C to stop." -ForegroundColor Yellow
Write-Host "[INFO] Tunnel URL saved to: $TUNNEL_URL_FILE`n" -ForegroundColor Yellow

$lastDockerRestart = Get-Date
$lastTunnelRestart = Get-Date

while ($true) {
    Start-Sleep -Seconds 15

    # ── Проверка Docker ──────────────────────────────────────────────────
    if (!(Test-DockerAlive) -or !(Test-ServerAlive)) {
        $timeSinceRestart = (Get-Date) - $lastDockerRestart
        
        if ($timeSinceRestart.TotalSeconds -lt 30) {
            Write-Error "Docker restarting too frequently. Waiting 60 seconds..."
            Start-Sleep -Seconds 60
        }

        Write-Error "Docker containers down! Restarting..."
        cd $PROJECT_DIR
        docker-compose down
        Start-Sleep -Seconds 5
        Start-DockerCompose
        $lastDockerRestart = Get-Date
    } else {
        Write-Server "Status: OK"
    }

    # ── Проверка туннеля ─────────────────────────────────────────────────
    if ($script:tunnelProc.HasExited) {
        $timeSinceRestart = (Get-Date) - $lastTunnelRestart
        
        if ($timeSinceRestart.TotalSeconds -lt 30) {
            Write-Error "Tunnel restarting too frequently. Waiting 60 seconds..."
            Start-Sleep -Seconds 60
        }

        Write-Error "Tunnel down! Restarting..."
        $script:tunnelProc = Start-Tunnel
        $lastTunnelRestart = Get-Date
    } else {
        Write-Tunnel "Status: OK"
    }
}