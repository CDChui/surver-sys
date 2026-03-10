Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

param(
    [ValidateSet("up", "down", "restart", "logs", "ps")]
    [string]$Action = "up"
)

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker command not found. Please install Docker Desktop first."
}

switch ($Action) {
    "up" {
        docker compose up -d
    }
    "down" {
        docker compose down
    }
    "restart" {
        docker compose down
        docker compose up -d
    }
    "logs" {
        docker compose logs -f --tail=200
    }
    "ps" {
        docker compose ps
    }
}
