# start-demo.ps1
# Starts the LegalCase ERP backend with the local AI switched on.
# Run from the backend folder:   .\start-demo.ps1
# Keep this window open during the demo.

# Set the AI address in THIS window, then start Java from THIS window.
# (A variable set in another window is not seen by the backend.)
$env:LCE_OLLAMA_URL = "http://localhost:11434"
$env:LCE_OLLAMA_MODEL = "llama3.2:3b"

# Check Ollama answers before starting the system.
try {
    Invoke-RestMethod -Uri "$($env:LCE_OLLAMA_URL)/api/tags" -TimeoutSec 5 | Out-Null
    Write-Host "Ollama is running. The AI help is ON." -ForegroundColor Green
} catch {
    Write-Host "Ollama is NOT answering at $($env:LCE_OLLAMA_URL)." -ForegroundColor Yellow
    Write-Host "Open the Ollama app, then restart this script. Continuing without the AI." -ForegroundColor Yellow
}

Write-Host "Starting LegalCase ERP on http://localhost:8080 ..." -ForegroundColor Cyan
java -jar target\legal-backend.jar
