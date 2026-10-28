# Chay toan bo he thong tu cac jar da build (mvnw.cmd package -DskipTests).
# Ha tang: PostgreSQL (service Windows), Redis va Kafka (docker). Thu tu: eureka -> config-server -> 6 service ->
# gateway. Log o logs/<service>.log.
#   powershell -File tools/start-all.ps1            # profile dev
#   powershell -File tools/start-all.ps1 -Stop      # dung tat ca
param([switch]$Stop, [string]$Profile = "dev")

$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root "logs"
$pidFile = Join-Path $logs "pids.txt"
New-Item -ItemType Directory -Force $logs | Out-Null

if ($Stop) {
    if (Test-Path $pidFile) {
        # java.exe trong Oracle/javapath chi la trinh khoi chay, JVM that la tien trinh con -> giet ca cay
        Get-Content $pidFile | ForEach-Object { taskkill /PID $_ /T /F 2>$null | Out-Null }
        Remove-Item $pidFile
    }
    Write-Host "Da dung."
    return
}

function Wait-Port([int]$port, [string]$name) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 "http://localhost:$port/actuator/health"
            if ($r.StatusCode -eq 200) { Write-Host "  $name san sang (:$port)"; return }
        } catch {}
        Start-Sleep -Seconds 2
    }
    throw "$name khong len sau 3 phut, xem logs/$name.log"
}

function Start-Module([string]$name, [string[]]$extra = @()) {
    $jar = Get-ChildItem (Join-Path $root "$name/target") -Filter "$name-*.jar" | Select-Object -First 1
    if (-not $jar) { throw "Chua build $name - chay mvnw.cmd package -DskipTests" }
    # Thu muc lam viec la thu muc module de duong dan ../config, ../storage tro dung goc du an
    # 9 JVM tren may 16 GB: gioi han heap, mac dinh moi JVM duoc lay toi 1/4 RAM
    $jvm = @("-Xms64m", "-Xmx256m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xss512k")
    $javaArgs = $jvm + @("-jar", $jar.FullName,"--spring.profiles.active=$Profile") + $extra
    $p = Start-Process java -ArgumentList $javaArgs `
        -WorkingDirectory (Join-Path $root $name) -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $logs "$name.log") -RedirectStandardError (Join-Path $logs "$name.err.log")
    Add-Content $pidFile $p.Id
}

# Kafka va Redis chay trong docker; start lai container da dung la an toan
docker start rhl-redis rhl-kafka | Out-Null

Start-Module "eureka-server";  Wait-Port 8761 "eureka-server"
# config-server dung profile composite (native cho bi mat + git config-repo), khong nhan profile dev.
# config-repo phai la git repo co it nhat mot commit tren nhanh main.
$configRepo = "file:///" + ((Join-Path $root "config-repo") -replace '\\', '/')
$Saved = $Profile; $Profile = "composite"
Start-Module "config-server" @("--CONFIG_GIT_URI=$configRepo"); $Profile = $Saved
Wait-Port 8888 "config-server"
$services = [ordered]@{ "identity-service" = 8081; "driver-service" = 8082; "location-service" = 8083;
                        "pricing-service" = 8084; "trip-service" = 8085; "payment-service" = 8086 }
$services.Keys | ForEach-Object { Start-Module $_ }
$services.GetEnumerator() | ForEach-Object { Wait-Port $_.Value $_.Key }
Start-Module "api-gateway";    Wait-Port 8080 "api-gateway"

# Gateway chi biet service sau khi tai danh ba tu Eureka (~30 s); truoc do moi route lb:// tra 503
$probes = "/api/v1/auth/login", "/api/v1/drivers/me", "/api/v1/drivers/me/location", "/api/v1/quotes",
          "/api/v1/trips/mine", "/api/v1/drivers/me/wallet"
foreach ($path in $probes) {
    for ($i = 0; $i -lt 60; $i++) {
        $code = 0
        try { $code = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 "http://localhost:8080$path").StatusCode }
        catch { if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } }
        if ($code -ne 0 -and $code -ne 503) { break }
        Start-Sleep -Seconds 2
    }
}
Write-Host "Xong: http://localhost:8080  (Eureka: http://localhost:8761)"
