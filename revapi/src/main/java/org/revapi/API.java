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

package org.revapi;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents a set of archives that define an API.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class API {

    public static final class Builder {
        private final Set<Archive> archives = new LinkedHashSet<>();
        private final Set<Archive> supplementaryArchives = new LinkedHashSet<>();

        public Builder of(Archive... archives) {
            return of(Arrays.asList(archives));
        }

        public Builder of(Iterable<? extends Archive> archives) {
            this.archives.clear();
            return addArchives(archives);
        }

        public Builder addArchive(Archive archive) {
            archives.add(archive);
            return this;
        }

        public Builder addArchives(Archive... archives) {
            return addArchives(Arrays.asList(archives));
        }

        public Builder addArchives(Iterable<? extends Archive> archives) {
            for (Archive a : archives) {
                this.archives.add(a);
            }
            return this;
        }

        public Builder supportedBy(Archive... archives) {
            return supportedBy(Arrays.asList(archives));
        }

        public Builder supportedBy(Iterable<? extends Archive> archives) {
            this.supplementaryArchives.clear();
            return addSupportArchives(archives);
        }

        public Builder addSupportArchive(Archive archive) {
            supplementaryArchives.add(archive);
            return this;
        }

        public Builder addSupportArchives(Archive... archives) {
            return addSupportArchives(Arrays.asList(archives));
        }

        public Builder addSupportArchives(Iterable<? extends Archive> archives) {
            for (Archive a : archives) {
                this.supplementaryArchives.add(a);
            }
            return this;
        }

        public API build() {
            return new API(archives, supplementaryArchives);
        }
    }

    private final Iterable<? extends Archive> archives;
    private final Iterable<? extends Archive> supplementaryArchives;

    /**
     * @see #getArchives()
     * @see #getSupplementaryArchives()
     */
    public API(@Nonnull Iterable<? extends Archive> archives,
        @Nullable Iterable<? extends Archive> supplementaryArchives) {
        this.archives = archives;
        this.supplementaryArchives = supplementaryArchives;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder of(Archive... archives) {
        return builder().of(archives);
    }

    public static Builder of(Iterable<? extends Archive> archives) {
        return builder().of(archives);
    }

    /**
     * The set of archives to check the API of.
     */
    @Nonnull
    public Iterable<? extends Archive> getArchives() {
        return archives;
    }

    /**
     * The set of archives that somehow supplement the main ones (for example they contain
     * definitions used in the main archives). In Java, supplementary archives would be
     * the JARs that need to be on the compilation classpath. Can be null if no such
     * archives are needed.
     */
    @Nullable
    public Iterable<? extends Archive> getSupplementaryArchives() {
        return supplementaryArchives;
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder("API[archives=");
        addArchivesToString(bld, archives);
        bld.append(", supplementary=");
        addArchivesToString(bld, supplementaryArchives);
        bld.append("]");
        return bld.toString();
    }

    private static void addArchivesToString(StringBuilder bld, Iterable<? extends Archive> archives) {
        bld.append("[");

        if (archives != null) {
            Iterator<? extends Archive> it = archives.iterator();
            if (it.hasNext()) {
                bld.append(it.next().getName());
            }

            while (it.hasNext()) {
                bld.append(", ").append(it.next().getName());
            }
        }

        bld.append("]");
    }
}
