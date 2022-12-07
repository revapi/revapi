/*
 * Copyright 2014-2022 Lukas Krejci
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
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;

public class ElementMatcher implements org.revapi.ElementMatcher {

    public Optional<CompiledRecipe> compile(String recipe) {
        return Optional.empty();
    }


    @Override
    public void close() throws Exception {

    }

    @Override
    public String getMatcherId() {
        return "elementMatcher";
    }

    @Override
    public String getExtensionId() {
        return "elementMatcher";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        try {
            Files.write(new File("ElementMatcher").toPath(), Arrays.asList(""));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
