/*
 * Copyright 2015 Lukas Krejci
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

package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor7;

/**
 * Can be used by various checks and problem transformations to work with two elements of the same type.
 * 
 * <p>Typical usage:
 * <pre><code>
 *     javax.lang.model.element.Element e1 = ...;
 *     javax.lang.model.element.Element e2 = ...;
 * 
 *     e1.accept(new ElementPairVisitor&lt;Void&gt;() {
 * 
 *         protected Void visitType(TypeElement e1, TypeElement e2) {
 *             ...
 *         }
 *     }, e2);
 * </code></pre>
 *
 * @see org.revapi.java.spi.TypeMirrorPairVisitor
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class ElementPairVisitor<R> extends SimpleElementVisitor7<R, Element> {

    @SuppressWarnings("UnusedParameters")
    protected R unmatchedAction(@Nonnull Element element, @Nullable Element otherElement) {
        return null;
    }

    protected R defaultMatchAction(@Nonnull Element element, @Nullable Element otherElement) {
        return unmatchedAction(element, otherElement);
    }

    @Override
    public final R visitPackage(@Nonnull PackageElement element, @Nullable Element otherElement) {
        return otherElement instanceof PackageElement ? visitPackage(element, (PackageElement) otherElement) :
            unmatchedAction(element, otherElement);
    }

    protected R visitPackage(@Nonnull PackageElement element, @Nonnull PackageElement otherElement) {
        return defaultMatchAction(element, otherElement);
    }

    @Override
    public final R visitType(@Nonnull TypeElement element, @Nullable Element otherElement) {
        return otherElement instanceof TypeElement ? visitType(element, (TypeElement) otherElement) :
            unmatchedAction(element, otherElement);
    }

    protected R visitType(@Nonnull TypeElement element, @Nonnull TypeElement otherElement) {
        return defaultMatchAction(element, otherElement);
    }

    @Override
    public final R visitVariable(@Nonnull VariableElement element, @Nullable Element otherElement) {
        return otherElement instanceof VariableElement ? visitVariable(element, (VariableElement) otherElement) :
            unmatchedAction(element, otherElement);
    }

    protected R visitVariable(@Nonnull VariableElement element, @Nonnull VariableElement otherElement) {
        return defaultMatchAction(element, otherElement);
    }

    @Override
    public final R visitExecutable(@Nonnull ExecutableElement element, @Nullable Element otherElement) {
        return otherElement instanceof ExecutableElement ? visitExecutable(element, (ExecutableElement) otherElement) :
            unmatchedAction(element, otherElement);
    }

    protected R visitExecutable(@Nonnull ExecutableElement element, @Nonnull ExecutableElement otherElement) {
        return defaultMatchAction(element, otherElement);
    }

    @Override
    public final R visitTypeParameter(@Nonnull TypeParameterElement element, @Nullable Element otherElement) {
        return otherElement instanceof TypeParameterElement ? visitTypeParameter(element,
            (TypeParameterElement) otherElement) :
            unmatchedAction(element, otherElement);
    }

    protected R visitTypeParameter(@Nonnull TypeParameterElement element, @Nonnull TypeParameterElement otherElement) {
        return defaultMatchAction(element, otherElement);
    }

    @Override
    public R visitUnknown(@Nonnull Element element, @Nullable Element otherElement) {
        return unmatchedAction(element, otherElement);
    }
}
