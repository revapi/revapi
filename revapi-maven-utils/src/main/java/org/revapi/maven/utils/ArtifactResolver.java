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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
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
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;

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
        DefaultArtifact artifact = new DefaultArtifact(gav);
        ArtifactRequest request = new ArtifactRequest().setArtifact(artifact)
            .setRepositories(repositories);

        ArtifactResult result = repositorySystem.resolveArtifact(session, request);
        return result.getArtifact();
    }

    public Set<Artifact> collectTransitiveDeps(String... gavs) throws RepositoryException {

        Set<Artifact> result = new HashSet<>();

        for (String gav : gavs) {
            collectTransitiveDeps(gav, result);
        }

        return result;
    }

    protected void collectTransitiveDeps(String gav, Set<Artifact> resolvedArtifacts) throws RepositoryException {

        final Artifact rootArtifact = resolveArtifact(gav);

        CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, null), repositories);

        DependencyRequest request = new DependencyRequest(collectRequest, null);

        DependencyResult result = repositorySystem.resolveDependencies(session, request);
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
    }
}
