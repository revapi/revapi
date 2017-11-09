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

import javax.annotation.Nonnull;

import org.revapi.Element;

/**
 * Basic interface that all Revapi elements modelling the Java AST satisfy.
 * The methods on this interface are provided so that it is possible to write {@link
 * org.revapi.DifferenceTransform
 * problem transforms} without needing to somehow initialize the environment the java element is present in.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface JavaElement extends Element {
    @Nonnull
    TypeEnvironment getTypeEnvironment();
}
