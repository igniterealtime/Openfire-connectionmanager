@echo off

REM #
REM # $RCSfile$
REM # $Revision: 1102 $
REM # $Date: 2005-03-07 22:36:48 -0300 (Mon, 07 Mar 2005) $
REM #

REM # SET JVM_SETTINGS = "-Xms512m -Xmx1024m"
SET JVM_SETTINGS = ""

if "%JAVA_HOME%" == "" goto javaerror
if not exist "%JAVA_HOME%\bin\java.exe" goto javaerror
goto run

:javaerror
echo.
echo Error: JAVA_HOME environment variable not set, Connection Manager not started.
echo.
goto end

:run
if "%1" == "-debug" goto debug
start "Connection Manager" "%JAVA_HOME%\bin\java" -server %JVM_SETTINGS% -jar ..\lib\startup.jar
goto end

:debug
start "Connection Manager" "%JAVA_HOME%\bin\java" %JVM_SETTINGS% -Xdebug -Xint -server -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000 -jar ..\lib\startup.jar
goto end
:end


