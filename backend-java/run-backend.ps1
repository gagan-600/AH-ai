# Run this script to set environment variables and start the backend
# Usage: .\run-backend.ps1

$env:OPENAI_API_KEY="your_openai_api_key"
$env:LANGFUSE_SECRET_KEY="your_langfuse_secret_key"
$env:LANGFUSE_PUBLIC_KEY="your_langfuse_public_key"
$env:LANGFUSE_HOST="http://localhost:3010"

Write-Host "Environment variables set. Starting Spring Boot application..." -ForegroundColor Green
Write-Host "OPENAI_API_KEY: Set" -ForegroundColor Cyan
Write-Host "LANGFUSE keys: Set" -ForegroundColor Cyan
Write-Host ""

mvn spring-boot:run
