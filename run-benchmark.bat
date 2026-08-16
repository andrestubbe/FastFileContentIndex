@echo off
echo Building Main Project...
call mvn clean install -DskipTests -q

echo Building Benchmark Uber-JAR...
cd examples\Benchmark
call mvn clean package -DskipTests -q

echo Running JMH Benchmarks...
java -jar target\benchmarks.jar -f 1 -i 3 -wi 2 -w 1s -r 1s

cd ..\..
pause
