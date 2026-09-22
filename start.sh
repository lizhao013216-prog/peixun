#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/backend"
exec java -jar peixun-demo.jar
