# Helper to invoke the workspace Gradle with the correct environment.
# Usage: pwsh build.ps1 [gradle args...]   e.g.  pwsh build.ps1 build
$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$env:JAVA_HOME = Join-Path $root '.toolchain\jdk25\jdk-25.0.4.1+1'
$env:GRADLE_USER_HOME = Join-Path $root '.toolchain\gradle-home'
# The JDK25 truststore already contains the local TLS proxy certificate; share it
# with every launched JVM (incl. the Java-21 NFRT subprocess) via JAVA_TOOL_OPTIONS.
$env:JAVA_TOOL_OPTIONS = "-Dorg.gradle.native=false -Dorg.gradle.vfs.watch=false -Djavax.net.ssl.trustStore=$root\.toolchain\jdk25\jdk-25.0.4.1+1\lib\security\cacerts -Djavax.net.ssl.trustStorePassword=changeit --enable-native-access=ALL-UNNAMED"
$gradle = Join-Path $root '.toolchain\gradle-9.7.1\gradle-9.7.1\bin\gradle.bat'
& $gradle --no-daemon --no-watch-fs @args
exit $LASTEXITCODE
