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
import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;

public class ForthTransform implements DifferenceTransform {
    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[] {Pattern.compile(".*")};
    }

    @Nullable
    @Override
    public Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
            @Nonnull Difference difference) {
        return Difference.copy(difference).withCriticality(Criticality.ERROR).build();
    }

    @Override
    public void close() throws Exception {

    }

    @Override
    public String getExtensionId() {
        return "forth";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new StringReader("{\"type\": \"string\"}");
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        try {
            Files.write(new File("ForthTransform").toPath(), asList(""));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}