package org.revapi.java.matcher;

import java.util.stream.Stream;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

public class IsTypeParameterOfExpression extends AbstractFullScanRequiringExpression {

    private static final TypeVisitor<Stream<? extends TypeMirror>, Void> TYPE_PARAMS_EXTRACTOR =
            new SimpleTypeVisitor8<Stream<? extends TypeMirror>, Void>() {
                @Override
                protected Stream<? extends TypeMirror> defaultAction(TypeMirror e, Void __) {
                    return Stream.empty();
                }

                @Override
                public Stream<? extends TypeMirror> visitDeclared(DeclaredType t, Void __) {
                    return t.getTypeArguments().stream().flatMap(ta ->
                            ta.accept(new SimpleTypeVisitor8<Stream<? extends TypeMirror>, Void>() {
                                @Override
                                protected Stream<? extends TypeMirror> defaultAction(TypeMirror e, Void __) {
                                    return Stream.empty();
                                }

                                @Override
                                public Stream<? extends TypeMirror> visitIntersection(IntersectionType t, Void __) {
                                    return t.getBounds().stream();
                                }

                                @Override
                                public Stream<? extends TypeMirror> visitDeclared(DeclaredType t, Void __) {
                                    return Stream.of(t);
                                }

                                @Override
                                public Stream<? extends TypeMirror> visitTypeVariable(TypeVariable t, Void __) {
                                    return Stream.of(t.getUpperBound());
                                }
                            }, null));
                }

                @Override
                public Stream<? extends TypeMirror> visitExecutable(ExecutableType t, Void __) {
                    return t.getTypeVariables().stream().map(TypeVariable::getUpperBound);
                }
            };

    IsTypeParameterOfExpression(MatchExpression scan) {
        super(scan);
    }

    @Override
    protected FilterMatch matchesAfterScan(JavaModelElement element) {
        return matches(typeOf(element));
    }

    @Override
    protected FilterMatch matchesAfterScan(JavaAnnotationElement element) {
        return matches(typeOf(element));
    }

    @Override
    protected FilterMatch matchesAfterScan(AnnotationAttributeElement element) {
        return matches(typeOf(element));
    }

    @Override
    protected FilterMatch matchesAfterScan(TypeParameterElement element) {
        return matches(typeOf(element));
    }

    private FilterMatch matches(JavaTypeElement type) {
        if (type == null) {
            return FilterMatch.DOESNT_MATCH;
        }

        String typeStr = Util.toUniqueString(type.getModelRepresentation());

        boolean matches = getMatchedInScan().stream()
                .filter(e -> e instanceof JavaModelElement)
                .map(e -> (JavaModelElement) e)
                .flatMap(e -> e.getModelRepresentation().accept(TYPE_PARAMS_EXTRACTOR, null))
                .anyMatch(t -> typeStr.equals(Util.toUniqueString(t)));

        return FilterMatch.fromBoolean(matches);
    }
}
