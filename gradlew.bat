@rem Gradle startup script for Windows
@if "%DEBUG%"=="" @echo off
setlocal

set GRADLE_HOME=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.14.3-bin\cv11ve7ro1n3o1j4so8xd9n66\gradle-8.14.3
set PATH=%GRADLE_HOME%\bin;%PATH%

gradle %*

endlocal
