/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.maven.utils;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
final class ScopeDependencyTraverser implements DependencyTraverser {
    private final String[] topLevelScopes;
    private final String[] transitiveScopes;
    private final int depth;

    public ScopeDependencyTraverser(String[] topLevelScopes, String[] transitiveScopes) {
        this(topLevelScopes, transitiveScopes, 0);
    }

    private ScopeDependencyTraverser(String[] topLevelScopes, String[] transitiveScopes, int depth) {
        this.topLevelScopes = topLevelScopes;
        this.transitiveScopes = transitiveScopes;
        this.depth = depth;
    }

    private boolean hasRequiredScope(Dependency dep) {
        String scope = dep.getScope();
        if (scope == null || scope.isEmpty()) {
            scope = "compile";
        }

        for (String s : depth > 1 ? transitiveScopes : topLevelScopes) {
            if (s.equals(scope)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean traverseDependency(Dependency dependency) {
        return hasRequiredScope(dependency);
    }

    @Override
    public DependencyTraverser deriveChildTraverser(DependencyCollectionContext context) {
        return depth > 1 ? this : new ScopeDependencyTraverser(topLevelScopes, transitiveScopes, depth + 1);
    }
}
