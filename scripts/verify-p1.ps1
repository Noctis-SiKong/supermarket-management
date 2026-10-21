param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$taskProject = Split-Path -Parent $PSScriptRoot
$taskJdkCandidates = @($env:JAVA_HOME, (Join-Path $env:LOCALAPPDATA 'pdc-build-tools/jdk-17.0.20.1+1'), (Join-Path $env:TEMP 'p1-jdk17/jdk'))
$taskJava = $null
foreach ($taskJdk in $taskJdkCandidates) {
    if (-not $taskJdk -or -not (Test-Path -LiteralPath (Join-Path $taskJdk 'bin/java.exe'))) { continue }
    # PS5.1 treats native stderr as an error under Stop; capture the version explicitly.
    $taskProbe = New-Object Diagnostics.ProcessStartInfo
    $taskProbe.FileName = Join-Path $taskJdk 'bin/java.exe'
    $taskProbe.Arguments = '-version'
    $taskProbe.UseShellExecute = $false
    $taskProbe.RedirectStandardError = $true
    $taskProbe.RedirectStandardOutput = $true
    $taskProcess = [Diagnostics.Process]::Start($taskProbe)
    try { $taskVersion = $taskProcess.StandardError.ReadToEnd() + $taskProcess.StandardOutput.ReadToEnd(); $taskProcess.WaitForExit() }
    finally { $taskProcess.Dispose() }
    if ($taskVersion -match 'version "17\.') { $env:JAVA_HOME = $taskJdk; $taskJava = Join-Path $taskJdk 'bin/java.exe'; break }
}
if (-not $taskJava) { throw 'Set JAVA_HOME to an installed JDK 17.' }
Push-Location $taskProject
$taskFixture = $null
try {
    $taskMavenArgs = @('-B', '-q', '--no-transfer-progress', 'clean', 'verify')
    if ($Offline) { $taskMavenArgs = @('--offline') + $taskMavenArgs }
    & (Join-Path $taskProject 'mvnw.cmd') @taskMavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Maven verification failed.' }
    $taskTotals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($taskReport in Get-ChildItem -LiteralPath (Join-Path $taskProject 'target/surefire-reports') -Filter 'TEST-*.xml') {
        [xml]$taskXml = Get-Content -Raw -LiteralPath $taskReport.FullName -Encoding utf8
        foreach ($taskMetric in @('tests','failures','errors','skipped')) { $taskTotals[$taskMetric] += [int]$taskXml.testsuite.GetAttribute($taskMetric) }
    }
    if ($taskTotals.tests -eq 0 -or $taskTotals.failures -ne 0 -or $taskTotals.errors -ne 0) { throw 'Missing or failed test reports.' }
    $taskJar = Join-Path $taskProject 'target/supermarket-management-0.1.0-SNAPSHOT-jar-with-dependencies.jar'
    $taskEntries = & (Join-Path $env:JAVA_HOME 'bin/jar.exe') tf $taskJar
    if ($LASTEXITCODE -ne 0 -or $taskEntries -notcontains 'com/fasterxml/jackson/databind/ObjectMapper.class' -or
        $taskEntries -notcontains 'nz/ac/aut/pdc/supermarket/app/Main.class' -or
        @($taskEntries | Where-Object { $_ -match 'FictionalDependencies|CrashAfterCommitProbe|SubmissionGateway.class|reversal/ReversalService.class' }).Count -ne 0) { throw 'Executable package/dependency isolation failed.' }
    $taskTempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $taskFixture = Join-Path $taskTempRoot ('p1-verify-' + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path (Join-Path $taskFixture 'docs') | Out-Null
    Set-Content -LiteralPath (Join-Path $taskFixture 'README.md') -Value 'FICTIONAL isolated packaged runtime test' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $taskFixture 'pom.xml') -Value '<project/>' -Encoding utf8
    & $taskJava '-Dfile.encoding=UTF-8' -jar $taskJar --help | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Packaged help failed.' }
    & $taskJava '-Dfile.encoding=UTF-8' -jar $taskJar --check --project-root $taskFixture
    if ($LASTEXITCODE -ne 0 -or (Test-Path -LiteralPath (Join-Path $taskFixture 'data/snapshot.json'))) { throw 'Empty startup seeded unexpected data.' }
    # Synthetic credentials exist only under this temporary fixture; no source password or hash is shipped.
    $taskPassword = [guid]::NewGuid().ToString()
    $taskHasher = [Security.Cryptography.SHA256]::Create()
    try { $taskHash = [BitConverter]::ToString($taskHasher.ComputeHash([Text.Encoding]::UTF8.GetBytes($taskPassword))).Replace('-','').ToLowerInvariant() }
    finally { $taskHasher.Dispose() }
    $taskAccounts = @(
        @{accountId='fictional-manager';username='fictional-manager';passwordHash=$taskHash;role='MANAGER'},
        @{accountId='fictional-clerk';username='fictional-clerk';passwordHash=$taskHash;role='CLERK'},
        @{accountId='fictional-admin';username='fictional-admin';passwordHash=$taskHash;role='ADMIN'}
    )
    $taskAccountFile = Join-Path $taskFixture 'initial-accounts.json'
    [IO.File]::WriteAllText($taskAccountFile, (ConvertTo-Json -InputObject $taskAccounts), [Text.UTF8Encoding]::new($false))
    $taskInput = @('fictional-manager',$taskPassword,'7','new','00123456','Fictional JAR product','item','3.33','8','00123456','5','S','Fictional opening','13','00123456','1','','y','14','DOCUMENT','','0','exit') -join "`n"
    $taskOldEncoding = $OutputEncoding
    $taskOldConsoleEncoding = [Console]::OutputEncoding
    try {
        $OutputEncoding = [Text.UTF8Encoding]::new($false)
        [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
        $taskOutput = ($taskInput | & $taskJava '-Dfile.encoding=UTF-8' -jar $taskJar --project-root $taskFixture --accounts $taskAccountFile | Out-String)
        $taskExit = $LASTEXITCODE
        $taskZhProject = Join-Path $taskFixture 'zh-run'
        New-Item -ItemType Directory -Path (Join-Path $taskZhProject 'docs') | Out-Null
        [IO.File]::WriteAllText((Join-Path $taskZhProject 'README.md'), 'FICTIONAL isolated Chinese run', [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $taskZhProject 'pom.xml'), '<project/>', [Text.UTF8Encoding]::new($false))
        $taskEnglishHelp = (& $taskJava '-Dfile.encoding=UTF-8' -jar $taskJar --lang en --help | Out-String)
        if ($LASTEXITCODE -ne 0 -or -not $taskEnglishHelp.Contains('Language')) { throw 'Explicit English packaged help failed.' }
        $taskChineseHelp = (& $taskJava '-Dfile.encoding=UTF-8' -jar $taskJar --lang zh --help | Out-String)
        if ($LASTEXITCODE -ne 0 -or -not $taskChineseHelp.Contains('登录')) { throw 'Chinese packaged help failed.' }
        $taskZhOutput = ($taskInput | & $taskJava '-Dfile.encoding=UTF-8' -jar $taskJar --lang zh --project-root $taskZhProject --accounts $taskAccountFile | Out-String)
        $taskZhExit = $LASTEXITCODE

    } finally {
        [Console]::OutputEncoding = $taskOldConsoleEncoding
        $OutputEncoding = $taskOldEncoding
    }
    $taskState = Get-Content -Raw -LiteralPath (Join-Path $taskFixture 'data/snapshot.json') -Encoding utf8 | ConvertFrom-Json
    if ($taskExit -ne 0 -or -not $taskOutput.Contains('Login successful') -or $taskOutput.Contains($taskPassword) -or
        $taskState.last_commit_ordinal -ne 3 -or @($taskState.records | Where-Object table -eq 'PRODUCT').Count -ne 1 -or
        @($taskState.records | Where-Object table -eq 'SALE').Count -ne 1 -or @($taskState.records | Where-Object table -eq 'STOCK_ENTRY').Count -ne 2) { throw 'Packaged real CUI smoke failed.' }
    $taskZhState = Get-Content -Raw -LiteralPath (Join-Path $taskZhProject 'data/snapshot.json') -Encoding utf8 | ConvertFrom-Json
    if ($taskZhExit -ne 0 -or -not $taskZhOutput.Contains('登录成功') -or $taskZhOutput.Contains($taskPassword) -or
        $taskOutput.Contains('TEXT_UNAVAILABLE') -or $taskZhOutput.Contains('TEXT_UNAVAILABLE') -or
        $taskZhState.last_commit_ordinal -ne 3 -or @($taskZhState.records | Where-Object table -eq 'PRODUCT').Count -ne 1 -or
        @($taskZhState.records | Where-Object table -eq 'SALE').Count -ne 1 -or @($taskZhState.records | Where-Object table -eq 'STOCK_ENTRY').Count -ne 2) { throw 'Chinese packaged real CUI smoke failed.' }
    $taskEnMoney = (@($taskState.records | Where-Object table -eq 'MONEY_ENTRY')[0].json | ConvertFrom-Json).amountSigned
    $taskZhMoney = (@($taskZhState.records | Where-Object table -eq 'MONEY_ENTRY')[0].json | ConvertFrom-Json).amountSigned
    if ($taskEnMoney -ne 3.33 -or $taskZhMoney -ne $taskEnMoney) { throw 'Language changed the packaged sale amount.' }
    [pscustomobject]@{ tests=$taskTotals.tests; failures=$taskTotals.failures; errors=$taskTotals.errors; skipped=$taskTotals.skipped; packagedCuiExit=$taskExit; chinesePackagedCuiExit=$taskZhExit; legalBusinessCommits=$taskState.last_commit_ordinal } | ConvertTo-Json -Compress
} finally {
    # One shell end-to-end. Delete only this invocation's generated, resolved, non-link temporary directory.
    if ($taskFixture -and (Test-Path -LiteralPath $taskFixture)) {
        $taskResolved = (Get-Item -LiteralPath $taskFixture).FullName
        $taskTempPrefix = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
        if (-not $taskResolved.StartsWith($taskTempPrefix,[StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Leaf $taskResolved) -notlike 'p1-verify-*' -or
            ((Get-Item -LiteralPath $taskResolved).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe temporary cleanup target; retained.' }
        if (-not [string]::Equals($taskResolved, [IO.Path]::GetFullPath($taskFixture), [StringComparison]::OrdinalIgnoreCase)) { throw 'Temporary directory identity changed; retained.' }
        # Inspect children without following links before recursive cleanup.
        $taskPending = New-Object 'Collections.Generic.Stack[string]'
        $taskPending.Push($taskResolved)
        while ($taskPending.Count -gt 0) {
            $taskDirectory = $taskPending.Pop()
            foreach ($taskChild in Get-ChildItem -LiteralPath $taskDirectory -Force) {
                if ($taskChild.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Temporary directory contains a link; retained.' }
                if ($taskChild.PSIsContainer) { $taskPending.Push($taskChild.FullName) }
            }
        }
        if ((Get-Item -LiteralPath $taskResolved).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Temporary directory became a link; retained.' }
        Remove-Item -LiteralPath $taskResolved -Recurse -Force
    }
    Pop-Location
}
