/*
 * Copyright 2017 Lukas Krejci
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
 * limitations under the License
 */

file = { name ->
    f = new File((String) name)
    if (!f.exists()) {
        f = new File("revapi-maven-plugin", f.getPath())
    }

    return f
}

File pomXml = file("target/it/build/json-to-xml/pom.xml")
pomXml.eachLine { line ->
    assert !line.contains("{")
    assert !line.contains("conf1.json")
    assert !line.contains("conf2.json")
    assert !line.contains("conf3.xml")
}

File conf1Xml = file("target/it/build/json-to-xml/conf1.xml")
File conf2Xml = file("target/it/build/json-to-xml/subdir/conf2.xml")
File conf3Xml = file("target/it/build/json-to-xml/conf3.xml")

assert conf1Xml.exists()
assert conf2Xml.exists()
assert !conf3Xml.exists()
