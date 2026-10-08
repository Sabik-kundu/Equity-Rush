#!/bin/sh
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/bullrun/*.java && java -Dfile.encoding=UTF-8 -cp out bullrun.Main
