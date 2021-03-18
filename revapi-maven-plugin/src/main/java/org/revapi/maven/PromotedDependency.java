/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.maven;

import java.util.regex.Pattern;

import org.eclipse.aether.artifact.Artifact;

public class PromotedDependency {
    private String groupId;
    private String artifactId;
    private String type;
    private String classifier;
    private String version;

    private Pattern groupIdPattern;
    private Pattern artifactIdPattern;
    private Pattern typePattern;
    private Pattern classifierPattern;
    private Pattern versionPattern;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
        this.groupIdPattern = asPattern(groupId);
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        this.artifactIdPattern = asPattern(artifactId);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
        this.typePattern = asPattern(type);
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
        this.classifierPattern = asPattern(classifier);
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
        this.versionPattern = asPattern(version);
    }

    public boolean matches(Artifact artifact) {
        return matches(groupIdPattern, artifact.getGroupId()) && matches(artifactIdPattern, artifact.getArtifactId())
                && matches(typePattern, artifact.getExtension()) && matches(classifierPattern, artifact.getClassifier())
                && matches(versionPattern, artifact.getBaseVersion());
    }

    private static boolean matches(Pattern pattern, String string) {
        string = string == null ? "" : string;
        if (pattern == null) {
            return true;
        } else {
            return pattern.matcher(string).matches();
        }
    }

    private static Pattern asPattern(String value) {
        if (value == null) {
            return null;
        } else if (value.charAt(0) == '/' && value.charAt(value.length() - 1) == '/') {
            return Pattern.compile(value.substring(1, value.length() - 1));
        } else {
            return Pattern.compile(Pattern.quote(value));
        }
    }
}
