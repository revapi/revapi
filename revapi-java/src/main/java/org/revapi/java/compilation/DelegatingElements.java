package org.revapi.java.compilation;

import java.io.Writer;
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

/**
 * @author Lukas Krejci
 * @since 0.1
 */
class DelegatingElements implements Elements {
    private final Elements elements;

    DelegatingElements(Elements elements) {
        this.elements = elements;
    }

    @Override
    public PackageElement getPackageElement(CharSequence name) {
        return elements.getPackageElement(name);
    }

    @Override
    public TypeElement getTypeElement(CharSequence name) {
        return elements.getTypeElement(name);
    }

    @Override
    public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
        AnnotationMirror a) {
        return elements.getElementValuesWithDefaults(a);
    }

    @Override
    public String getDocComment(Element e) {
        return elements.getDocComment(e);
    }

    @Override
    public boolean isDeprecated(Element e) {
        return elements.isDeprecated(e);
    }

    @Override
    public Name getBinaryName(TypeElement type) {
        return elements.getBinaryName(type);
    }

    @Override
    public PackageElement getPackageOf(Element type) {
        return elements.getPackageOf(type);
    }

    @Override
    public List<? extends Element> getAllMembers(
        TypeElement type) {
        return elements.getAllMembers(type);
    }

    @Override
    public List<? extends AnnotationMirror> getAllAnnotationMirrors(
        Element e) {
        return elements.getAllAnnotationMirrors(e);
    }

    @Override
    public boolean hides(Element hider, Element hidden) {
        return elements.hides(hider, hidden);
    }

    @Override
    public boolean overrides(ExecutableElement overrider,
        ExecutableElement overridden, TypeElement type) {
        return elements.overrides(overrider, overridden, type);
    }

    @Override
    public String getConstantExpression(Object value) {
        return elements.getConstantExpression(value);
    }

    @Override
    public void printElements(Writer w, Element... elements) {
        this.elements.printElements(w, elements);
    }

    @Override
    public Name getName(CharSequence cs) {
        return elements.getName(cs);
    }
}
