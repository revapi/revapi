/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.ResourceBundle;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.AnnotationElement;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.JavaElementFactory;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Check;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaElementDifferenceAnalyzer implements DifferenceAnalyzer {
    private static final Logger LOG = LoggerFactory.getLogger(JavaElementDifferenceAnalyzer.class);

    private final Iterable<Check> checks;
    private final CompilationValve oldCompilationValve;
    private final CompilationValve newCompilationValve;
    private final AnalysisConfiguration analysisConfiguration;
    private final ResourceBundle messages;
    private final ProbingEnvironment oldEnvironment;
    private final ProbingEnvironment newEnvironment;

    // NOTE: this doesn't have to be a stack of lists only because of the fact that annotations
    // are always sorted as last amongst sibling model elements.
    // So, when reported for their parent element, we can be sure that there are no more children
    // coming for given parent.
    private List<Difference> lastAnnotationResults;

    public JavaElementDifferenceAnalyzer(AnalysisContext analysisContext, ProbingEnvironment oldEnvironment,
        CompilationValve oldValve,
        ProbingEnvironment newEnvironment, CompilationValve newValve, Iterable<Check> checks,
        AnalysisConfiguration analysisConfiguration) {

        this.oldCompilationValve = oldValve;
        this.newCompilationValve = newValve;

        this.checks = checks;
        for (Check c : checks) {
            c.initialize(analysisContext);
            c.setOldTypeEnvironment(oldEnvironment);
            c.setNewTypeEnvironment(newEnvironment);
        }

        this.analysisConfiguration = analysisConfiguration;

        messages = ResourceBundle.getBundle("org.revapi.java.messages", analysisContext.getLocale());

        this.oldEnvironment = oldEnvironment;
        this.newEnvironment = newEnvironment;
    }

    @Override
    public Comparator<? super Element> getCorrespondenceComparator() {
        return new Comparator<Element>() {
            @Override
            public int compare(Element o1, Element o2) {
                int ret = JavaElementFactory.compareByType(o1, o2);

                if (ret != 0) {
                    return ret;
                }

                //the only "special" treatment is required for methods, for which we need to detect return type
                //changes and such, which requires pronouncing methods equal on type and name, excluding return and
                //parameter types
                if (o1 instanceof MethodElement) {
                    MethodElement m1 = (MethodElement) o1;
                    MethodElement m2 = (MethodElement) o2;

                    @SuppressWarnings("ConstantConditions")
                    String sig1 = ((TypeElement) m1.getParent()).getCanonicalName() + "::" +
                        m1.getModelElement().getSimpleName().toString();

                    @SuppressWarnings("ConstantConditions")
                    String sig2 = ((TypeElement) m2.getParent()).getCanonicalName() + "::" +
                        m2.getModelElement().getSimpleName().toString();

                    return sig1.compareTo(sig2);
                } else {
                    return o1.compareTo(o2);
                }
            }
        };
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
        LOG.trace("Tearing down compilation results");
        oldCompilationValve.removeCompiledResults();
        newCompilationValve.removeCompiledResults();
    }

    @Override
    public void beginAnalysis(@Nullable Element oldElement, @Nullable Element newElement) {
        LOG.trace("Beginning analysis of {} and {}.", oldElement, newElement);

        if (conforms(oldElement, newElement, TypeElement.class)) {
            for (Check c : checks) {
                c.visitClass(oldElement == null ? null : ((TypeElement) oldElement).getModelElement(),
                    newElement == null ? null : ((TypeElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, AnnotationElement.class)) {
            // annotation are always terminal elements and they also always sort as last elements amongst siblings, so
            // treat them a bit differently
            if (lastAnnotationResults == null) {
                lastAnnotationResults = new ArrayList<>();
            }
            for (Check c : checks) {
                List<Difference> cps = c
                    .visitAnnotation(oldElement == null ? null : ((AnnotationElement) oldElement).getAnnotation(),
                        newElement == null ? null : ((AnnotationElement) newElement).getAnnotation());
                if (cps != null) {
                    lastAnnotationResults.addAll(cps);
                }
            }
        } else if (conforms(oldElement, newElement, FieldElement.class)) {
            for (Check c : checks) {
                c.visitField(oldElement == null ? null : ((FieldElement) oldElement).getModelElement(),
                    newElement == null ? null : ((FieldElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, MethodElement.class)) {
            for (Check c : checks) {
                c.visitMethod(oldElement == null ? null : ((MethodElement) oldElement).getModelElement(),
                    newElement == null ? null : ((MethodElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, MethodParameterElement.class)) {
            for (Check c : checks) {
                c.visitMethodParameter(
                    oldElement == null ? null : ((MethodParameterElement) oldElement).getModelElement(),
                    newElement == null ? null : ((MethodParameterElement) newElement).getModelElement());
            }
        }
    }

    @Override
    public Report endAnalysis(@Nullable Element oldElement, @Nullable Element newElement) {
        if (conforms(oldElement, newElement, AnnotationElement.class)) {
            //the annotations are always reported at the parent element
            return new Report(Collections.<Difference>emptyList(), oldElement, newElement);
        }

        List<Difference> differences = new ArrayList<>();
        for (Check c : checks) {
            List<Difference> p = c.visitEnd();
            if (p != null) {
                differences.addAll(p);
            }
        }

        if (lastAnnotationResults != null && !lastAnnotationResults.isEmpty()) {
            differences.addAll(lastAnnotationResults);
            lastAnnotationResults.clear();
        }

        if (!differences.isEmpty()) {
            LOG.trace("Detected following problems: {}", differences);
        }
        LOG.trace("Ended analysis of {} and {}.", oldElement, newElement);

        ListIterator<Difference> it = differences.listIterator();
        while (it.hasNext()) {
            Difference d = it.next();
            if (analysisConfiguration.getUseReportingCodes().contains(d.code)) {
                StringBuilder newDesc = new StringBuilder(d.description);
                appendUses(oldElement, oldEnvironment, newDesc);
                appendUses(newElement, newEnvironment, newDesc);

                d = Difference.builder().addAttachments(d.attachments).addClassifications(d.classification)
                    .withCode(d.code).withName(d.name).withDescription(newDesc.toString()).build();
            }
            it.set(d);
        }

        return new Report(differences, oldElement, newElement);
    }

    private <T> boolean conforms(Object a, Object b, Class<T> cls) {
        boolean ca = a == null || cls.isAssignableFrom(a.getClass());
        boolean cb = b == null || cls.isAssignableFrom(b.getClass());

        return ca && cb;
    }

    private String join(Collection<UseSite> useSites) {
        StringBuilder bld = new StringBuilder();
        Iterator<UseSite> it = useSites.iterator();

        if (it.hasNext()) {
            append(bld, it.next());
        }

        while (it.hasNext()) {
            bld.append(", ");
            append(bld, it.next());
        }

        return bld.toString();
    }

    private void append(StringBuilder bld, UseSite use) {
        String message;
        switch (use.getUseType()) {
        case ANNOTATES:
            message = "revapi.java.uses.annotates";
            break;
        case HAS_TYPE:
            message = "revapi.java.uses.hasType";
            break;
        case IS_IMPLEMENTED:
            message = "revapi.java.uses.isImplemented";
            break;
        case IS_INHERITED:
            message = "revapi.java.uses.isInherited";
            break;
        case IS_THROWN:
            message = "revapi.java.uses.isThrown";
            break;
        case PARAMETER_TYPE:
            message = "revapi.java.uses.parameterType";
            break;
        case RETURN_TYPE:
            message = "revapi.java.uses.returnType";
            break;
        default:
            throw new AssertionError("Invalid use type.");
        }

        message = messages.getString(message);
        message = MessageFormat.format(message, use.getSite().getFullHumanReadableString());

        bld.append(message);
    }

    private void appendUses(Element element, ProbingEnvironment environment, StringBuilder bld) {
        if (element instanceof JavaTypeElement) {
            bld.append("\n");
            LOG.trace("Reporting uses of {}", element);
            javax.lang.model.element.TypeElement type = ((JavaTypeElement) element).getModelElement();

            Set<UseSite> useSites = environment.getUseSites(type);
            Iterator<UseSite> useIt = useSites.iterator();

            if (useIt.hasNext()) {
                appendUse(bld, useIt.next());
            }

            while (useIt.hasNext()) {
                bld.append(", ");
                appendUse(bld, useIt.next());
            }
        }
    }

    private void appendUse(StringBuilder bld, UseSite use) {
        List<UseSite> chain = getShortestPathToApiArchive(use);
        Iterator<UseSite> chainIt = chain.iterator();

        if (chainIt.hasNext()) {
            append(bld, chainIt.next());
        }

        while (chainIt.hasNext()) {
            bld.append(" <- ");
            append(bld, chainIt.next());
        }
    }

    private List<UseSite> getShortestPathToApiArchive(UseSite bottomUse) {
        // TODO implement
        return Collections.singletonList(bottomUse);
    }

    private static <T> boolean contains(T value, Iterable<? extends T> values) {
        Iterator<? extends T> it = values.iterator();
        while (it.hasNext()) {
            if (value.equals(it.next())) {
                return true;
            }
        }

        return false;
    }
}
