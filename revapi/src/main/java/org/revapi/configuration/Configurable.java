package org.revapi.configuration;

import java.io.Reader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Configurable {

    @Nullable
    String[] getConfigurationRootPaths();

    @Nullable
    Reader getJSONSchema(@Nonnull String configurationRootPath);

    void initialize(@Nonnull AnalysisContext analysisContext);
}
