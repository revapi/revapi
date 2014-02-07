/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java;

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import org.revapi.Configuration;
import org.revapi.MatchReport;

/**
 * An interface that java API checkers need to implement.
 * <p/>
 * The methods on this interface are called in the following order:
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
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Check {

    void initialize(Configuration configuration);

    /**
     * The environment containing the old version of the classes. This can be used to reason about the
     * classes when doing the checks.
     * <p/>
     * Called once after the check has been instantiated.
     *
     * @param env the environment to obtain the helper objects using which one can navigate and examine types
     */
    void setOldTypeEnvironment(TypeEnvironment env);

    /**
     * The environment containing the new version of the classes. This can be used to reason about the
     * classes when doing the checks.
     * <p/>
     * Called once after the check has been instantiated.
     *
     * @param env the environment to obtain the helper objects using which one can navigate and examine types
     */
    void setNewTypeEnvironment(TypeEnvironment env);

    /**
     * Each of the other visit* calls is followed by a corresponding call to this method in a stack-like
     * manner.
     * <p/>
     * I.e. a series of calls might look like this:<br/>
     * <pre><code>
     * visitType();
     * visitMethod();
     * visitEnd();
     * visitMethod();
     * visitEnd();
     * visitEnd(); //"ends" the visitType()
     * </code></pre>
     */
    List<MatchReport.Problem> visitEnd();

    void visitClass(TypeElement oldType, TypeElement newType);

    void visitMethod(ExecutableElement oldMethod, ExecutableElement newMethod);

    void visitMethodParameter(VariableElement oldParameter, VariableElement newParameter);

    void visitField(VariableElement oldField, VariableElement newField);

    List<MatchReport.Problem> visitAnnotation(AnnotationMirror oldAnnotation, AnnotationMirror newAnnotation);
}
