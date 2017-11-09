/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi.java.spi;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.configuration.Configurable;

/**
 * An interface that java API checkers need to implement.
 * 
 * <p>The methods on this interface are called in the following order:
 * <pre><code>
 * initialize
 * setOldTypeEnvironment
 * setNewTypeEnvironment
 * (visitClass
 *     (visitClass ... visitEnd)* //inner classes
 *     (visitField
 *         visitAnnotation*
 *      visitEnd)*
 *     (visitMethod
 *         (visitMethodParameter
 *             visitAnnotation*
 *          visitEnd)*
 *         visitAnnotation*
 *      visitEnd)*
 *     visitAnnotation*
 *  visitEnd)*
 * </code></pre>
 * 
 * <p>Consider inheriting from the {@link org.revapi.java.spi.CheckBase} instead of directly implementing this
 * interface because it takes care of matching the corresponding {@code visit*()} and {@code visitEnd()} calls.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Check extends Configurable {

    /**
     * The environment containing the old version of the classes. This can be used to reason about the
     * classes when doing the checks.
     * 
     * <p>Called once after the check has been instantiated.
     *
     * @param env the environment to obtain the helper objects using which one can navigate and examine types
     */
    void setOldTypeEnvironment(@Nonnull TypeEnvironment env);

    /**
     * The environment containing the new version of the classes. This can be used to reason about the
     * classes when doing the checks.
     * 
     * <p>Called once after the check has been instantiated.
     *
     * @param env the environment to obtain the helper objects using which one can navigate and examine types
     */
    void setNewTypeEnvironment(@Nonnull TypeEnvironment env);

    /**
     * Each check typically checks only a single type of java element - a method or an annotation - but may be
     * interested in more.
     *
     * <p>This method must be used by the implementations to advertise what type of checks they are interested in.
     * Only the appropriate {@code visit*} calls will then be made on the check instances.
     *
     * @return the set of check types this instance is interested in performing
     */
    EnumSet<Type> getInterest();

    /**
     * Each of the other visit* calls is followed by a corresponding call to this method in a stack-like
     * manner.
     * 
     * <p>I.e. a series of calls might look like this:<br>
     * <pre><code>
     * visitClass();
     * visitMethod();
     * visitEnd();
     * visitMethod();
     * visitEnd();
     * visitEnd(); //"ends" the visitClass()
     * </code></pre>
     *
     * @return the list of found differences between corresponding elements or null if no differences found (null is
     * considered equivalent to returning an empty collection).
     */
    @Nullable
    List<Difference> visitEnd();

    void visitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType);

    void visitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod);

    void visitMethodParameter(@Nullable JavaMethodParameterElement oldParameter, @Nullable JavaMethodParameterElement newParameter);

    void visitField(@Nullable JavaFieldElement oldField, @Nullable JavaFieldElement newField);

    /**
     * Visiting annotation is slightly different, because it is not followed by the standard {@link #visitEnd()} call.
     * Instead, because visiting annotation is always "terminal" in a sense that an annotation doesn't have any child
     * elements, the list of differences is returned straight away.
     *
     * @param oldAnnotation the annotation in the old API
     * @param newAnnotation the annotation in the new API
     *
     * @return the list of differences between the two annotations
     */
    @Nullable
    List<Difference> visitAnnotation(@Nullable JavaAnnotationElement oldAnnotation,
        @Nullable JavaAnnotationElement newAnnotation);

    enum Type {
        CLASS, METHOD, METHOD_PARAMETER, FIELD, ANNOTATION
    }
}
