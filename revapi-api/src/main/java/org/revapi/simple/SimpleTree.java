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

package org.revapi.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.revapi.Element;
import org.revapi.Tree;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class SimpleTree<Lang> implements Tree {
    private SortedSet<? extends SimpleElement<Lang>> roots;

    @Override
    public SortedSet<? extends SimpleElement<Lang>> getRoots() {
        if (roots == null) {
            roots = new TreeSet<>();
        }
        return roots;
    }

    @Override
    public <T extends Element> List<T> search(Class<T> resultType, boolean recurse, Filter<? super T> filter,
        Element root) {
        List<T> results = new ArrayList<>();
        search(results, resultType, root == null ? getRoots() : root.getChildren(), recurse, filter);
        return results;
    }

    public <T extends Element> void search(List<T> results, Class<T> resultType,
        SortedSet<? extends Element> currentLevel, boolean recurse, Filter<? super T> filter) {

        for (Element e : currentLevel) {
            if (resultType.isAssignableFrom(e.getClass())) {
                T te = resultType.cast(e);

                if (filter == null || filter.applies(te)) {
                    results.add(te);
                }
            }

            if (recurse && (filter == null || filter.shouldDescendInto(e))) {
                search(results, resultType, e.getChildren(), recurse, filter);
            }
        }
    }
}
