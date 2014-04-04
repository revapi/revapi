#!/bin/sh

echo "Checking CLIRR report..."
while read -r line; do
    cnt=`cat overall.report | grep -F "$line" | wc -l`;
    if [ $cnt -eq 0 ]; then
        echo "$line";
    fi
done < clirr.report

echo "Checking SIGTEST report..."
line_no=0
while read -r line; do
    #in sigtest report, only the lines starting with lowercase contain the difference descriptions
    isDif=`echo "$line" | grep -E '^[a-z]'`
    if [ -n "$isDif" ]; then
        cnt=`cat overall.report | grep -F "$line" | wc -l`;
        if [ $cnt -eq 0 ]; then
            echo "$line_no: $line";
        fi
    fi
    line_no=$(($line_no + 1))
done < sigtest.report
