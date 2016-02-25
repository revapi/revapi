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

package org.revapi.standalone;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.graph.selector.StaticDependencySelector;
import org.eclipse.aether.version.Version;
import org.revapi.maven.utils.ScopeDependencySelector;
import org.revapi.maven.utils.ScopeDependencyTraverser;

import org.jboss.forge.furnace.addons.AddonId;
import org.jboss.forge.furnace.impl.modules.providers.FurnaceContainerSpec;
import org.jboss.forge.furnace.manager.maven.MavenContainer;
import org.jboss.forge.furnace.manager.maven.addon.AddonInfoBuilder;
import org.jboss.forge.furnace.manager.maven.addon.MavenAddonDependencyResolver;
import org.jboss.forge.furnace.manager.maven.result.MavenResponseBuilder;
import org.jboss.forge.furnace.manager.maven.util.MavenRepositories;
import org.jboss.forge.furnace.manager.spi.AddonInfo;
import org.jboss.forge.furnace.manager.spi.Response;
import org.jboss.forge.furnace.versions.Versions;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class ExtensionResolver extends MavenAddonDependencyResolver {
    public static final String REVAPI_GROUP_ID = "org.revapi";
    public static final String REVAPI_ARTIFACT_ID = "revapi";

    public static final Set<String> ARTIFACTS_IN_LIB = new HashSet<>();

    public static void init() {
        FurnaceContainerSpec.paths.add("javax/annotation/processing");

        initLibJar("org.revapi:revapi", "org/revapi", "org/revapi/simple",
            "org/revapi/query", "org/revapi/configuration");

        initLibJar("com.google.code.findbugs:jsr305", "java/annotation", "javax/annotation/concurrent",
            "javax/annotation/meta");

        initLibJar("org.slf4j:slf4j-api", "org/slf4j", "org/slf4j/helpers", "org/slf4j/spi");

        initLibJar("org.jboss:jboss-dmr", "org/jboss/dmr");
    }

    private static void initLibJar(String groupArtifact, String... packages) {
        ARTIFACTS_IN_LIB.add(groupArtifact);
        for (String pkg : packages) {
            FurnaceContainerSpec.paths.add(pkg);
        }
    }

    private Settings settings;
    private final MavenContainer container = new MavenContainer();

    private final ScopeDependencyTraverser dependencyTraverser = new ScopeDependencyTraverser("compile", "provided") {
        @Override
        public boolean traverseDependency(Dependency dependency) {
            return super.traverseDependency(dependency) && !ExtensionResolver.isRevapiApi(dependency);
        }
    };
    private final ScopeDependencySelector dependencySelector = new ScopeDependencySelector("compile", "provided") {
        @Override
        public boolean selectDependency(Dependency dependency) {
            return super.selectDependency(dependency) && !ExtensionResolver.isRevapiApi(dependency);
        }
    };

    public ExtensionResolver() {
    }

    public static boolean isRevapiApi(Dependency dep) {
        return isRevapiApi(dep.getArtifact());
    }

    public static boolean isRevapiApi(Artifact artifact) {
        return ARTIFACTS_IN_LIB.contains(artifact.getGroupId() + ":" + artifact.getArtifactId());
    }

    @Override
    public AddonInfo resolveAddonDependencyHierarchy(AddonId addonId) {
        String coords = toMavenCoords(addonId);
        RepositorySystem system = container.getRepositorySystem();
        Settings settings = getSettings();
        DefaultRepositorySystemSession session = container.setupRepoSession(system, settings);

        DependencyNode dependencyNode = traverseAddonGraph(coords, system, settings, session);
        return fromNode(addonId, dependencyNode, system, settings, session);
    }

    @Override
    public Response<File[]> resolveResources(final AddonId addonId) {
        RepositorySystem system = container.getRepositorySystem();
        Settings settings = getSettings();
        DefaultRepositorySystemSession session = container.setupRepoSession(system, settings);
        final String mavenCoords = toMavenCoords(addonId);
        Artifact queryArtifact = new DefaultArtifact(mavenCoords);
        session.setDependencyTraverser(dependencyTraverser);
        session.setDependencySelector(dependencySelector);
        Dependency dependency = new Dependency(queryArtifact, null);

        List<RemoteRepository> repositories = MavenRepositories.getRemoteRepositories(container, settings);

        CollectRequest collectRequest = new CollectRequest(dependency, repositories);
        DependencyResult result;
        try {
            result = system.resolveDependencies(session, new DependencyRequest(collectRequest, null));
        } catch (DependencyResolutionException e) {
            throw new RuntimeException(e);
        }
        List<Exception> collectExceptions = result.getCollectExceptions();
        Set<File> files = new HashSet<File>();
        List<ArtifactResult> artifactResults = result.getArtifactResults();
        for (ArtifactResult artifactResult : artifactResults) {
            Artifact artifact = artifactResult.getArtifact();
            if (!mavenCoords.equals(artifact.toString())) {
                continue;
            }
            files.add(artifact.getFile());
        }
        return new MavenResponseBuilder<File[]>(files.toArray(new File[files.size()])).setExceptions(collectExceptions);
    }

    @Override
    public Response<AddonId[]> resolveVersions(final String addonName) {
        String addonNameSplit;
        String version;

        String[] split = addonName.split(",");
        if (split.length == 2) {
            addonNameSplit = split[0];
            version = split[1];
        } else {
            addonNameSplit = addonName;
            version = null;
        }
        RepositorySystem system = container.getRepositorySystem();
        Settings settings = getSettings();
        DefaultRepositorySystemSession session = container.setupRepoSession(system, settings);
        List<RemoteRepository> repositories = MavenRepositories.getRemoteRepositories(container, settings);
        VersionRangeResult versions = getVersions(system, settings, session, repositories, addonNameSplit, version);
        List<Exception> exceptions = versions.getExceptions();
        List<Version> versionsList = versions.getVersions();
        List<AddonId> addons = new ArrayList<AddonId>();
        List<AddonId> snapshots = new ArrayList<AddonId>();
        for (Version artifactVersion : versionsList) {
            AddonId addonId = AddonId.from(addonName, artifactVersion.toString());
            if (Versions.isSnapshot(addonId.getVersion())) {
                snapshots.add(addonId);
            } else {
                addons.add(addonId);
            }
        }
        if (addons.isEmpty()) {
            addons = snapshots;
        }
        return new MavenResponseBuilder<AddonId[]>(addons.toArray(new AddonId[addons.size()]))
            .setExceptions(exceptions);
    }

    @Override
    public Response<String> resolveAPIVersion(AddonId addonId) {
        RepositorySystem system = container.getRepositorySystem();
        Settings settings = getSettings();
        DefaultRepositorySystemSession session = container.setupRepoSession(system, settings);
        List<RemoteRepository> repositories = MavenRepositories.getRemoteRepositories(container, settings);
        String mavenCoords = toMavenCoords(addonId);
        Artifact queryArtifact = new DefaultArtifact(mavenCoords);

        session.setDependencyTraverser(new ScopeDependencyTraverser("compile", "provided"));
        session.setDependencySelector(new StaticDependencySelector(true));
        CollectRequest request = new CollectRequest(new Dependency(queryArtifact, null), repositories);
        CollectResult result;
        try {
            result = system.collectDependencies(session, request);
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
        List<Exception> exceptions = result.getExceptions();
        String apiVersion = findVersion(result.getRoot().getChildren(), REVAPI_GROUP_ID, REVAPI_ARTIFACT_ID);
        return new MavenResponseBuilder<>(apiVersion).setExceptions(exceptions);
    }

    private String findVersion(List<DependencyNode> dependencies, String groupId, String artifactId) {
        for (DependencyNode child : dependencies) {
            Artifact childArtifact = child.getArtifact();

            if (groupId.equals(childArtifact.getGroupId())
                && artifactId.equals(childArtifact.getArtifactId())) {
                return childArtifact.getBaseVersion();
            } else {
                String version = findVersion(child.getChildren(), groupId, artifactId);
                if (version != null) {
                    return version;
                }
            }
        }
        return null;
    }

    private VersionRangeResult getVersions(RepositorySystem system, Settings settings, RepositorySystemSession session,
        List<RemoteRepository> repositories,
        String addonName,
        String version) {
        try {
            String[] split = addonName.split(",");
            if (split.length == 2) {
                version = split[1];
            }
            if (version == null || version.isEmpty()) {
                version = "[,)";
            } else if (!version.matches("(\\(|\\[).*?(\\)|\\])")) {
                version = "[" + version + "]";
            }

            Artifact artifact = new DefaultArtifact(toMavenCoords(AddonId.from(addonName, version)));
            VersionRangeRequest rangeRequest = new VersionRangeRequest(artifact, repositories, null);
            VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);
            return rangeResult;
        } catch (Exception e) {
            throw new RuntimeException("Failed to look up versions for [" + addonName + "]", e);
        }
    }

    private AddonInfo fromNode(AddonId id, DependencyNode dependencyNode, RepositorySystem system, Settings settings,
        DefaultRepositorySystemSession session) {
        AddonInfoBuilder builder = AddonInfoBuilder.from(id);
        List<DependencyNode> children = dependencyNode.getChildren();
        for (DependencyNode child : children) {
            Dependency dependency = child.getDependency();
            Artifact artifact = dependency.getArtifact();

            if (!"jar".equals(artifact.getExtension())) {
                //skip non-jar dependencies - furnace doesn't work with them and they do not provide code (hopefully)
                continue;
            }

            AddonId childId = toAddonId(artifact);
            boolean exported = false;
            boolean optional = dependency.isOptional();
            String scope = dependency.getScope();
            if (scope != null && !optional) {
                if ("compile".equalsIgnoreCase(scope) || "runtime".equalsIgnoreCase(scope)) {
                    exported = true;
                } else if ("provided".equalsIgnoreCase(scope)) {
                    exported = false;
                }
            }
            DependencyNode node = traverseAddonGraph(toMavenCoords(childId), system, settings, session);
            AddonInfo addonInfo = fromNode(childId, node, system, settings, session);
            if (optional) {
                builder.addOptionalDependency(addonInfo, exported);
            } else {
                builder.addRequiredDependency(addonInfo, exported);
            }
        }
        return new LazyAddonInfo(this, builder);
    }

    private DependencyNode traverseAddonGraph(String coords, RepositorySystem system, Settings settings,
        DefaultRepositorySystemSession session) {
        session.setDependencyTraverser(dependencyTraverser);
        session.setDependencySelector(dependencySelector);
        Artifact queryArtifact = new DefaultArtifact(coords);

        List<RemoteRepository> repositories = MavenRepositories.getRemoteRepositories(container, settings);
        CollectRequest collectRequest = new CollectRequest(new Dependency(queryArtifact, null), repositories);

        CollectResult result;
        try {
            result = system.collectDependencies(session, collectRequest);
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
        return result.getRoot();
    }

    private String toMavenCoords(AddonId addonId) {
        String coords = addonId.getName() + ":jar:" + addonId.getVersion();
        return coords;
    }

    private AddonId toAddonId(Artifact artifact) {
        return AddonId.from(artifact.getGroupId() + ":" + artifact.getArtifactId(), artifact.getBaseVersion());
    }

    /**
     * @param settings the settings to set
     */
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    /**
     * @return the settings
     */
    public Settings getSettings() {
        return settings == null ? container.getSettings() : settings;
    }
}
