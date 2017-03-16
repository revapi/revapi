import java.util.regex.Pattern

static void checkVersion(File pom, String moduleVersion, String... versions) throws Exception {
    int versionCount = 0
    pom.eachLine { line ->
        line = line.trim()

        if (line.matches("^\\s*<version>.*") && versionCount < versions.length) {
            String version = versions[versionCount++]
            if (!line.matches("^\\s*<version>" + Pattern.quote(version) + "</version>.*")) {
                throw new AssertionError("The " + versionCount.toString() + "th version tag in " + moduleVersion
                        + " pom should be " + version + " but the line reads: " + line
                )
            }
        }
    }

    if (versionCount < versions.length) {
        throw new AssertionError("Failed to find the all the correct versions in the pom.xml of " + moduleVersion)
    }
}

static File file(String path) {
    File f = new File(path)
    if (!f.exists()) {
        //the top level build might be running
        path = "revapi-maven-plugin/" + f.getPath()
        f = new File(path)
    }

    return f
}

File v2Pom = file("target/it/build/version-handling-simple/v2/pom.xml")
checkVersion(v2Pom, "v2", "1.1.0")

File v3Pom = file("target/it/build/version-handling-simple/v3/artifact/pom.xml")

//1.0.2 is the version of the parent we're referencing in the pom
//1.1.0 here, too, because we run just update-versions on the v2 artifact. I.e. what we find in the repo is still
//1.0.1, not the version that update-versions changed the pom to.
checkVersion(v3Pom, "v3", "1.0.2", "1.1.0")
