package org.revapi.maven.utils;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ScopeDependencySelector implements DependencySelector {
    private final String[] scopes;
    private final int depth;
    private final Dependency parent;
    private final ScopeDependencySelector parentSelector;

    public ScopeDependencySelector(String... scopes) {
        this(scopes, null, null, 0);
    }

    protected ScopeDependencySelector(String[] scopes, Dependency parent, ScopeDependencySelector parentSelector,
        int depth) {
        this.scopes = scopes;
        this.parent = parent;
        this.parentSelector = parentSelector;
        this.depth = depth;
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
    public boolean selectDependency(Dependency dependency) {
        if (!isExcluded(dependency)) {
            boolean optional = dependency.isOptional();

            return !optional && hasRequiredScope(dependency);
        }
        return false;
    }

    protected boolean isExcluded(Dependency dependency) {
        boolean result = isExcludedFromParent(dependency);
        if (!result && parentSelector != null) {
            result = parentSelector.isExcluded(dependency);
        }
        return result;
    }

    private boolean isExcludedFromParent(Dependency dependency) {
        boolean result = false;
        if (parent != null && parent.getExclusions().size() > 0) {
            for (Exclusion exclusion : parent.getExclusions()) {
                if (exclusion != null) {
                    if (exclusion.getArtifactId() != null
                        && exclusion.getArtifactId().equals(dependency.getArtifact().getArtifactId())) {
                        if (exclusion.getGroupId() != null
                            && exclusion.getGroupId().equals(dependency.getArtifact().getGroupId())) {
                            result = true;
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public org.eclipse.aether.collection.DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        return new ScopeDependencySelector(scopes, context.getDependency(), this, depth + 1);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (null == obj || !getClass().equals(obj.getClass())) {
            return false;
        }

        ScopeDependencySelector that = (ScopeDependencySelector) obj;
        return depth == that.depth;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = hash * 31 + depth;
        return hash;
    }
}
