package org.revapi.ant;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Locale;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Revapi;

/**
 * @author Lukas Krejci
 * @since 0.2
 */
@SuppressWarnings("UnusedDeclaration")
public final class RevapiTask extends Task {

    private FileSet oldArchives;
    private FileSet oldSupplementaryArchives;
    private FileSet newArchives;
    private FileSet newSupplementaryArchives;

    private Path revapiClasspath;
    private String configuration;

    private String breakingSeverity = FailSeverity.potentiallyBreaking.name();

    public void addOldArchives(FileSet oldArchives) {
        this.oldArchives = oldArchives;
    }

    public void addOldSupplementaryArchives(FileSet oldSupplementaryArchives) {
        this.oldSupplementaryArchives = oldSupplementaryArchives;
    }

    public void addNewArchives(FileSet newArchives) {
        this.newArchives = newArchives;
    }

    public void addNewSupplementaryArchives(FileSet newSupplementaryArchives) {
        this.newSupplementaryArchives = newSupplementaryArchives;
    }

    public void setRevapiClasspath(Path revapiClasspath) {
        this.revapiClasspath = revapiClasspath;
    }

    public void addRevapiClasspath(Path revapiClasspath) {
        this.revapiClasspath = revapiClasspath;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public void setBreakingSeverity(String breakingSeverity) {
        this.breakingSeverity = breakingSeverity;
    }

    @Override
    public void execute() throws BuildException {

        Revapi revapi = initRevapi();
        AnalysisContext context = initAnalysisContext();

        log("Running API analysis");
        log("Old API: " + context.getOldApi().toString());
        log("New API: " + context.getNewApi().toString());

        try(AnalysisResult res = revapi.analyze(context)) {
            res.throwIfFailed();
        } catch (Exception e) {
            throw new BuildException("API analysis failed.", e);
        }
    }

    private Revapi initRevapi() throws BuildException {
        Revapi.Builder revapiBuilder = Revapi.builder();

        if (revapiClasspath != null) {
            String[] elements = revapiClasspath.list();

            URL[] urls = new URL[elements.length];
            for (int i = 0; i < elements.length; ++i) {
                try {
                    urls[i] = new File(elements[i]).toURI().toURL();
                } catch (MalformedURLException | IllegalArgumentException | SecurityException e) {
                    throw new BuildException("Could not compose revapi classpath: " + e.getMessage(), e);
                }
            }

            revapiBuilder.withAllExtensionsFrom(new URLClassLoader(urls, getClass().getClassLoader()));
        } else {
            revapiBuilder.withAllExtensionsFrom(getClass().getClassLoader());
        }

        //always add the Ant reporter, so that we get stuff in the Ant log
        revapiBuilder.withReporters(AntReporter.class);

        return revapiBuilder.build();
    }

    private AnalysisContext initAnalysisContext() {
        API oldApi = API.of(FileArchive.from(oldArchives))
            .addSupportArchives(FileArchive.from(oldSupplementaryArchives)).build();

        API newApi = API.of(FileArchive.from(newArchives))
            .addSupportArchives(FileArchive.from(newSupplementaryArchives)).build();

        AnalysisContext.Builder builder = AnalysisContext.builder().withOldAPI(oldApi).withNewAPI(newApi)
            .withLocale(Locale.getDefault());

        if (configuration != null) {
            builder.withConfigurationFromJSON(configuration);
        }

        builder.withData(AntReporter.ANT_REPORTER_LOGGER_KEY, this);
        builder.withData(AntReporter.MIN_SEVERITY_KEY, FailSeverity.valueOf(breakingSeverity).asDifferenceSeverity());

        return builder.build();
    }
}
