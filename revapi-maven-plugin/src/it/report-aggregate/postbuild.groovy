def lines = 0
new File(basedir, "target/aggregate-report.txt").eachLine {
    lines++
}

if (lines != 8) {
    throw new AssertionError("Expected 8 lines of output in the aggregate report but found: " + lines)
}
