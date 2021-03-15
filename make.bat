xas99.py -R -S -L zaxxon.lst -w src/zaxxon.a99 -o zaxxon.obj
@IF %ERRORLEVEL% NEQ 0 GOTO :end

xas99.py -R -i -w src/zaxxon.a99 -o bin/ZAXXON

java -jar tools/ea5tocart.jar bin/ZAXXON "ZAXXON" > make.log

tools\pad.exe bin\map1.bin bin\map1-8k.bin 8192

xas99.py -b -w src/sprite-patterns.a99 -o bin/sprite-patterns.bin

copy /b bin\ZAXXON8.bin + ^
    bin\map1-8k.bin + ^
    bin\empty-2k.bin + bin\sprite-patterns.bin + ^
    bin\empty.bin + ^
    bin\empty.bin ^
    .\zaxxon8.bin

java -jar tools/CopyHeader.jar zaxxon8.bin 60 4

:end
