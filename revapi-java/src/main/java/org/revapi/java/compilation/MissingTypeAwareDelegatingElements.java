package org.revapi.java.compilation;

import static org.revapi.java.compilation.IgnoreCompletionFailures.Fn1;
import static org.revapi.java.compilation.IgnoreCompletionFailures.Fn2;
import static org.revapi.java.compilation.IgnoreCompletionFailures.Fn3;
import static org.revapi.java.compilation.IgnoreCompletionFailures.call;

import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.java.model.MissingTypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class MissingTypeAwareDelegatingElements implements Elements {
    private static final Logger LOG = LoggerFactory.getLogger(MissingTypeAwareDelegatingElements.class);
    private final Elements elements;

    private final Fn1<PackageElement, CharSequence> getPackageElement = new Fn1<PackageElement, CharSequence>() {
        @Override
        public PackageElement call(CharSequence name) throws Exception {
            return elements.getPackageElement(name);
        }
    };

    private final Fn1<TypeElement, CharSequence> getTypeElement = new Fn1<TypeElement, CharSequence>() {
        @Override
        public TypeElement call(CharSequence name) throws Exception {
            return elements.getTypeElement(name);
        }
    };

    private final
    Fn1<Map<? extends ExecutableElement, ? extends AnnotationValue>, AnnotationMirror>
        getElementValuesWithDefaults =
        new Fn1<Map<? extends ExecutableElement, ? extends AnnotationValue>, AnnotationMirror>() {

            @Override
            public Map<? extends ExecutableElement, ? extends AnnotationValue> call(AnnotationMirror annotationMirror)
                throws Exception {

                return elements.getElementValuesWithDefaults(annotationMirror);
            }
        };

    private final Fn1<String, Element> getDocComment = new Fn1<String, Element>() {
        @Override
        public String call(Element element) throws Exception {
            return elements.getDocComment(element);
        }
    };

    private final Fn1<Boolean, Element> isDeprecated = new Fn1<Boolean, Element>() {
        @Override
        public Boolean call(Element element) throws Exception {
            return elements.isDeprecated(element);
        }
    };

    private final Fn1<Name, TypeElement> getBinaryName = new Fn1<Name, TypeElement>() {
        @Override
        public Name call(TypeElement type) throws Exception {
            return elements.getBinaryName(type);
        }
    };

    private final Fn1<PackageElement, Element> getPackageOf = new Fn1<PackageElement, Element>() {
        @Override
        public PackageElement call(Element element) throws Exception {
            return elements.getPackageOf(element);
        }
    };

    private final Fn1<List<? extends Element>, TypeElement> getAllMembers = new Fn1<List<? extends Element>, TypeElement>() {
        @Override
        public List<? extends Element> call(TypeElement element) throws Exception {
            return elements.getAllMembers(element);
        }
    };

    private final Fn1<List<? extends AnnotationMirror>, Element> getAllAnnotationMirrors = new Fn1<List<? extends AnnotationMirror>, Element>() {
        @Override
        public List<? extends AnnotationMirror> call(Element element) throws Exception {
            return elements.getAllAnnotationMirrors(element);
        }
    };

    private final Fn2<Boolean, Element, Element> hides = new Fn2<Boolean, Element, Element>() {
        @Override
        public Boolean call(Element hider, Element hidden) throws Exception {
            return elements.hides(hider, hidden);
        }
    };

    private final Fn3<Boolean, ExecutableElement, ExecutableElement, TypeElement> overrides =
        new Fn3<Boolean, ExecutableElement, ExecutableElement, TypeElement>() {

            @Override
            public Boolean call(ExecutableElement m1, ExecutableElement m2,
                TypeElement type) throws Exception {
                return elements.overrides(m1, m2, type);
            }
        };

    private final Fn1<String, Object> getConstantExpression = new Fn1<String, Object>() {
        @Override
        public String call(Object o) throws Exception {
            return elements.getConstantExpression(o);
        }
    };

    private final Fn2<Void, Writer, Element[]> printElements = new Fn2<Void, Writer, Element[]>() {
        @Override
        public Void call(Writer writer, Element[] things) throws Exception {
            elements.printElements(writer, things);
            return null;
        }
    };

    MissingTypeAwareDelegatingElements(Elements elements) {
        this.elements = elements;
    }

    @Override
    public PackageElement getPackageElement(CharSequence name) {
        return call(getPackageElement, name);
    }

    @Override
    public TypeElement getTypeElement(CharSequence name) {
        return call(getTypeElement, name);
    }

    @Override
    public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
        AnnotationMirror a) {

        return call(getElementValuesWithDefaults, a);
    }

    @Override
    public String getDocComment(Element e) {
        if (MissingTypeElement.isMissing(e)) {
            return "";
        }
        return call(getDocComment, e);
    }

    @Override
    public boolean isDeprecated(Element e) {
        if (MissingTypeElement.isMissing(e)) {
            return false;
        }

        return call(isDeprecated, e);
    }

    @Override
    public Name getBinaryName(TypeElement type) {
        if (MissingTypeElement.isMissing(type)) {
            return type.getQualifiedName();
        }

        return call(getBinaryName, type);
    }

    @Override
    public PackageElement getPackageOf(Element type) {
        if (MissingTypeElement.isMissing(type)) {
            String binaryName = ((MissingTypeElement) type).getQualifiedName().toString();
            int lastDot = binaryName.lastIndexOf('.');

            return elements.getPackageElement(binaryName.substring(0, lastDot));
        }

        return call(getPackageOf, type);
    }

    @Override
    public List<? extends Element> getAllMembers(
        TypeElement type) {
        if (MissingTypeElement.isMissing(type)) {
            return Collections.emptyList();
        }

        return call(getAllMembers, type);
    }

    @Override
    public List<? extends AnnotationMirror> getAllAnnotationMirrors(
        Element e) {
        if (MissingTypeElement.isMissing(e)) {
            return Collections.emptyList();
        }
        return call(getAllAnnotationMirrors, e);
    }

    @Override
    public boolean hides(Element hider, Element hidden) {
        if (MissingTypeElement.isMissing(hider) || MissingTypeElement.isMissing(hidden)) {
            return false;
        }

        return call(hides, hider, hidden);
    }

    @Override
    public boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
        if (MissingTypeElement.isMissing(type)) {
            return false;
        }

        return call(overrides, overrider, overridden, type);
    }

    @Override
    public String getConstantExpression(Object value) {
        return call(getConstantExpression, value);
    }

    @Override
    public void printElements(Writer w, Element... elements) {
        call(printElements, w, elements);
    }

    @Override
    public Name getName(CharSequence cs) {
        try {
            return elements.getName(cs);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
