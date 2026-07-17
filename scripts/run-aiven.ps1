$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$dotenvPath = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $dotenvPath)) {
    throw "Missing $dotenvPath. Create it with DB_URL, DB_USERNAME and DB_PASSWORD."
}

$settings = @{}
Get-Content -LiteralPath $dotenvPath | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
    }
}

foreach ($requiredName in 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET') {
    if ([string]::IsNullOrWhiteSpace($settings[$requiredName])) {
        throw "$requiredName is required in .env"
    }
}

$databaseUrl = $settings['DB_URL']
if (-not $databaseUrl.StartsWith('mysql://', [StringComparison]::OrdinalIgnoreCase) -and
        -not $databaseUrl.StartsWith('jdbc:mysql://', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'DB_URL must be an Aiven mysql:// service URI or a jdbc:mysql:// URL'
}

[Environment]::SetEnvironmentVariable('DB_URL', $databaseUrl, 'Process')
[Environment]::SetEnvironmentVariable('DB_USERNAME', $settings['DB_USERNAME'], 'Process')
[Environment]::SetEnvironmentVariable('DB_PASSWORD', $settings['DB_PASSWORD'], 'Process')
[Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', 'aiven', 'Process')

[Environment]::SetEnvironmentVariable('JWT_SECRET', $settings['JWT_SECRET'], 'Process')

if (-not $env:JAVA_HOME) {
    $defaultJdk = 'C:\Program Files\Java\jdk-25.0.3'
    if (Test-Path -LiteralPath $defaultJdk) {
        [Environment]::SetEnvironmentVariable('JAVA_HOME', $defaultJdk, 'Process')
    }
}

Push-Location $repositoryRoot
try {
    & .\mvnw.cmd spring-boot:run
}
finally {
    Pop-Location
}
