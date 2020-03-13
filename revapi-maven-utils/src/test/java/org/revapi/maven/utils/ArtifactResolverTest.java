/*
 * Copyright 2014-2020 Lukas Krejci
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
package org.revapi.maven.utils;

import static java.util.Collections.singletonList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.revapi.maven.utils.ArtifactResolver.getRevapiDependencySelector;
import static org.revapi.maven.utils.ArtifactResolver.getRevapiDependencyTraverser;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.junit.Test;
import org.revapi.maven.utils.ArtifactResolver.CollectionResult;

public class ArtifactResolverTest {

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
            File localRepoLocation) {

        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(localRepoLocation);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        session.setTransferListener(new AbstractTransferListener() {});
        session.setRepositoryListener(new AbstractRepositoryListener() {});

        return session;
    }

    private static List<RemoteRepository> repos() {
        return singletonList(
                new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());
    }

    private static File localRepo() {
        return new File(System.getProperty("repo.path"));
    }

    private ArtifactResolver getResolver(boolean resolveProvidedDependencies, boolean resolveTransitiveProvidedDependencies) {
        RepositorySystem repositorySystem = newRepositorySystem();
        DefaultRepositorySystemSession session = newRepositorySystemSession(repositorySystem, localRepo());

        session.setDependencySelector(getRevapiDependencySelector(resolveProvidedDependencies, resolveTransitiveProvidedDependencies));
        session.setDependencyTraverser(getRevapiDependencyTraverser(resolveProvidedDependencies, resolveTransitiveProvidedDependencies));

        return new ArtifactResolver(repositorySystem, session, repos());
    }

    @Test
    public void testResolvesCompileScopes() throws RepositoryException {
        ArtifactResolver resolver = getResolver(false, false);

        CollectionResult res = resolver.collectTransitiveDeps("used-scopes:root:0");

        assertTrue(res.getFailures().isEmpty());
        Set<Artifact> artifacts = res.getResolvedArtifacts();

        assertEquals(2, artifacts.size());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("compile"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-compile-compile"::equals).count());
    }

    @Test
    public void testResolvesCompileAndTopLevelProvidedScopes() throws RepositoryException {
        ArtifactResolver resolver = getResolver(true, false);

        CollectionResult res = resolver.collectTransitiveDeps("used-scopes:root:0");

        assertTrue(res.getFailures().isEmpty());
        Set<Artifact> artifacts = res.getResolvedArtifacts();

        assertEquals(4, artifacts.size());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("compile"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-compile-compile"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("provided"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-provided-compile"::equals).count());
    }

    @Test
    public void testResolvesCompileAndTransitiveProvidedScopes() throws RepositoryException {
        ArtifactResolver resolver = getResolver(true, true);

        CollectionResult res = resolver.collectTransitiveDeps("used-scopes:root:0");

        assertTrue(res.getFailures().isEmpty());
        Set<Artifact> artifacts = res.getResolvedArtifacts();

        assertEquals(6, artifacts.size());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("compile"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-compile-compile"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-compile-provided"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("provided"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-provided-compile"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("deep-provided-provided"::equals).count());
    }

    @Test
    public void testHonorsExclusions() throws RepositoryException {
        ArtifactResolver resolver = getResolver(false, false);

        CollectionResult res = resolver.collectTransitiveDeps("exclusions:root:0");

        assertTrue(res.getFailures().isEmpty());
        Set<Artifact> artifacts = res.getResolvedArtifacts();

        assertEquals(3, artifacts.size());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("dep"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("included"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("included-included"::equals).count());
    }

    @Test
    public void testIgnoresNonCompileAndProvidedScopes() throws RepositoryException {
        ArtifactResolver resolver = getResolver(false, false);

        CollectionResult res = resolver.collectTransitiveDeps("ignored-scopes:root:0");

        assertTrue(res.getFailures().isEmpty());
        Set<Artifact> artifacts = res.getResolvedArtifacts();

        assertEquals(2, artifacts.size());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("included"::equals).count());
        assertEquals(1, artifacts.stream().map(Artifact::getArtifactId).filter("optional"::equals).count());
    }
}
