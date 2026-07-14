param(
    [switch]$Local
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$rootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$backendDir = Join-Path $rootDir "backend"
$mavenVersion = "3.9.9"
$localMaven = Join-Path $rootDir ".tools\apache-maven-$mavenVersion\bin\mvn.cmd"

if ($Local) {
    [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_URL", "jdbc:h2:mem:swp391;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "Process")
    [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_USERNAME", "sa", "Process")
    [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_PASSWORD", "", "Process")
    [Environment]::SetEnvironmentVariable("SPRING_DATASOURCE_DRIVER", "org.h2.Driver", "Process")
    [Environment]::SetEnvironmentVariable("SPRING_JPA_DDL_AUTO", "create-drop", "Process")
} else {
    foreach ($envFile in @((Join-Path $rootDir ".env"), (Join-Path $rootDir ".env.local"))) {
        if (-not (Test-Path $envFile)) {
            continue
        }

        Get-Content $envFile | ForEach-Object {
            $line = $_.Trim()
            if ($line.Length -eq 0 -or $line.StartsWith("#") -or -not $line.Contains("=")) {
                return
            }

            $parts = $line -split "=", 2
            $name = $parts[0].Trim()
            $value = $parts[1].Trim()

            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }

            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

$mvnCommand = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCommand) {
    $mvnBin = $mvnCommand.Source
} else {
    $mvnBin = $localMaven
    if (-not (Test-Path $mvnBin)) {
        $toolsDir = Join-Path $rootDir ".tools"
        New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null

        $archive = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.tar.gz"
        Invoke-WebRequest `
            -Uri "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.tar.gz" `
            -OutFile $archive
        tar -xzf $archive -C $toolsDir
    }
}

Set-Location $backendDir
& $mvnBin spring-boot:run
