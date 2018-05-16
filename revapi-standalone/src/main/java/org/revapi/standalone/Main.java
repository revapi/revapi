/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.standalone;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.Nullable;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.DependencySpec;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleSpec;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;
import org.revapi.maven.utils.ArtifactResolver;
import org.revapi.maven.utils.ScopeDependencySelector;
import org.revapi.maven.utils.ScopeDependencyTraverser;
import org.revapi.simple.FileArchive;
import org.slf4j.LoggerFactory;
import pw.krejci.modules.maven.MavenBootstrap;
import pw.krejci.modules.maven.ModuleSpecController;
import pw.krejci.modules.maven.ProjectModule;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Main {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(Main.class);

    private static void usage(@Nullable String progName) {
        if (progName == null) {
            progName = "revapi.(sh|bat)";
        }

        String pad = "";
        for (int i = 0; i < progName.length(); ++i) {
            pad += " ";
        }

        System.out.println(progName +
                " [-u|-h] -e <GAV>[,<GAV>]* -o <FILE>[,<FILE>]* -n <FILE>[,<FILE>]* [-s <FILE>[,<FILE>]*] [-t <FILE>[,<FILE>]*] [-D<CONFIG_OPTION>=<VALUE>]* [-c <FILE>[,<FILE>]*] [-r <DIR>]");
        System.out.println();
        System.out.println(pad + " -u");
        System.out.println(pad + " -h");
        System.out.println(pad + " --usage");
        System.out.println(pad + " --help");
        System.out.println(pad + "     Prints this message and exits.");
        System.out.println(pad + " -e");
        System.out.println(pad + " --extensions=<GAV>[,<GAV>]*");
        System.out.println(pad + "     Comma-separated list of maven GAVs of revapi extensions.");
        System.out.println(pad + " -o");
        System.out.println(pad + " --old=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files of the old version of API");
        System.out.println(pad + " -a");
        System.out.println(pad + " --old-gavs=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of GAVs of the old version of API");
        System.out.println(pad + " -s");
        System.out.println(pad + " --old-supplementary=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files that supplement the old version of API");
        System.out.println(pad + " -n");
        System.out.println(pad + " --new=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files of the new version of API");
        System.out.println(pad + " -b");
        System.out.println(pad + " --new-gavs=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of GAVs of the new version of API");
        System.out.println(pad + " -t");
        System.out.println(pad + " --new-supplementary=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files that supplement the new version of API");
        System.out.println(pad + " -D");
        System.out
                .println(pad + "    A key-value pair representing a single configuration option of revapi or one of " +
                        "the loaded extensions");
        System.out.println(pad + " -c");
        System.out.println(pad + " --config-files=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of configuration files in JSON format.");
        System.out.println(pad + " -d");
        System.out.println(pad + " --cache-dir=<DIR>");
        System.out.println(pad + "    The location of local cache of extensions to use to locate artifacts. " +
                "Defaults to 'extensions' directory under revapi installation dir.");
        System.out.println(pad + " -r");
        System.out.println(pad + " --remote-repositories=<URL>[,<URL>]*");
        System.out.println(pad + "    The url of the remote Maven repository to use for artifact resolution. " +
                "Defaults to Maven Central (http://repo.maven.apache.org/maven2/).");
        System.out.println();
        System.out.println("You can specify the old API either using -o and -s where you specify the filesystem paths" +
                " to the archives and supplementary archives respectively or you can use -a to specify the GAVs of the" +
                " old API archives and the supplementary archives (i.e. their transitive dependencies) will be determined" +
                " automatically using Maven. But you cannot do both obviously.");
        System.out.println();
        System.out.println("Of course you can do the same for the new version of the API by using -n and -t for file" +
                " paths or -b for GAVs.");
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage(null);
            System.exit(1);
        }

        String scriptFileName = args[0];
        String baseDir = args[1];

        String[] realArgs = new String[args.length - 2];
        System.arraycopy(args, 2, realArgs, 0, realArgs.length);

        String[] extensionGAVs = null;
        String[] oldArchivePaths = null;
        String[] oldGavs = null;
        String[] newArchivePaths = null;
        String[] newGavs = null;
        String[] oldSupplementaryArchivePaths = null;
        String[] newSupplementaryArchivePaths = null;
        Map<String, String> additionalConfigOptions = new HashMap<>();
        String[] configFiles = null;
        File cacheDir = new File(baseDir, "cache");
        String[] remoteRepositoryUrls = null;

        LongOpt[] longOpts = new LongOpt[13];
        longOpts[0] = new LongOpt("usage", LongOpt.NO_ARGUMENT, null, 'u');
        longOpts[1] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
        longOpts[2] = new LongOpt("extensions", LongOpt.REQUIRED_ARGUMENT, null, 'e');
        longOpts[3] = new LongOpt("old", LongOpt.REQUIRED_ARGUMENT, null, 'o');
        longOpts[4] = new LongOpt("new", LongOpt.REQUIRED_ARGUMENT, null, 'n');
        longOpts[5] = new LongOpt("old-supplementary", LongOpt.REQUIRED_ARGUMENT, null, 's');
        longOpts[6] = new LongOpt("new-supplementary", LongOpt.REQUIRED_ARGUMENT, null, 't');
        longOpts[7] = new LongOpt("D", LongOpt.REQUIRED_ARGUMENT, null, 'D');
        longOpts[8] = new LongOpt("config-files", LongOpt.REQUIRED_ARGUMENT, null, 'c');
        longOpts[9] = new LongOpt("cache-dir", LongOpt.REQUIRED_ARGUMENT, null, 'd');
        longOpts[10] = new LongOpt("old-gavs", LongOpt.REQUIRED_ARGUMENT, null, 'a');
        longOpts[11] = new LongOpt("new-gavs", LongOpt.REQUIRED_ARGUMENT, null, 'b');
        longOpts[12] = new LongOpt("remote-repositories", LongOpt.REQUIRED_ARGUMENT, null, 'r');

        Getopt opts = new Getopt(scriptFileName, realArgs, "uhe:o:n:s:t:D:c:d:a:b:r:", longOpts);
        int c;
        while ((c = opts.getopt()) != -1) {
            switch (c) {
                case 'u':
                case 'h':
                    usage(scriptFileName);
                    System.exit(0);
                case 'e':
                    extensionGAVs = opts.getOptarg().split(",");
                    break;
                case 'o':
                    oldArchivePaths = opts.getOptarg().split(",");
                    break;
                case 'n':
                    newArchivePaths = opts.getOptarg().split(",");
                    break;
                case 's':
                    oldSupplementaryArchivePaths = opts.getOptarg().split(",");
                    break;
                case 't':
                    newSupplementaryArchivePaths = opts.getOptarg().split(",");
                    break;
                case 'c':
                    configFiles = opts.getOptarg().split(",");
                    break;
                case 'D':
                    String[] keyValue = opts.getOptarg().split("=");
                    additionalConfigOptions.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : null);
                    break;
                case 'd':
                    cacheDir = new File(opts.getOptarg());
                    break;
                case 'a':
                    oldGavs = opts.getOptarg().split(",");
                    break;
                case 'b':
                    newGavs = opts.getOptarg().split(",");
                    break;
                case 'r':
                    remoteRepositoryUrls = opts.getOptarg().split(",");
                    break;
                case ':':
                    System.err.println("Argument required for option " +
                            (char) opts.getOptopt());
                    break;
                case '?':
                    System.err.println("The option '" + (char) opts.getOptopt() +
                            "' is not valid");
                    System.exit(1);
                    break;
                default:
                    System.err.println("getopt() returned " + c);
                    System.exit(1);
                    break;
            }
        }

        if (extensionGAVs == null || (oldArchivePaths == null && oldGavs == null) ||
                (newArchivePaths == null && newGavs == null)) {

            usage(scriptFileName);
            System.exit(1);
        }

        final List<RemoteRepository> remoteRepositories = Collections.unmodifiableList(
            remoteRepositories(remoteRepositoryUrls == null ? new String[0] : remoteRepositoryUrls));

        List<FileArchive> oldArchives = null;
        List<FileArchive> newArchives = null;
        List<FileArchive> oldSupplementaryArchives = null;
        List<FileArchive> newSupplementaryArchives = null;

        LOG.info("Downloading checked archives");

        if (oldArchivePaths == null) {
            ArchivesAndSupplementaryArchives res = convertGavs(oldGavs, "Old API Maven artifact", cacheDir, remoteRepositories);
            oldArchives = res.archives;
            oldSupplementaryArchives = res.supplementaryArchives;
        } else {
            oldArchives = convertPaths(oldArchivePaths, "Old API files");
            oldSupplementaryArchives = oldSupplementaryArchivePaths == null ? emptyList() :
                    convertPaths(oldSupplementaryArchivePaths, "Old API supplementary files");
        }

        if (newArchivePaths == null) {
            ArchivesAndSupplementaryArchives res = convertGavs(newGavs, "New API Maven artifact", cacheDir, remoteRepositories);
            newArchives = res.archives;
            newSupplementaryArchives = res.supplementaryArchives;
        } else {
            newArchives = convertPaths(newArchivePaths, "New API files");
            newSupplementaryArchives = newSupplementaryArchivePaths == null ? emptyList() :
                    convertPaths(newSupplementaryArchivePaths, "New API supplementary files");
        }

        try {
            run(cacheDir, extensionGAVs, oldArchives, oldSupplementaryArchives, newArchives,
                    newSupplementaryArchives, configFiles, additionalConfigOptions,
                    remoteRepositories);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    @SuppressWarnings("ConstantConditions")
    private static void run(File cacheDir, String[] extensionGAVs, List<FileArchive> oldArchives,
            List<FileArchive> oldSupplementaryArchives, List<FileArchive> newArchives,
            List<FileArchive> newSupplementaryArchives, String[] configFiles, Map<String, String> additionalConfig,
            List<RemoteRepository> remoteRepositories)
            throws Exception {

        ProjectModule.Builder bld = ProjectModule.build();
        bld.localRepository(cacheDir);
        remoteRepositories.forEach(r -> bld.addRemoteRepository(r.getId(), r.getUrl()));

        if (extensionGAVs != null) {
            for (String gav : extensionGAVs) {
                bld.addDependency(gav);
            }
        }

        Properties libraryVersionsProps = new Properties();
        libraryVersionsProps.load(Main.class.getResourceAsStream("/library.versions"));
        Set<Artifact> globalArtifacts = libraryVersionsProps.stringPropertyNames().stream()
                .map(p -> {
                    String gav = p + ':' + libraryVersionsProps.get(p);
                    return new DefaultArtifact(gav);
                })
                .collect(toSet());

        bld.moduleSpecController(new ModuleSpecController() {
            private boolean override;
            private String currentModuleName;

            @Override
            public void start(String moduleName) {
                currentModuleName = moduleName;
            }

            @Override
            public DependencySpec modifyDependency(String dependencyName, DependencySpec original) {
                boolean overrideThis = false;
                Artifact a = new DefaultArtifact(dependencyName);

                for (Artifact ga : globalArtifacts) {
                    if (ga.getGroupId().equals(a.getGroupId()) && ga.getArtifactId().equals(a.getArtifactId())
                            && !ga.getVersion().equals(a.getVersion())) {
                        LOG.warn("Detected version conflict in dependencies of extension " + currentModuleName +
                                ". The extension depends on " + a + " while the CLI has " + ga + " on global" +
                                " classpath. This will likely cause problems.");
                    }
                }

                override = override || overrideThis;
                return overrideThis ? null : original;
            }

            @Override
            public void modify(ModuleSpec.Builder bld) {
                if (override) {
                    Set<String> revapiPaths = new HashSet<>(asList("org/revapi", "org/revapi/configuration",
                            "org/revapi/query", "org/revapi/simple"));

                    bld.addDependency(DependencySpec.createSystemDependencySpec(revapiPaths));
                    override = false;
                }
            }

            @Override
            public void end(String moduleName) {
                currentModuleName = null;
            }
        });

        LOG.info("Downloading extensions");

        Module project = bld.create();

        Revapi revapi = Revapi.builder()
                .withAllExtensionsFrom(project.getClassLoader())
                .withAllExtensionsFromThreadContextClassLoader().build();

        AnalysisContext.Builder ctxBld = AnalysisContext.builder(revapi)
                .withOldAPI(API.of(oldArchives).supportedBy(oldSupplementaryArchives).build())
                .withNewAPI(API.of(newArchives).supportedBy(newSupplementaryArchives).build());

        if (configFiles != null) {
            for (String cf : configFiles) {
                File f = new File(cf);
                checkCanRead(f, "Configuration file");

                try (FileInputStream is = new FileInputStream(f)) {
                    ctxBld.mergeConfigurationFromJSONStream(is);
                }
            }
        }

        for (Map.Entry<String, String> e : additionalConfig.entrySet()) {
            String[] keyPath = e.getKey().split("\\.");
            ModelNode additionalNode = new ModelNode();
            ModelNode key = additionalNode.get(keyPath);

            String value = e.getValue();
            if (value.startsWith("[") && value.endsWith("]")) {
                String[] values = value.substring(1, value.length() - 1).split("\\s*,\\s*");
                for (String v : values) {
                    key.add(v);
                }
            } else {
                key.set(value);
            }
            ctxBld.mergeConfiguration(additionalNode);
        }

        LOG.info("Starting analysis");

        long time = System.currentTimeMillis();

        try (AnalysisResult result = revapi.analyze(ctxBld.build())) {
            if (!result.isSuccess()) {
                throw result.getFailure();
            }
        } finally {
            LOG.info("Analysis took " + (System.currentTimeMillis() - time) + "ms.");
        }
    }

    private static List<FileArchive> convertPaths(String[] paths, String errorMessagePrefix) {
        List<FileArchive> archives = new ArrayList<>(paths.length);
        for (String path : paths) {
            File f = new File(path);
            checkCanRead(f, errorMessagePrefix);
            archives.add(new FileArchive(f));
        }

        return archives;
    }

    private static ArchivesAndSupplementaryArchives convertGavs(String[] gavs, String errorMessagePrefix,
            File localRepo, List<RemoteRepository> remoteRepositories) {
        RepositorySystem repositorySystem = MavenBootstrap.newRepositorySystem();
        DefaultRepositorySystemSession session = MavenBootstrap.newRepositorySystemSession(repositorySystem, localRepo);

        String[] topLevelScopes = new String[]{"compile", "provided"};
        String[] transitiveScopes = new String[]{"compile"};

        session.setDependencySelector(new ScopeDependencySelector(topLevelScopes, transitiveScopes));
        session.setDependencyTraverser(new ScopeDependencyTraverser(topLevelScopes, transitiveScopes));

        ArtifactResolver resolver = new ArtifactResolver(repositorySystem, session, remoteRepositories);

        List<FileArchive> archives = new ArrayList<>();
        List<FileArchive> supplementaryArchives = new ArrayList<>();

        for (String gav : gavs) {
            try {
                archives.add(new FileArchive(resolver.resolveArtifact(gav).getFile()));
                ArtifactResolver.CollectionResult res = resolver.collectTransitiveDeps(gav);

                res.getResolvedArtifacts().
                        forEach(a -> supplementaryArchives.add(new FileArchive(a.getFile())));
                if (!res.getFailures().isEmpty()) {
                    LOG.warn("Failed to resolve some transitive dependencies: " + res.getFailures().toString());
                }
            } catch (RepositoryException e) {
                throw new IllegalArgumentException(errorMessagePrefix + " " + e.getMessage());
            }
        }

        return new ArchivesAndSupplementaryArchives(archives, supplementaryArchives);
    }

    private static List<RemoteRepository> remoteRepositories(String[] customRepositoryUrls) {
        List<RemoteRepository> remoteRepositories = new ArrayList<>();

        for (int i = 0; i < customRepositoryUrls.length; i++) {
            String repositoryId = "@@custom-remote-repository-" + i + "@@";
            remoteRepositories.add(new RemoteRepository.Builder(repositoryId, "default", customRepositoryUrls[i]).build());
        }

        if (remoteRepositories.isEmpty()) {
            remoteRepositories.add(new RemoteRepository.Builder("@@forced-maven-central@@", "default", "http://repo.maven.apache.org/maven2/").build());
        }

        File localMaven = new File(new File(System.getProperties().getProperty("user.home"), ".m2"), "repository");
        remoteRepositories.add(new RemoteRepository.Builder("@@~/.m2/repository@@", "local", localMaven.toURI().toString()).build());

        return remoteRepositories;
    }

    private static void checkCanRead(File f, String errorMessagePrefix) throws IllegalArgumentException {
        if (!f.exists()) {
            throw new IllegalArgumentException(errorMessagePrefix + " '" + f.getAbsolutePath() + "' does not exist.");
        }

        if (!f.isFile() || !f.canRead()) {
            throw new IllegalArgumentException(
                    errorMessagePrefix + " '" + f.getAbsolutePath() + "' is not a file or cannot be read.");
        }
    }

    private static class ArchivesAndSupplementaryArchives {
        final List<FileArchive> archives;
        final List<FileArchive> supplementaryArchives;

        public ArchivesAndSupplementaryArchives(List<FileArchive> archives,
                List<FileArchive> supplementaryArchives) {
            this.archives = archives;
            this.supplementaryArchives = supplementaryArchives;
        }
    }
}


