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

package org.revapi;

import javax.annotation.Nonnull;

import org.revapi.configuration.Configurable;

/**
 * A reporter is the final stage during the API change analysis. It somehow conveys the found difference reports to
 * some kind of output.
 *
 * <p>Importantly, reporters are {@link org.revapi.configuration.Configurable} and can use the locale defined in the
 * analysis context of the configuration to produce the desired output.
 *
 * <p>The {@link #close()} is not called if there is no prior call to {@link #initialize(AnalysisContext)}. Do all your
 * resource acquisition in initialize, not during the construction of the object.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Reporter extends AutoCloseable, Configurable {

    void report(@Nonnull Report report);
}
