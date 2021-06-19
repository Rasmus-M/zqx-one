xas99.py -R -S -L zaxxon.lst -w src/zaxxon.a99 -o zaxxon.obj
@IF %ERRORLEVEL% NEQ 0 GOTO :end

xas99.py -R -i -w src/zaxxon.a99 -o bin/ZAXXON

java -jar tools/ea5tocart.jar bin/ZAXXON "ZAXXON DEMO" > make.log

tools\pad.exe bin\map0.bin bin\map0-8k.bin 8192
tools\pad.exe bin\map1.bin bin\map1-8k.bin 8192
tools\pad.exe bin\map2.bin bin\map2-8k.bin 8192

xas99.py -b -w patterns/sprite-patterns-0.a99 -o bin/sprite-patterns-0.bin
xas99.py -b -w patterns/sprite-patterns-1.a99 -o bin/sprite-patterns-1.bin
xas99.py -b -w patterns/sprite-patterns-2.a99 -o bin/sprite-patterns-2.bin
xas99.py -b -w patterns/char-patterns0-1.a99 -o bin/char-patterns0-1.bin
xas99.py -b -w patterns/char-patterns1-1.a99 -o bin/char-patterns1-1.bin
xas99.py -b -w patterns/char-patterns1-2.a99 -o bin/char-patterns1-2.bin
xas99.py -b -w patterns/char-patterns1-3.a99 -o bin/char-patterns1-3.bin
xas99.py -b -w patterns/char-patterns2-1.a99 -o bin/char-patterns2-1.bin
xas99.py -b -w patterns/char-patterns2-2.a99 -o bin/char-patterns2-2.bin

copy /b bin\ZAXXON8.bin + ^
    bin\map1-8k.bin + ^
    bin\map2-8k.bin + ^
    bin\map0-8k.bin + ^
    bin\empty-2k.bin + bin\sprite-patterns-1.bin + ^
    bin\empty-1k.bin + bin\char-patterns1-1.bin + ^
    bin\empty-1k.bin + bin\char-patterns1-2.bin + ^
    bin\empty-1k.bin + bin\char-patterns1-3.bin + ^
    bin\empty-1k.bin + bin\char-patterns2-1.bin + ^
    bin\empty-1k.bin + bin\char-patterns2-2.bin + ^
    bin\empty-1k.bin + bin\char-patterns0-1.bin + ^
    bin\empty-2k.bin + bin\sprite-patterns-0.bin + ^
    bin\empty-2k.bin + bin\sprite-patterns-2.bin ^
    .\zaxxon8.bin

java -jar tools/CopyHeader.jar zaxxon8.bin 60 4 5 6

:end
