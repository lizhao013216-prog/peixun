#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/backend"
exec java -jar peixun-demo.jar
