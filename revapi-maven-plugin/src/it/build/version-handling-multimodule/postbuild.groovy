/*
 * Copyright 2014-2017 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

File topDir = new File("target/it/build/version-handling-multimodule");
if (!topDir.exists()) {
    //the top level build might be running
    String path = "revapi-maven-plugin/" + topDir.getPath()
    topDir = new File(path)
}
File topPom = new File(topDir, "pom.xml");
File v2aDir = new File(topDir, "a");
File v2aPom = new File(v2aDir, "pom.xml");
File v2bDir = new File(topDir, "b");
File v2bPom = new File(v2bDir, "pom.xml");

checkVersion(topPom, "top", "2.0.0");
checkVersion(v2aPom, "v2a", "2.0.0", "2.0.0");
checkVersion(v2bPom, "v2b", "2.0.0", "2.0.0");
