/*
 * Copyright 2014-2019 Lukas Krejci
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
package org.revapi.java;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nonnull;

import org.revapi.API;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementForest;
import org.revapi.TreeFilter;
import org.revapi.java.compilation.CompilationFuture;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.Compiler;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaArchiveAnalyzer implements ArchiveAnalyzer {
    private final API api;
    private final ExecutorService executor;
    private final ProbingEnvironment probingEnvironment;
    private final AnalysisConfiguration.MissingClassReporting missingClassReporting;
    private final boolean ignoreMissingAnnotations;
    private CompilationValve compilationValve;

    public JavaArchiveAnalyzer(API api, ExecutorService compilationExecutor,
            AnalysisConfiguration.MissingClassReporting missingClassReporting,
            boolean ignoreMissingAnnotations) {
        this.api = api;
        this.executor = compilationExecutor;
        this.missingClassReporting = missingClassReporting;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.probingEnvironment = new ProbingEnvironment(api);
    }

    @Nonnull
    @Override
    public JavaElementForest analyze(TreeFilter filter) {
        if (Timing.LOG.isDebugEnabled()) {
            Timing.LOG.debug("Starting analysis of " + api);
        }

        StringWriter output = new StringWriter();
        Compiler compiler = new Compiler(executor, output, api.getArchives(), api.getSupplementaryArchives(),
                filter);
        try {
            compilationValve = compiler
                .compile(probingEnvironment, missingClassReporting, ignoreMissingAnnotations);

            probingEnvironment.getTree()
                .setCompilationFuture(new CompilationFuture(compilationValve, output));

            if (Timing.LOG.isDebugEnabled()) {
                Timing.LOG.debug("Preliminary API tree produced for " + api);
            }
            return probingEnvironment.getTree();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to analyze archives in api " + api, e);
        }
    }

    @Override
    public void prune(ElementForest forest) {
        if (!(forest instanceof JavaElementForest)) {
            return;
        }

        boolean changed;

        Set<TypeElement> toRemove = new HashSet<>();

        do {
            Iterator<TypeElement> it = forest.iterateOverElements(TypeElement.class, true, null, null);

            toRemove.clear();

            while (it.hasNext()) {
                TypeElement type = it.next();

                boolean remove = true;

                Iterator<UseSite> usit = type.getUseSites().iterator();
                while (usit.hasNext()) {
                    UseSite useSite = usit.next();
                    if (isInForest(forest, useSite.getSite())) {
                        if (useSite.getUseType().isMovingToApi()) {
                            remove = false;
                        }
                    } else {
                        usit.remove();
                    }
                }

                // keep the types that are in the API because they are, not just because something dragged them into it.
                if (isInApi(type)) {
                    continue;
                }

                if (remove) {
                    toRemove.add(type);
                }
            }

            changed = !toRemove.isEmpty();

            for (TypeElement t : toRemove) {
                if (t.getParent() == null) {
                    forest.getRoots().remove(t);
                } else {
                    t.getParent().getChildren().remove(t);
                }

                t.getUsedTypes().entrySet().removeIf(e -> {
                    UseSite.Type useType = e.getKey();
                    e.getValue().entrySet().removeIf(e2 -> {
                        JavaTypeElement usedType = e2.getKey();
                        usedType.getUseSites().removeIf(us -> {
                            //noinspection SuspiciousMethodCalls
                            return us.getUseType() == useType && e2.getValue().contains(us.getSite());
                        });

                        return usedType.getUseSites().isEmpty();
                    });

                    return e.getValue().isEmpty();
                });
            }
        } while (changed);
    }

    public ProbingEnvironment getProbingEnvironment() {
        return probingEnvironment;
    }

    public CompilationValve getCompilationValve() {
        return compilationValve;
    }

    private static boolean isInForest(ElementForest forest, Element element) {
        Element parent = element.getParent();
        while (parent != null) {
            element = parent;
            parent = parent.getParent();
        }

        return forest.getRoots().contains(element);
    }

    private static boolean isInApi(TypeElement element) {
        while (element != null) {
            if (element.isInAPI() && !element.isInApiThroughUse()) {
                return true;
            }

            if (element.getParent() instanceof TypeElement) {
                element = (TypeElement) element.getParent();
            } else {
                element = null;
            }
        }

        return false;
    }
}
