/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import org.revapi.Configuration;
import org.revapi.Difference;

/**
 * An empty implementation of the {@link Check} interface.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class CheckBase implements Check {

    protected static class ActiveElements<T extends Element> {
        public final T oldElement;
        public final T newElement;
        public final Object[] context;
        private final int depth;

        private ActiveElements(int depth, T oldElement, T newElement, Object... context) {
            this.depth = depth;
            this.oldElement = oldElement;
            this.newElement = newElement;
            this.context = context;
        }
    }

    private TypeEnvironment oldTypeEnvironment;
    private TypeEnvironment newTypeEnvironment;
    private int depth;
    private final Deque<ActiveElements<?>> activations = new ArrayDeque<>();
    private Configuration configuration;

    @Nonnull
    public TypeEnvironment getOldTypeEnvironment() {
        return oldTypeEnvironment;
    }

    @Nonnull
    public TypeEnvironment getNewTypeEnvironment() {
        return newTypeEnvironment;
    }

    @Nonnull
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void initialize(@Nonnull Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void setOldTypeEnvironment(@Nonnull TypeEnvironment env) {
        oldTypeEnvironment = env;
    }

    @Override
    public void setNewTypeEnvironment(@Nonnull TypeEnvironment env) {
        newTypeEnvironment = env;
    }

    @Nullable
    @Override
    public final List<Difference> visitEnd() {
        try {
            return doEnd();
        } finally {
            depth--;
        }
    }

    @Nullable
    protected List<Difference> doEnd() {
        return null;
    }

    @Override
    public final void visitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
        depth++;
        doVisitClass(oldType, newType);
    }

    protected void doVisitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
    }

    @Override
    public final void visitMethod(@Nullable ExecutableElement oldMethod, @Nullable ExecutableElement newMethod) {
        depth++;
        doVisitMethod(oldMethod, newMethod);
    }

    protected void doVisitMethod(@Nullable ExecutableElement oldMethod, @Nullable ExecutableElement newMethod) {
    }

    @Override
    public final void visitMethodParameter(@Nullable VariableElement oldParameter,
        @Nullable VariableElement newParameter) {
        depth++;
        doVisitMethodParameter(oldParameter, newParameter);
    }

    @SuppressWarnings("UnusedParameters")
    protected void doVisitMethodParameter(@Nullable VariableElement oldParameter,
        @Nullable VariableElement newParameter) {
    }

    @Override
    public final void visitField(@Nullable VariableElement oldField, @Nullable VariableElement newField) {
        depth++;
        doVisitField(oldField, newField);
    }

    protected void doVisitField(@Nullable VariableElement oldField, @Nullable VariableElement newField) {
    }

    @Nullable
    @Override
    public final List<Difference> visitAnnotation(@Nullable AnnotationMirror oldAnnotation,
        @Nullable AnnotationMirror newAnnotation) {
        depth++;
        List<Difference> ret = doVisitAnnotation(oldAnnotation, newAnnotation);
        depth--;
        return ret;
    }

    @Nullable
    protected List<Difference> doVisitAnnotation(@Nullable AnnotationMirror oldAnnotation,
        @Nullable AnnotationMirror newAnnotation) {
        return null;
    }

    protected final <T extends Element> void pushActive(@Nullable T oldElement, @Nullable T newElement,
        Object... context) {
        ActiveElements<T> r = new ActiveElements<>(depth, oldElement, newElement, context);
        activations.push(r);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T extends Element> ActiveElements<T> popIfActive() {
        return (ActiveElements<T>) (!activations.isEmpty() && activations.peek().depth == depth ? activations.pop() :
            null);
    }

    @Nullable
    @SuppressWarnings({"unchecked", "UnusedDeclaration"})
    protected <T extends Element> ActiveElements<T> peekLastActive() {
        return (ActiveElements<T>) activations.peek();
    }
}
