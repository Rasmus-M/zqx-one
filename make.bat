xas99.py -R -S -L zaxxon.lst -w src/zaxxon.a99 -o zaxxon.obj
@IF %ERRORLEVEL% NEQ 0 GOTO :end

xas99.py -R -i -w src/zaxxon.a99 -o bin/ZAXXON

java -jar tools/ea5tocart.jar bin/ZAXXON "ZAXXON" > make.log

copy /b bin\ZAXXON8.bin .\zaxxon8.bin

:end
