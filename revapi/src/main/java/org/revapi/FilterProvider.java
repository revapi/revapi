package org.revapi;

import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;

/**
 * Forest filter helps the {@link ArchiveAnalyzer} filter the resulting element forest while it is being
 * created.
 * <p>
 * It is guaranteed that the elements will be called in an hierarchical order, e.g. parents will be filtered
 * before their children.
 */
public interface FilterProvider extends Configurable, AutoCloseable {
    /**
     * Creates a new filter specifically for use with the provided analyzer. Can return null if this forest filter
     * cannot understand elements provided by the analyzer.
     *
     * @param archiveAnalyzer the archive analyzer to produce a new filter for
     * @return a new filter for given analyzer or null if this forest filter is not compatible with the analyzer
     */
    @Nullable
    TreeFilter filterFor(ArchiveAnalyzer archiveAnalyzer);
}
