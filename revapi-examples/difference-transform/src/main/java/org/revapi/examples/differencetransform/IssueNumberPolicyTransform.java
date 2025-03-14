/*
 * Copyright 2014-2025 Lukas Krejci
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
package org.revapi.examples.differencetransform;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.TransformationResult;
import org.revapi.base.BaseDifferenceTransform;

/**
 * Once the API trees of the old and new APIs are established, they compared using a
 * {@link org.revapi.DifferenceAnalyzer}. The difference analyzer is obtained from the {@link org.revapi.ApiAnalyzer}.
 *
 * This difference analyzer produces an initial set of differences between elements that are specific to the type of API
 * the API analyzer checks.
 *
 * Each difference report from the initial set is processed by all transforms. Each transform is given a chance to
 * transform the differences from the original report and their intended changes are gathered. After the "round", the
 * changes are applied to the set of differences for the element pair and all the transforms can again react on the new
 * set of differences. This repeats until no further changes are made to the set by the transforms.
 *
 * You can spot in the explanation above that there is a good chance for an infinite loop if two or more transformers
 * form a "cycle", meaning that a difference that produced by one transformer is changed again into the original by a
 * second transformer, which again is transformed by the first transformer, etc.
 *
 * Revapi guards against this simply by doing at most 1{nbsp}000{nbsp}000 such iterations and then throwing an error.
 *
 * In addition to being asked to transform the found differences, the transforms are also informed about the progress of
 * the parallel traversal of the 2 API trees. This makes sure that the transforms can update their internal state during
 * the traversal without needing to again query (in some shape or form) the API tree for more information once asked to
 * perform the transformations.
 *
 * The {@link org.revapi.DifferenceAnalyzer} tries to explain the roles of the methods during the traversal and
 * transformation in detail.
 *
 * Let's try to implement a transform that will flag any difference that doesn't have an attachment called "issue-no"
 * with "error" criticality. Let's call it {@code IssueNumberPolicyTransform}. It is important for this transform to
 * only be called after the {@code revapi.differences} transformation. See the {@literal README.adoc} file that shows
 * how to do that.
 */
public class IssueNumberPolicyTransform<E extends Element<E>> extends BaseDifferenceTransform<E> {

    public Pattern[] getDifferenceCodePatterns() {
        // we want to react on all kinds of differences. A type of difference is identified by its "code" and we want
        // to match them all.
        return new Pattern[] { Pattern.compile(".*") };
    }

    @Nonnull
    @Override
    public List<Predicate<String>> getDifferenceCodePredicates() {
        // we want to react on all kinds of differences. A type of difference is identified by its "code" and we want
        // to match them all.
        return Collections.singletonList(__ -> true);
    }

    @Override
    public String getExtensionId() {
        return "issue-no";
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
        // no initialization needed for this extension
    }

    @Override
    public TransformationResult tryTransform(@Nullable E oldElement, @Nullable E newElement, Difference diff) {
        String issueNo = diff.attachments.get("issue-no");
        if (issueNo == null || issueNo.trim().isEmpty()) {
            return TransformationResult.replaceWith(Difference.copy(diff).withCriticality(Criticality.ERROR).build());
        } else {
            return TransformationResult.keep();
        }
    }
}
