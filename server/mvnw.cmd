@echo off
setlocal
set "MAVEN_VERSION=3.9.9"
set "MAVEN_HOME=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip'; $z=$env:TEMP+'\apache-maven-%MAVEN_VERSION%.zip'; Invoke-WebRequest $u -OutFile $z; New-Item -ItemType Directory -Force '%MAVEN_HOME%\..' | Out-Null; Expand-Archive -Force $z '%MAVEN_HOME%\..'"
  if errorlevel 1 exit /b 1
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
