@echo off
echo This can cause high system load.
echo To abort press CTRL+C.
echo ***
pause  
java -cp classes de.anomic.server.serverSystem -m
pause