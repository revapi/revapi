package org.revapi.java.test.support;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import org.junit.Rule;
import org.junit.Test;

public class JarTest {

    @Rule
    public Jar jar = new Jar();

    @Test
    public void shouldBuildJarFromClassPath() throws Exception {
        Jar.BuildOutput output = jar.from().classPathSources(null,"Root.java").build();
        assertTrue(output.jarFile().exists());

        JarFile jf = new JarFile(output.jarFile());
        assertNotNull(jf.getJarEntry("Root.class"));
    }

    @Test
    public void shouldResolveAgainstCustomRootInClassPath() throws Exception {
        Jar.BuildOutput output = jar.from()
                .classPathSources("/sub-directory/", "pkg/ClassInPackage.java")
                .classPathResources("/sub-directory/", "META-INF/file-in-meta-inf.txt")
                .build();

        assertTrue(output.jarFile().exists());

        JarFile jf = new JarFile(output.jarFile());

        assertNotNull(jf.getJarEntry("pkg/ClassInPackage.class"));
        assertNotNull(jf.getJarEntry("META-INF/file-in-meta-inf.txt"));
    }

    @Test
    public void shouldBuildJarsFromFiles() throws Exception {
        Path root = Files.createTempDirectory("JarTest");
        Path metaInf = Files.createDirectory(root.resolve("META-INF"));
        Path pkg = Files.createDirectory(root.resolve("pkg"));

        Files.copy(getClass().getResourceAsStream("/sub-directory/pkg/ClassInPackage.java"),
                pkg.resolve("ClassInPackage.java"));
        Files.copy(getClass().getResourceAsStream("/sub-directory/META-INF/file-in-meta-inf.txt"),
                metaInf.resolve("file-in-meta-inf.txt"));

        Jar.BuildOutput output = jar.from()
                .fileSources(root.toFile(), root.relativize(pkg.resolve("ClassInPackage.java")).toFile())
                .fileResources(root.toFile(), root.relativize(metaInf.resolve("file-in-meta-inf.txt")).toFile())
                .build();

        assertTrue(output.jarFile().exists());

        JarFile jf = new JarFile(output.jarFile());

        assertNotNull(jf.getJarEntry("pkg/ClassInPackage.class"));
        assertNotNull(jf.getJarEntry("META-INF/file-in-meta-inf.txt"));
    }

    @Test
    public void shouldProvideFunctionalTypeEnvironment() throws Exception {
        Jar.BuildOutput output = jar.from()
                .classPathSources("/sub-directory/", "pkg/ClassInPackage.java")
                .classPathResources("/sub-directory/", "META-INF/file-in-meta-inf.txt")
                .build();

        Jar.Environment env = output.analyze();

        assertNotNull(env.elements().getTypeElement("pkg.ClassInPackage"));
    }
}
