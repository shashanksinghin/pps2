#!/bin/sh
TZ=IN; export TZ

. /app/setEnv.sh

java $JAVA_VM_OPTS -jar app.jar