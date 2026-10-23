param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$repository = 'GoryNickel9/app-turni'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if (git status --porcelain) { throw 'Commit the reviewed sources before publishing a release.' }
    if (-not $SkipBuild) {
        & "$PSScriptRoot/verify-windows.ps1" -Release
        if ($LASTEXITCODE -ne 0) { throw 'Verification failed.' }
    }
    $metadata = Get-Content 'app/build/outputs/apk/release/output-metadata.json' -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    $versionCode = $metadata.elements[0].versionCode
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid release version.' }
    $apk = Join-Path $root "app/build/outputs/apk/release/$($metadata.elements[0].outputFile)"
    $sdkPath = $env:ANDROID_HOME
    if (-not $sdkPath) {
        $sdkLine = Get-Content local.properties | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
        $sdkPath = $sdkLine.Substring(8).Replace('\\', '\').Replace('\:', ':')
    }
    $buildTools = Get-ChildItem -LiteralPath "$sdkPath/build-tools" -Directory | Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    $signature = & "$($buildTools.FullName)/apksigner.bat" verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $certificate = $signature | Select-String '^Signer #1 certificate SHA-256 digest: ([a-fA-F0-9]+)$'
    $expectedCertificate = (Get-Content signing-certificate.sha256 -Raw).Trim()
    if (-not $certificate -or $certificate.Matches[0].Groups[1].Value.ToLowerInvariant() -ne $expectedCertificate) {
        throw 'Wrong signing certificate: this APK cannot update the installed app.'
    }
    $tag = "v$version"
    $commit = (git rev-parse HEAD).Trim()
    & gh api "repos/$repository/commits/$commit" --silent
    if ($LASTEXITCODE -ne 0) { throw 'Push the reviewed commit before publishing.' }
    $notes = "docs/releases/$version.md"
    if (-not (Test-Path -LiteralPath $notes)) { throw "Missing release notes: $notes" }
    & gh release view $tag --repo $repository *> $null
    if ($LASTEXITCODE -eq 0) { throw "Release $tag already exists; published versions must not be replaced." }
    $output = Join-Path $root "artifacts/release-$version"
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    $apkName = "Shiftrail-$version.apk"
    Copy-Item -LiteralPath $apk -Destination (Join-Path $output $apkName)
    $checksum = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest = [ordered]@{ schemaVersion = 1; versionCode = [int]$versionCode; versionName = $version; sha256 = $checksum; minSdk = 26 }
    [IO.File]::WriteAllText((Join-Path $output 'update.json'), ($manifest | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $output 'SHA256SUMS.txt'), "$checksum  $apkName`n", [Text.UTF8Encoding]::new($false))
    & gh release create $tag --repo $repository --target $commit --title "Shiftrail $version" --notes-file $notes (Join-Path $output $apkName) (Join-Path $output 'update.json') (Join-Path $output 'SHA256SUMS.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Release publication failed.' }
} finally { Pop-Location }
