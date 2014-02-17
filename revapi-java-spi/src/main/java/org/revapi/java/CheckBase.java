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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import org.revapi.Configuration;
import org.revapi.MatchReport;

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
    private final Deque<ActiveElements> activations = new ArrayDeque<>();
    private Configuration configuration;

    public TypeEnvironment getOldTypeEnvironment() {
        return oldTypeEnvironment;
    }

    public TypeEnvironment getNewTypeEnvironment() {
        return newTypeEnvironment;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void initialize(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void setOldTypeEnvironment(TypeEnvironment env) {
        oldTypeEnvironment = env;
    }

    @Override
    public void setNewTypeEnvironment(TypeEnvironment env) {
        newTypeEnvironment = env;
    }

    @Override
    public final List<MatchReport.Problem> visitEnd() {
        try {
            return doEnd();
        } finally {
            depth--;
        }
    }

    protected List<MatchReport.Problem> doEnd() {
        return null;
    }

    @Override
    public final void visitClass(TypeElement oldType, TypeElement newType) {
        depth++;
        doVisitClass(oldType, newType);
    }

    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
    }

    @Override
    public final void visitMethod(ExecutableElement oldMethod, ExecutableElement newMethod) {
        depth++;
        doVisitMethod(oldMethod, newMethod);
    }

    protected void doVisitMethod(ExecutableElement oldMethod, ExecutableElement newMethod) {
    }

    @Override
    public final void visitMethodParameter(VariableElement oldParameter, VariableElement newParameter) {
        depth++;
        doVisitMethodParameter(oldParameter, newParameter);
    }

    protected void doVisitMethodParameter(VariableElement oldParameter, VariableElement newParameter) {
    }

    @Override
    public final void visitField(VariableElement oldField, VariableElement newField) {
        depth++;
        doVisitField(oldField, newField);
    }

    protected void doVisitField(VariableElement oldField, VariableElement newField) {
    }

    @Override
    public final List<MatchReport.Problem> visitAnnotation(AnnotationMirror oldAnnotation,
        AnnotationMirror newAnnotation) {
        depth++;
        List<MatchReport.Problem> ret = doVisitAnnotation(oldAnnotation, newAnnotation);
        depth--;
        return ret;
    }

    protected List<MatchReport.Problem> doVisitAnnotation(AnnotationMirror oldAnnotation,
        AnnotationMirror newAnnotation) {
        return null;
    }

    protected final <T extends Element> void pushActive(T oldElement, T newElement, Object... context) {
        ActiveElements<T> r = new ActiveElements<>(depth, oldElement, newElement, context);
        activations.push(r);
    }

    @SuppressWarnings("unchecked")
    protected <T extends Element> ActiveElements<T> popIfActive() {
        return !activations.isEmpty() && activations.peek().depth == depth ? activations.pop() : null;
    }

    @SuppressWarnings("unchecked")
    protected <T extends Element> ActiveElements<T> peekLastActive() {
        return activations.peek();
    }
}
