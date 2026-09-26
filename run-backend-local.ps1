param([switch]$UseCompiledClasses)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$runtimeRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '../../work/runtime'))
$mavenRepo = Join-Path $projectRoot 'backend/.m2'
$env:JAVA_HOME = Join-Path $runtimeRoot 'jdk/jdk-17.0.20.1+1'
Get-Content (Join-Path $projectRoot '.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process') }
}
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3307/chimaera?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC'
$env:SPRING_DATASOURCE_USERNAME = 'chimaera'
$env:SPRING_DATASOURCE_PASSWORD = 'chimaera-local-3307'
Set-Location (Join-Path $projectRoot 'backend')
$buildOptions = @()
if ($UseCompiledClasses) { $buildOptions = @('-Dmaven.main.skip=true', '-Dmaven.test.skip=true') }
& (Join-Path $runtimeRoot 'maven-3.9.16/bin/mvn.cmd') "-Dmaven.repo.local=$mavenRepo" @buildOptions spring-boot:run
