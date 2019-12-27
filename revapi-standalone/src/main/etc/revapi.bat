@REM
@REM Copyright 2014-2019 Lukas Krejci
@REM and other contributors as indicated by the @author tags.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM     http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off

if "%JAVA_HOME%" == "" (
    set "JAVA_EXE=java"
) else (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

set "mydir=%~dp0"

"%JAVA_EXE%" -cp "%mydir%\lib\*;%mydir%\conf" org.revapi.standalone.Main revapi.bat . %*

