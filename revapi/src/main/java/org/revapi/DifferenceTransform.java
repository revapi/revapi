/*
 * Copyright 2014-2019 Lukas Krejci
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
package org.revapi;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;

/**
 * A difference transform may elect to transform certain kinds of differences into other kinds. This comes useful
 * in custom extensions that want to "modify the behavior" of other extensions by consuming and transforming the
 * differences found by the other extensions into something else.
 *
 * <p>The {@link #close()} is not called if there is no prior call to {@link #initialize(AnalysisContext)}. Do all your
 * resource acquisition in initialize, not during the construction of the object.
 *
 * <p><b>NOTE</b>: for more complex transformations that require contextual knowledge about the traversal (like when
 * you need to employ an element matcher during the transformation), you may want to re-implement the default methods
 * for the element-pair tree traversal of the elements. The traversal happens prior to any transformations.
 *
 * @param <T> the type of the element expected in the {@code transform} method. Note that you need to be careful about
 *            this type because the types of the elements passed to {@code transform} depend on the differences that the
 *            transform is interested in. Thus you may end up with {@code ClassCastException}s if you're not careful.
 *            This type needs to be cast-able to the type of all possible elements that the handled differences can
 *            apply to. If in doubt, just use {@link org.revapi.Element} which is guaranteed to work.
 * @author Lukas Krejci
 * @since 0.1
 */
public interface DifferenceTransform<T extends Element> extends AutoCloseable, Configurable {

    /**
     * @return The list of regexes to match the difference codes this transform can handle.
     */
    @Nonnull
    Pattern[] getDifferenceCodePatterns();

    /**
     * Returns a transformed version of the difference. If this method returns null, the difference is
     * discarded and not reported. Therefore, if you don't want to transform a difference, just return it.
     *
     * <p>The code of the supplied difference will match at least one of the regexes returned from the {@link
     * #getDifferenceCodePatterns()} method.
     *
     * @param oldElement the old differing element
     * @param newElement the new differing element
     * @param difference the difference description
     *
     * @return the transformed difference or the passed in difference if no transformation necessary or null if the
     * difference should be discarded
     *
     * @deprecated use {@link #tryTransform(Element, Element, Difference)} which offers richer possibilities
     */
    @Deprecated
    @Nullable
    default Difference transform(@Nullable T oldElement, @Nullable T newElement, @Nonnull Difference difference) {
        return difference;
    }

    /**
     * Tries to transform the difference into some other one(s) given the old and new elements.
     *
     * <p>This method is only called on differences matching the {@link #getDifferenceCodePatterns()}. The elements are
     * cast into the required type, but that can result in the class cast exception if the implementation is not careful
     * enough and wants to react on a difference code that matches elements which are incompatible with the required
     * type.
     *
     * <p>Note that this method can be called repeatedly for the same element pair if there are multiple transformations
     * operating on the two elements. Also note that this method is called only after
     * the {@link #startElements(Element, Element)} and the {@link #endElements(Element, Element)} have been called on
     * all element pairs and the {@link #endTraversal(ApiAnalyzer)} ended the API forests traversal of the api analyzer.
     * Only after this method has been called on all element pairs from the traversal,
     * the {@link #startTraversal(ApiAnalyzer, ArchiveAnalyzer, ArchiveAnalyzer) started} is called for the next API
     * analyzer.
     *
     * @param oldElement the old element, if any, being compared to the new element
     * @param newElement the new element, if any, being compared to the old element
     * @param difference the difference to transform
     * @return the transformation result.
     */
    default TransformationResult tryTransform(@Nullable T oldElement, @Nullable T newElement, Difference difference) {
        Difference diff = transform(oldElement, newElement, difference);

        if (diff == null) {
            return TransformationResult.discard();
        } else if (diff == difference) {
            return TransformationResult.keep();
        } else {
            return TransformationResult.replaceWith(diff);
        }
    }

    /**
     * Called when Revapi is about to start traversing the elements provided by the given archive analyzers.
     *
     * <p>This method can invalidate the transform early if it finds out that it cannot possibly work with the api
     * analyzer and the archive analyzers. Simple transforms that only work with generic elements will likely work with
     * any API analyzer, but there may be more specialized transforms that can know upfront whether or not they will be
     * able to work with elements from the supplied analyzers and can opt out straight away reducing the required
     * processing during the API analysis.
     *
     * @param apiAnalyzer the api analyzer currently analyzing the APIs
     * @param oldArchiveAnalyzer the archive analyzer used for obtaining the element forest of the old API
     * @param newArchiveAnalyzer the archive analyzer used for obtaining the element forest of the new API
     * @return true if the transform can work with the provided api analyzer and archive analyzers, false otherwise
     */
    default boolean startTraversal(ApiAnalyzer apiAnalyzer, ArchiveAnalyzer oldArchiveAnalyzer,
            ArchiveAnalyzer newArchiveAnalyzer) {
        return true;
    }

    /**
     * Called when the analyzer starts to traverse the two elements. The traversal is performed in
     * the depth-first manner, so all child elements of the two elements are processed before
     * the {@link #endElements(Element, Element)} method is called with the same pair of elements
     *
     * @param oldElement the old element, if any, being compared to the new element
     * @param newElement the new element, if any, being compared to the old element
     *
     * @return true if the difference requires descending into the children even if one of the elements is not present
     * (null), false otherwise
     */
    default boolean startElements(@Nullable Element oldElement, @Nullable Element newElement) {
        return false;
    }

    /**
     * Called when the analyzer finished the traversal of the two elements, that is after
     * the {@link #startElements(Element, Element)} and this method has been called on all the children of the two
     * elements.
     *
     * @param oldElement the old element, if any, being compared to the new element
     * @param newElement the new element, if any, being compared to the old element
     */
    default void endElements(@Nullable Element oldElement, @Nullable Element newElement) {
    }

    /**
     * Called when the analysis finished traversing all the elements from the given api analyzer.
     *
     * @param apiAnalyzer the api analyzer that was analyzing the APIs
     * @
     */
    default void endTraversal(ApiAnalyzer apiAnalyzer) {
    }

    /**
     * Called after everything traversed using the provided analyzer has been transformed. This can be used to clean
     * up resources used during the analysis using the provided analyzer. After this method was called a new "round"
     * of analysis may start using a difference API analyzer. This is advertized to the transforms by calling
     * {@link #startTraversal(ApiAnalyzer, ArchiveAnalyzer, ArchiveAnalyzer)} again.
     *
     * @param apiAnalyzer the API analyzer used for the currently ending analysis
     */
    default void endAnalysis(ApiAnalyzer apiAnalyzer) {
    }
}
