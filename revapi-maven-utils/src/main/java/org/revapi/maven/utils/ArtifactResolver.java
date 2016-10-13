/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.maven.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.version.Version;

/**
 * @author Lukas Krejci
 * @since 0.3.0
 */
public class ArtifactResolver {
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    public ArtifactResolver(RepositorySystem repositorySystem, RepositorySystemSession session,
        List<RemoteRepository> repositories) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = repositories;
    }


    public Artifact resolveArtifact(String gav) throws ArtifactResolutionException {
        return resolveArtifact(new DefaultArtifact(gav));
    }

    /**
     * Tries to find the newest version of the artifact that matches given regular expression.
     * The found version will be older than the {@code upToVersion} or newest available if {@code upToVersion} is null.
     *
     * @param gav the coordinates of the artifact. The version part is ignored
     * @param upToVersion the version up to which the versions will be matched
     * @param versionMatcher the matcher to match the version
     * @return
     * @throws VersionRangeResolutionException
     */
    public Artifact resolveNewestMatching(String gav, @Nullable String upToVersion, Pattern versionMatcher)
            throws VersionRangeResolutionException, ArtifactResolutionException {
        Artifact artifact = new DefaultArtifact(gav);
        artifact = artifact.setVersion(upToVersion == null ? "[,)" : "[," + upToVersion + ")");
        VersionRangeRequest rangeRequest = new VersionRangeRequest(artifact, repositories, null);

        VersionRangeResult result = repositorySystem.resolveVersionRange(session, rangeRequest);

        List<Version> versions = new ArrayList<>(result.getVersions());
        Collections.reverse(versions);

        for(Version v : versions) {
            if (versionMatcher.matcher(v.toString()).matches()) {
                return resolveArtifact(artifact.setVersion(v.toString()));
            }
        }


        throw new VersionRangeResolutionException(result) {
            @Override
            public String getMessage() {
                return "Failed to find a version of artifact '" + gav + "' that would correspond to an expression '"
                        + versionMatcher + "'. The versions found were: " + versions;
            }
        };
    }

    public CollectionResult collectTransitiveDeps(String... gavs) throws RepositoryException {

        Set<Artifact> artifacts = new HashSet<>();
        Set<Exception> failures = new HashSet<>();

        for (String gav : gavs) {
            collectTransitiveDeps(gav, artifacts, failures);
        }

        return new CollectionResult(failures, artifacts);
    }

    protected void collectTransitiveDeps(String gav, Set<Artifact> resolvedArtifacts, Set<Exception> failures)
            throws RepositoryException {

        final Artifact rootArtifact = resolveArtifact(gav);

        CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, null), repositories);

        DependencyRequest request = new DependencyRequest(collectRequest, null);

        DependencyResult result;

        try {
            result = repositorySystem.resolveDependencies(session, request);
        } catch (DependencyResolutionException dre) {
            result = dre.getResult();
        }

        result.getRoot().accept(new TreeDependencyVisitor(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                Dependency dep = node.getDependency();
                if (dep == null || dep.getArtifact().equals(rootArtifact)) {
                    return true;
                }

                resolvedArtifacts.add(dep.getArtifact());

                return true;
            }
        }));

        failures.addAll(result.getCollectExceptions());
    }

    public static final class CollectionResult {
        private final Set<Artifact> resolvedArtifacts;
        private final Set<Exception> failures;

        private CollectionResult(Set<Exception> failures, Set<Artifact> resolvedArtifacts) {
            this.failures = failures;
            this.resolvedArtifacts = resolvedArtifacts;
        }

        public Set<Exception> getFailures() {
            return failures;
        }

        public Set<Artifact> getResolvedArtifacts() {
            return resolvedArtifacts;
        }
    }

    private Artifact resolveArtifact(Artifact artifact) throws ArtifactResolutionException {
        ArtifactRequest request = new ArtifactRequest().setArtifact(artifact)
                .setRepositories(repositories);

        ArtifactResult result = repositorySystem.resolveArtifact(session, request);
        return result.getArtifact();
    }
}
