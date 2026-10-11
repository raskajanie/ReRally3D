@echo off
cd /d "%~dp0"
if exist "%~dp0tools\jdk\bin\java.exe" ( set "JAVA=%~dp0tools\jdk\bin\java.exe" ) else ( set "JAVA=java" )
"%JAVA%" -XX:+UseZGC -XX:+ZGenerational -XX:-UsePerfData -Xms384m -XX:MaxRAMPercentage=15 -XX:+AlwaysPreTouch -XX:-ZUncommit -XX:+UseStringDeduplication -Drally3d.parity=true -Dsun.java2d.noddraw=true -jar Rally3D-Java21-GPU.jar 2>>"%~dp0rally3d-native.log"
