$ErrorActionPreference = "Stop"

$patterns = @(
    "^\s*AI_API_KEY\s*=\s*[^#\s].+",
    "^\s*OLLAMA_CLOUD_API_KEY\s*=\s*[^#\s].+",
    "^\s*JWT_SECRET\s*=\s*[^#\s].+",
    "sk-[A-Za-z0-9_-]+",
    "github_pat_[A-Za-z0-9_]+"
)

$exclude = "\\(\.git|build|\.gradle)\\"
$textExtensions = @(
    ".java", ".kt", ".kts", ".yml", ".yaml", ".md", ".txt", ".properties",
    ".conf", ".sql", ".sh", ".ps1", ".json", ".xml", ".toml", ".gradle",
    ".gitignore", ".gitattributes", ".example"
)

$files = Get-ChildItem -Recurse -File |
    Where-Object { $_.FullName -notmatch $exclude } |
    Where-Object { $_.Name -notin @(".env", "local.properties") } |
    Where-Object { $textExtensions -contains $_.Extension -or $textExtensions -contains $_.Name }

$found = $false
foreach ($pattern in $patterns) {
    $matches = $files | Select-String -Pattern $pattern
    if ($matches) {
        $found = $true
        $matches | ForEach-Object {
            Write-Host "$($_.Path):$($_.LineNumber): possible secret pattern: $pattern"
        }
    }
}

if ($found) {
    exit 1
}

Write-Host "No obvious committed secret patterns found."
