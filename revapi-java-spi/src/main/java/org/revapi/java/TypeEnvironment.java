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

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * This interface mimics (to an extent) the {@link javax.annotation.processing.ProcessingEnvironment} and
 * serves the same purpose. To give a context to the API checking classes.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public interface TypeEnvironment {

    Elements getElementUtils();

    Types getTypeUtils();

    //TODO shall we support this way of reporting stuff?
    //Messager getMessager();
}
