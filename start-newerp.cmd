@echo off
rem Starts NewErp from the built jar, in this folder so application-local.yml is found.
rem Build the jar first:  mvnw.cmd -DskipTests package
cd /d "%~dp0"
java -jar newerp-api\target\newerp.jar %*
