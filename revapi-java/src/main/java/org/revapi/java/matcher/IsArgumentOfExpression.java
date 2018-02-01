package org.revapi.java.matcher;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.lang.model.type.TypeMirror;

import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

public class IsArgumentOfExpression extends AbstractFullScanRequiringExpression {
    private final Integer order;

    IsArgumentOfExpression(MatchExpression scan, Integer order) {
        super(scan);
        this.order = order;
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

    private FilterMatch matches(JavaTypeElement typeElement) {
        if (typeElement == null) {
            return FilterMatch.DOESNT_MATCH;
        }

        String type = Util.toUniqueString(typeElement.getModelRepresentation());

        Stream<JavaMethodElement> subMatches = getMatchedInScan().stream()
                .filter(e -> e instanceof JavaMethodElement)
                .map(e -> (JavaMethodElement) e);

        Predicate<TypeMirror> test = t -> Util.toUniqueString(t).equals(type);

        Stream<TypeMirror> params;

        if (order == null) {
            params = subMatches.flatMap(m -> m.getModelRepresentation().getParameterTypes().stream());
        } else {
            params = subMatches.flatMap(m -> {
                List<? extends TypeMirror> paramTypes = m.getModelRepresentation().getParameterTypes();

                if (paramTypes.size() < order) {
                    return Stream.empty();
                } else {
                    return Stream.of(paramTypes.get(order));
                }
            });
        }

        return FilterMatch.fromBoolean(params.anyMatch(test));
    }
}
