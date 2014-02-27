#!/bin/sh

BASE_DIR=`dirname "$0"`

CLASSPATH="$BASE_DIR"/conf
for jar in `ls "$BASE_DIR"/lib/*.jar`; do
    CLASSPATH="$CLASSPATH:$jar"
done

if [ -z "$JAVA_HOME" ]; then
    JAVA_EXE="java"
else
    JAVA_EXE="$JAVA_HOME/bin/java"
fi

"$JAVA_EXE" -cp $CLASSPATH $JAVA_OPTS org.revapi.standalone.Main revapi.sh "$BASE_DIR" $@
