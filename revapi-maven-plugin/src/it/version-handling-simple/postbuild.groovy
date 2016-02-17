void checkVersion(File pom, String... versions) throws Exception {
    int versionCount = 0;
    boolean found = false;
    pom.eachLine { line ->
        line = line.trim();

        if (line.startsWith("<version>") && versionCount < versions.length) {
            String version = versions[versionCount++];
            assert line.equals("<version>" + version + "</version>") :
                    "The " + versionCount + "th version tag in v2 pom should have been changed to " + version +
                            " but the line reads: " + line;
            found = true;
            return;
        }
    }

    if (!found) {
        throw new AssertionError("Failed to find the <version> tag in v2 pom.xml");
    }
}

File v2Dir = new File("target/it/build/version-handling-simple/v2");
File v2Pom = new File(v2Dir, "pom.xml");

checkVersion(v2Pom, "2.0.0");
