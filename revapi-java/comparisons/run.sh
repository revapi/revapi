#!/bin/sh

REVAPI_VERSION=0.3.7-SNAPSHOT
REVAPI_JAVA_VERSION=0.5.3-SNAPSHOT
REVAPI_REPORT_VERSION=0.3.5-SNAPSHOT
# first compile the stuff
mkdir code/v1/target
mkdir code/v2/target
$JAVA_HOME/bin/javac -d code/v1/target `find code/v1/src -name '*.java'`
$JAVA_HOME/bin/javac -d code/v2/target `find code/v2/src -name '*.java'`

#create jars
cd code/v1/target && $JAVA_HOME/bin/jar -cf ../../../v1.jar `ls`
cd ../../v2/target && $JAVA_HOME/bin/jar -cf ../../../v2.jar `ls`
cd ../../../

#--------------------------- sigtest -----------------------------------

# create the signature file of v1
start=$(($(date +%s%N)/1000000))
$JAVA_HOME/bin/java -jar $SIGTEST_DIR/sigtestdev.jar setup -classpath v1.jar:$JAVA_HOME/jre/lib/rt.jar -filename \
  ./v1.sig -package comparison > /dev/null 2>&1
$JAVA_HOME/bin/java -jar $SIGTEST_DIR/apicheck.jar -static -classpath v2.jar:$JAVA_HOME/jre/lib/rt.jar -filename \
  v1.sig -package comparison > sigtest.report 2>&1
end=$(($(date +%s%N)/1000000))
sigtest_time=$(($end - $start))

#-------------------------- clirr --------------------------------------
start=$(($(date +%s%N)/1000000))
$JAVA_HOME/bin/java -jar $CLIRR_DIR/clirr-core-0.7-SNAPSHOT-all.jar -o v1.jar -n v2.jar > clirr.report
end=$(($(date +%s%N)/1000000))
clirr_time=$(($end - $start))

#-------------------------- japicmp ------------------------------------
start=$(($(date +%s%N)/1000000))
$JAVA_HOME/bin/java -jar $JAPICMP_DIR/japicmp-0.6.0-jar-with-dependencies.jar -o v1.jar -n v2.jar > japicmp.report
end=$(($(date +%s%N)/1000000))
japicmp_time=$(($end - $start))

#-------------------------- revapi -------------------------------------
# install revapi-standalone
DIR="$PWD"
INST_DIR=`mktemp --tmpdir -d revapi.XXXX`
cd "$INST_DIR"
unzip "$DIR"/../../revapi-standalone/target/revapi-${REVAPI_VERSION}-standalone.zip
cd revapi-${REVAPI_VERSION}
# dry run so that revapi downloads its extensions and so we have a fair comparison of times
./revapi.sh -e org.revapi:revapi-java:${REVAPI_JAVA_VERSION},org.revapi:revapi-reporting-text:${REVAPI_REPORT_VERSION} \
  -o "$DIR"/v1.jar -n "$DIR"/v2.jar > /dev/null 2>&1
start=$(($(date +%s%N)/1000000))
./revapi.sh -e org.revapi:revapi-java:${REVAPI_JAVA_VERSION},org.revapi:revapi-reporting-text:${REVAPI_REPORT_VERSION} \
  -o "$DIR"/v1.jar -n "$DIR"/v2.jar -D revapi.reporter.text.minSeverity=NON_BREAKING > "$DIR"/revapi.report 2>/dev/null
end=$(($(date +%s%N)/1000000))
revapi_time=$(($end - $start))
cd "$DIR"

# recap
echo "Sigtest took ${sigtest_time}ms"
echo "Clirr took ${clirr_time}ms"
echo "japicmp took ${japicmp_time}ms"
echo "Revapi took ${revapi_time}ms"

# finally, delete the leftovers
rm -R code/v1/target
rm -R code/v2/target
#rm v1.jar
#rm v2.jar
rm v1.sig
rm -Rf "$INST_DIR"
