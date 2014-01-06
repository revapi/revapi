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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.Archive;
import org.revapi.Configuration;
import org.revapi.java.classes.misc.A;
import org.revapi.java.model.JavaTree;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;


/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class JavaArchiveAnalyzerTest {

    private static class ShrinkwrapArchive implements Archive {
        private final JavaArchive archive;

        private ShrinkwrapArchive(JavaArchive archive) {
            this.archive = archive;
        }

        @Override
        public String getName() {
            return archive.getName();
        }

        @Override
        public InputStream openStream() throws IOException {
            return archive.as(ZipExporter.class).exportAsInputStream();
        }
    }

    @Test
    public void test() throws Exception {
        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, "test.jar").addPackage(A.class.getPackage());

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new Configuration(Locale.getDefault(),
            Collections.<String, String>emptyMap()),
            Arrays.<Archive>asList(new ShrinkwrapArchive(archive)), Executors.newSingleThreadExecutor());

        JavaTree tree = analyzer.analyze();

        Assert.assertEquals(6, tree.getRoots().size());

        System.out.println(tree);
    }
}
