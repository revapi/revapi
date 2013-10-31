/*
 * Copyright 2013 Lukas Krejci
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.revapi.Archive;
import org.revapi.java.elements.ClassTree;
import org.revapi.simple.SimpleArchiveAnalyzer;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class JarAnalyzer extends SimpleArchiveAnalyzer<Java, ClassTree> {
    private ZipInputStream jar;

    private class ClassFileArchive implements Archive {

        private final String name;

        public ClassFileArchive(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public InputStream openStream() throws IOException {
            return jar;
        }
    }

    public JarAnalyzer(Archive archive) {
        super(archive);
    }

    @Override
    protected ClassTree doAnalyze() throws Exception {
        ClassTree tree = new ClassTree();

        jar = new ZipInputStream(openStream());

        ZipEntry entry = jar.getNextEntry();

        while (entry != null) {
            if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith(".class")) {

                //specifically do NOT close the ClassFileAnalyzer, because that would close our whole JAR stream.
                ClassTree classTree = new ClassFileAnalyzer(new ClassFileArchive(entry.getName())).analyze();
                tree.getRoots().addAll(classTree.getRoots());
            }

            entry = jar.getNextEntry();
        }

        return tree;
    }

    @Override
    public void close() throws IOException {
        if (jar != null) {
            jar.close();
        }
    }
}
