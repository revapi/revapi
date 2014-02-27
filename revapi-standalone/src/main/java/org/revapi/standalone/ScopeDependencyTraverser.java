package org.revapi.standalone;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class ScopeDependencyTraverser implements DependencyTraverser {
    private final String[] scopes;

    public ScopeDependencyTraverser(String... scopes) {
        this.scopes = scopes;
    }

    private boolean hasRequiredScope(Dependency dep) {
        String scope = dep.getScope();
        if (scope == null || scope.isEmpty()) {
            scope = "compile";
        }

        for (String s : scopes) {
            if (s.equals(scope)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean traverseDependency(Dependency dependency) {
        return hasRequiredScope(dependency) && !ExtensionResolver.isRevapiApi(dependency);
    }

    @Override
    public DependencyTraverser deriveChildTraverser(DependencyCollectionContext context) {
        return this;
    }
}
