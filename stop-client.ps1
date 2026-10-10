# Stops a running DecideVisuals client.
# Needed before starting a new one: the old client keeps run\logs\latest.log
# locked, so the new one fails with "Unable to delete file latest.log" and
# quits right after startup.
$procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like '*FabricMcEmu*' }

if (-not $procs) {
    Write-Host "[rebuild] no running client found"
    exit 0
}

foreach ($p in $procs) {
    Write-Host "[rebuild] stopping client PID $($p.ProcessId)"
    Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
}

# give the OS time to release the log file handle
Start-Sleep -Seconds 3
Write-Host "[rebuild] previous client stopped"
