package org.revapi.configuration;

import javax.annotation.Nonnull;

import org.revapi.AnalysisContext;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Configurable {

    void initialize(@Nonnull AnalysisContext analysisContext);
}
