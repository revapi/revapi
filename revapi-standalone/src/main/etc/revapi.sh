#!/bin/sh
#
# Copyright 2014-2017 Lukas Krejci
# and other contributors as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


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
