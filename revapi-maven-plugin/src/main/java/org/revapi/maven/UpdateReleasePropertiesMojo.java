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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * @author Lukas Krejci
 * 
 * @since 0.4.0
 */
@Mojo(name = "update-release-properties", requiresDirectInvocation = true)
@Execute(phase = LifecyclePhase.PACKAGE)
public class UpdateReleasePropertiesMojo extends AbstractVersionModifyingMojo {

    /**
     * The suffix to add to the release version, e.g. ".GA", ".Final", "-release". Must include some kind of separator
     * as the first character.
     */
    @Parameter(name = "releaseVersionSuffix", property = "revapi.releaseVersionSuffix")
    private String releaseVersionSuffix;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        setPreserveSuffix(false);
        setReplacementSuffix(releaseVersionSuffix);
        super.execute();
    }

    @Override
    void updateProjectVersion(MavenProject project, Version version) throws MojoExecutionException {
        File rpf = getReleasePropertiesFile();
        Properties ps = readProperties(rpf);

        String relProp;
        String devProp;

        if (isSingleVersionForAllModules()) {
            relProp = "project.rel." + project.getGroupId() + ":" + project.getArtifactId();
            devProp = "project.dev." + project.getGroupId() + ":" + project.getArtifactId();
        } else {
            relProp = "releaseVersion";
            devProp = "developmentVersion";
        }

        ps.setProperty(relProp, version.toString());

        Version dev = version.clone();
        dev.setPatch(dev.getPatch() + 1);
        dev.setSuffix(releaseVersionSuffix == null ? "SNAPSHOT" : releaseVersionSuffix + "-SNAPSHOT");

        ps.setProperty(devProp, dev.toString());

        try (FileOutputStream out = new FileOutputStream(rpf)) {
            ps.store(out, null);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write to the release.properties file.", e);
        }
    }

    @Override
    void updateProjectParentVersion(MavenProject project, Version version) throws MojoExecutionException {
        // we don't do this here
    }

    private File getReleasePropertiesFile() {
        return new File(mavenSession.getExecutionRootDirectory(), "release.properties");
    }

    private Properties readProperties(File file) {
        Properties ps = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            ps.load(in);
            return ps;
        } catch (FileNotFoundException e) {
            return ps;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the release.properties file.", e);
        }
    }
}
