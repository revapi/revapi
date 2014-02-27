package org.revapi.standalone;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.slf4j.bridge.SLF4JBridgeHandler;

import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.Addon;
import org.jboss.forge.furnace.addons.AddonId;
import org.jboss.forge.furnace.impl.FurnaceImpl;
import org.jboss.forge.furnace.impl.addons.AddonRepositoryImpl;
import org.jboss.forge.furnace.manager.AddonManager;
import org.jboss.forge.furnace.manager.impl.AddonManagerImpl;
import org.jboss.forge.furnace.manager.request.InstallRequest;
import org.jboss.forge.furnace.util.Addons;
import org.revapi.Revapi;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Main {

    private static void usage(@Nullable String progName) {
        if (progName == null) {
            progName = "revapi.(sh|bat)";
        }

        String pad = "";
        for (int i = 0; i < progName.length(); ++i) {
            pad += " ";
        }

        System.out.println(progName +
            " [-u|-h] -e <GAV>[,<GAV>]* -o <FILE>[,<FILE>]* -n <FILE>[,<FILE>]* [-s <FILE>[:<FILE>]*] [-t <FILE>[:<FILE>]*] [-D<CONFIG_OPTION>=<VALUE>]* [-c <FILE>[,<FILE>]*] [-r <DIR>]");
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
        System.out.println(pad + " -s");
        System.out.println(pad + " --old-supplementary=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files that supplement the old version of API");
        System.out.println(pad + " -n");
        System.out.println(pad + " --new=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files of the new version of API");
        System.out.println(pad + " -t");
        System.out.println(pad + " --new-supplementary=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of files that supplement the new version of API");
        System.out.println(pad + " -D");
        System.out
            .println(pad + "    A key-value pair representing a single configuration option of revapi or one of " +
                "the loaded extensions");
        System.out.println(pad + " -c");
        System.out.println(pad + " --config-files=<FILE>[,<FILE>]*");
        System.out.println(pad + "    Comma-separated list of configuration files in Java properties format.");
        System.out.println(pad + " -d");
        System.out.println(pad + " --cache-dir=<DIR>");
        System.out.println(pad + "    The location of local cache of extensions to use to locate artifacts. " +
            "Defaults to 'extensions' directory under revapi installation dir.");
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage(null);
            System.exit(1);
        }

        //redirect logging to slf4j - furnace is using jul
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        Logger.getLogger("").setLevel(Level.INFO);

        //and now continue

        String scriptFileName = args[0];
        String baseDir = args[1];

        String[] realArgs = new String[args.length - 2];
        System.arraycopy(args, 2, realArgs, 0, realArgs.length);

        String[] extensionGAVs = null;
        String[] oldArchivePaths = null;
        String[] newArchivePaths = null;
        String[] oldSupplementaryArchivePaths = null;
        String[] newSupplementaryArchivePaths = null;
        Map<String, String> additionalConfigOptions = new HashMap<>();
        String[] configFiles = null;
        File cacheDir = new File(baseDir, "extensions");

        LongOpt[] longOpts = new LongOpt[10];
        longOpts[0] = new LongOpt("usage", LongOpt.NO_ARGUMENT, null, 'u');
        longOpts[1] = new LongOpt("help", LongOpt.NO_ARGUMENT, null, 'h');
        longOpts[2] = new LongOpt("extensions", LongOpt.REQUIRED_ARGUMENT, null, 'c');
        longOpts[3] = new LongOpt("old", LongOpt.REQUIRED_ARGUMENT, null, 'o');
        longOpts[4] = new LongOpt("new", LongOpt.REQUIRED_ARGUMENT, null, 'n');
        longOpts[5] = new LongOpt("old-supplementary", LongOpt.REQUIRED_ARGUMENT, null, 's');
        longOpts[6] = new LongOpt("new-supplementary", LongOpt.REQUIRED_ARGUMENT, null, 't');
        longOpts[7] = null;
        longOpts[8] = new LongOpt("config-files", LongOpt.REQUIRED_ARGUMENT, null, 'c');
        longOpts[9] = new LongOpt("cache-dir", LongOpt.REQUIRED_ARGUMENT, null, 'd');

        Getopt opts = new Getopt(scriptFileName, realArgs, "ue:o:n:s:t:D:c:d:", longOpts);
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

        if (extensionGAVs == null || oldArchivePaths == null || newArchivePaths == null) {
            usage(scriptFileName);
            System.exit(1);
        }

        List<FileArchive> oldArchives = convertPaths(oldArchivePaths, "Old API file");
        List<FileArchive> newArchives = convertPaths(newArchivePaths, "New API file");
        List<FileArchive> oldSupplementaryArchives = oldSupplementaryArchivePaths == null ? null :
            convertPaths(oldSupplementaryArchivePaths, "Old API supplementary file");
        List<FileArchive> newSupplementaryArchives = newSupplementaryArchivePaths == null ? null :
            convertPaths(newSupplementaryArchivePaths, "New API supplementary file");

        Map<String, String> configuration = new HashMap<>();
        if (configFiles != null) {
            for (String configFile : configFiles) {
                File f = new File(configFile);
                checkCanRead(f, "Configuration file");
                Properties p = new Properties();
                try (FileInputStream is = new FileInputStream(f)) {
                    p.load(is);
                }

                configuration.putAll((Map<String, String>) (Map) p);
            }
        }
        configuration.putAll(additionalConfigOptions);

        run(new File(baseDir, "lib"), cacheDir, extensionGAVs, oldArchives, oldSupplementaryArchives, newArchives,
            newSupplementaryArchives,
            configuration);

        System.exit(0);
    }

    private static void run(File libDir, File cacheDir, String[] extensionGAVs, List<FileArchive> oldArchives,
        List<FileArchive> oldSupplementaryArchives, List<FileArchive> newArchives,
        List<FileArchive> newSupplementaryArchives, Map<String, String> configuration) throws Exception {

        ExtensionResolver.init();

        Furnace furnace = new FurnaceImpl();

        furnace.addRepository(AddonRepositoryImpl.forDirectory(furnace, cacheDir));

        furnace.startAsync();

        try {
            AddonManager manager = new AddonManagerImpl(furnace, new ExtensionResolver());

            for (String gav : extensionGAVs) {
                DefaultArtifact artifact = new DefaultArtifact(gav);
                String ga = artifact.getGroupId() + ":" + artifact.getArtifactId();
                String v = artifact.getBaseVersion();

                InstallRequest request = manager.install(AddonId.from(ga, v));
                request.perform();
            }

            Revapi.Builder builder = Revapi.builder();

            for (Addon addon : furnace.getAddonRegistry().getAddons()) {
                Addons.waitUntilStarted(addon);
                builder.withAllExtensionsFrom(addon.getClassLoader());
            }

            Revapi revapi = builder.withConfiguration(configuration).withAllExtensionsFromThreadContextClassLoader()
                .build();

            revapi.analyze(oldArchives, oldSupplementaryArchives, newArchives, newSupplementaryArchives);
        } finally {
            furnace.stop();
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

    private static void checkCanRead(File f, String errorMessagePrefix) throws IllegalArgumentException {
        if (!f.exists()) {
            throw new IllegalArgumentException(errorMessagePrefix + " '" + f.getAbsolutePath() + "' does not exist.");
        }

        if (!f.isFile() || !f.canRead()) {
            throw new IllegalArgumentException(
                errorMessagePrefix + " '" + f.getAbsolutePath() + "' is not a file or cannot be read.");
        }
    }
}


