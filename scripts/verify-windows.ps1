param([string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $Jdk) { throw 'Set JAVA_HOME or pass -Jdk with a JDK 17 or 21 path.' }
$previousJava = $env:JAVA_HOME
$previousOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_HOME = $Jdk
    # Force Java to fall back to TCP when Windows AF_UNIX connect is broken.
    $socketFallback = 'C:/' + ('turni-' * 22)
    $env:JAVA_TOOL_OPTIONS = "$previousOptions -Djdk.net.unixdomain.tmpdir=$socketFallback".Trim()
    Push-Location (Join-Path $PSScriptRoot '..')
    try { & .\gradlew.bat assembleDebug test lint checkKotlinFormat --no-daemon; if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" } }
    finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:JAVA_TOOL_OPTIONS = $previousOptions
}
