package org.revapi.java.matcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import java.io.Reader;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
public final class JavaElementMatcher implements ElementMatcher {
    private final Map<String, MatchExpression> parsedMatchers = new HashMap<>();

    @Override
    public boolean matches(String recipe, Element element) {
        if (!(element instanceof JavaElement)) {
            return false;
        }

        MatchExpression matcher = parsedMatchers.computeIfAbsent(recipe, this::createMatcher);
        return matcher.matches((JavaElement) element);
    }

    @Override
    public void close() throws Exception {
        parsedMatchers.clear();
    }

    @Override
    public String getExtensionId() {
        return "matcher.java";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        parsedMatchers.clear();
    }

    private MatchExpression createMatcher(String recipe) {
        MatchExpression[] result = new MatchExpression[1];

        ElementMatcherParser parser = createNewParser(recipe);
        parser.addParseListener(new ElementMatcherBaseListener() {
            //TODO implement

            @Override
            public void exitBinaryExpression(ElementMatcherParser.BinaryExpressionContext ctx) {
                String attachment = ctx.getChild(0).getText();
                String op = ctx.getChild(1).getText();
                String value = ctx.getChild(2).getText();

                DataExtractor<?> extractor = getExtractorForAttachment(attachment);
                BinaryOperator operator = BinaryOperator.fromSymbol(op);
                MatcherInstance<?> matcher = getMatcherFromValue(value);

                BinaryExpression<?> expr = createExpression(extractor, matcher, operator);
                if (expr == null) {
                    throw new IllegalArgumentException("Value " + value + " is not compatible with requested element data '" + attachment + "'.");
                }

                result[0] = expr;
            }

            @Override
            public void exitUnaryExpression(ElementMatcherParser.UnaryExpressionContext ctx) {
                super.exitUnaryExpression(ctx);
            }


            private <T> BinaryExpression<T> createExpression(DataExtractor<T> extractor, MatcherInstance<?> matcher, BinaryOperator operator) {
                MatcherInstance<T> matchingMatcher = MatcherInstance.ifCompatible(matcher, extractor.extractedType());
                if (matchingMatcher == null) {
                    return null;
                }
                return new BinaryExpression<>(extractor, matchingMatcher, operator);
            }

            private DataExtractor<?> getExtractorForAttachment(String attachment) {
                switch (attachment) {
                    case "kind":
                        return new ElementKindExtractor();
                    case "package":
                        return new ElementPackageExtractor();
                    case "class":
                        return new ElementClassExtractor();
                    case "name":
                        return new NameExtractor();
                    case "signature":
                        return new SignatureExtractor();
                    case "erasedSignature":
                        return new ErasedSignatureExtractor();
                    case "representation":
                        return new RepresentationExtractor();
                    case "index":
                        return new ParameterIndexExtractor();
                    case "returnType":
                        return new ReturnTypeExtractor();
                    default:
                        throw new IllegalArgumentException("Unsupported attachment of a java element: '" + attachment + "'.");
                }
            }

            private MatcherInstance<?> getMatcherFromValue(String value) {
                switch (value.charAt(0)) {
                    case '/':
                        return new PatternMatcher(Pattern.compile(value.substring(1, value.length() - 1)));
                    case '\'':
                        return new StringMatcher(value.substring(1, value.length() - 1));
                    default:
                        return new IndexMatcher(Integer.valueOf(value));
                }
            }
        });

        ElementMatcherParser.ExpressionContext ctx = parser.expression();
        if (ctx.exception != null) {
            throw new IllegalArgumentException("Failed to parse the expression", ctx.exception);
        }

        return result[0];
    }

    private interface Operator {
        String getSymbol();

        boolean isUnary();
    }

    private enum BinaryOperator implements Operator {
        EQUALS("="), NOT_EQUALS("!="), GT(">"), LT("<"), GE(">="), LE("<=");

        private final String symbol;

        BinaryOperator(String symbol) {
            this.symbol = symbol;
        }

        public static BinaryOperator fromSymbol(String symbol) {
            for (BinaryOperator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }

            return null;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public boolean isUnary() {
            return false;
        }
    }

    private enum UnaryOperator implements Operator {
        CONTAINS("contains"), IS_CONTAINED_IN("isContainedId"), IMPLEMENTS("implements"),
        IS_IMPLEMENTED_BY("isImplementedBy"), EXTENDS("extends"), IS_EXTENDED_BY("isExtendedBy"),
        ANNOTATES("isAnnotatedBy"), IS_ANNOTATED_BY("isAnnotatedBy"), OVERRIDES("overrides"),
        IS_OVERRIDEN_BY("isOverridenBy"), HAS_TYPE("hasType"), HAS_RETURN_TYPE("hasReturnType"),
        THROWS("throws"), IS_THROWN_BY("isThrownBy"), PARAMETERIZES("parameterizes"),
        IS_PARAMETERIZED_BY("isParameterizedBy"), IS_ARGUMENT_OF("isArgumentOf");

        private final String symbol;

        UnaryOperator(String symbol) {
            this.symbol = symbol;
        }

        public static UnaryOperator fromSymbol(String symbol) {
            for (UnaryOperator op : values()) {
                if (op.symbol.equals(symbol)) {
                    return op;
                }
            }

            return null;
        }

        @Override
        public String getSymbol() {
            return symbol;
        }

        @Override
        public boolean isUnary() {
            return true;
        }
    }

    private interface MatchExpression {
        boolean matches(JavaElement element);
    }

    private static class BinaryExpression<T> implements MatchExpression {
        private final DataExtractor<T> dataExtractor;
        private final MatcherInstance<T> matcher;
        private final BinaryOperator operator;

        private BinaryExpression(DataExtractor<T> dataExtractor, MatcherInstance<T> matcher, BinaryOperator operator) {
            this.dataExtractor = dataExtractor;
            this.matcher = matcher;
            this.operator = operator;
        }

        @Override
        public boolean matches(JavaElement value) {
            T val = dataExtractor.extract(value);
            return matcher.matches(val, operator);
        }
    }

    private enum LogicalOperator {
        AND, OR, NOT
    }

    private static ElementMatcherParser createNewParser(String recipe) {
        ElementMatcherLexer lexer = new ElementMatcherLexer(CharStreams.fromString(recipe));
        return new ElementMatcherParser(new CommonTokenStream(lexer));
    }

    private static <T extends Element> T findParentWithType(Element element, Class<T> type) {
        Element el = element.getParent();
        while (el != null && !type.isInstance(el)) {
            el = el.getParent();
        }
        return el == null ? null : type.cast(el);
    }

    private interface MatcherInstance<T> {
        boolean matches(T value, BinaryOperator operator);

        default boolean supports(Class<?> type) {
            ParameterizedType ptype = (ParameterizedType) this.getClass().getGenericInterfaces()[0];
            return type.getName().equals(ptype.getActualTypeArguments()[0].getTypeName());
        }

        @SuppressWarnings("unchecked")
        static <X> MatcherInstance<X> ifCompatible(MatcherInstance<?> inst, Class<X> type) {
            if (inst.supports(type)) {
                return (MatcherInstance<X>) inst;
            } else {
                return null;
            }
        }
    }

    private interface DataExtractor<T> {
        T extract(JavaElement element);
        Class<T> extractedType();
    }

    private static class StringMatcher implements MatcherInstance<String> {
        private final String string;

        private StringMatcher(String string) {
            this.string = string;
        }

        @Override
        public boolean matches(String value, BinaryOperator operator) {
            switch (operator) {
                case EQUALS:
                    return string.equals(value);
                case NOT_EQUALS:
                    return !string.equals(value);
                default:
                    throw new IllegalArgumentException("String comparison only supports '=' and '!=' operators.");
            }
        }
    }

    private static class PatternMatcher implements MatcherInstance<String> {
        private final Pattern pattern;

        private PatternMatcher(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean matches(String value, BinaryOperator operator) {
            switch (operator) {
                case EQUALS:
                    return pattern.matcher(value).matches();
                case NOT_EQUALS:
                    return !pattern.matcher(value).matches();
                default:
                    throw new IllegalArgumentException("Regex comparison only supports '=' and '!=' operators.");
            }
        }
    }

    private static final class IndexMatcher implements MatcherInstance<Integer> {
        private final int index;

        private IndexMatcher(int index) {
            this.index = index;
        }

        @Override
        public boolean matches(Integer value, BinaryOperator operator) {
            switch (operator) {
                case EQUALS:
                    return index == value;
                case NOT_EQUALS:
                    return index != value;
                case GE:
                    return index >= value;
                case GT:
                    return index > value;
                case LE:
                    return index <= value;
                case LT:
                    return index < value;
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
        }
    }

    private static final class ElementKindExtractor implements DataExtractor<String> {
        @Override
        public String extract(JavaElement element) {
            if (element instanceof JavaModelElement) {
                ElementKind kind = ((JavaModelElement) element).getDeclaringElement().getKind();
                switch (kind) {
                    //not supported ATM
                    //case PACKAGE:
                    //    return "package";
                    case ENUM:
                        return "enum";
                    case CLASS:
                        return "class";
                    case ANNOTATION_TYPE:
                        return "annotationType";
                    case INTERFACE:
                        return "interface";
                    case ENUM_CONSTANT:
                        return "enumConstant";
                    case FIELD:
                        return "field";
                    case PARAMETER:
                        return "parameter";
                    case METHOD:
                        return "method";
                    case CONSTRUCTOR:
                        return "constructor";
                    //not supported ATM
                    //case TYPE_PARAMETER:
                    //    return "typeParameter";
                    default:
                        throw new IllegalArgumentException("Unsupported element kind: '" + kind + "'.");
                }
            } else if (element instanceof JavaAnnotationElement) {
                return "annotation";
            } else {
                throw new IllegalArgumentException("Cannot find an element kind of element of type '" + element.getClass() + "'.");
            }
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class ElementPackageExtractor implements DataExtractor<String> {
        @Override
        public String extract(JavaElement element) {
            JavaTypeElement type = element instanceof JavaTypeElement
                    ? (JavaTypeElement) element
                    : findParentWithType(element, JavaTypeElement.class);

            while (type != null && type.getParent() != null) {
                type = findParentWithType(type, JavaTypeElement.class);
            }

            if (type == null) {
                return null;
            } else {
                PackageElement pkg = (PackageElement) type.getDeclaringElement().getEnclosingElement();
                return pkg == null ? null : pkg.getQualifiedName().toString();
            }
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class ElementClassExtractor implements DataExtractor<String> {

        @Override
        public String extract(JavaElement element) {
            JavaTypeElement type = element instanceof JavaTypeElement
                    ? (JavaTypeElement) element
                    : findParentWithType(element, JavaTypeElement.class);

            return type == null ? null : Util.toHumanReadableString(type.getModelRepresentation());
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class NameExtractor implements DataExtractor<String> {

        @Override
        public String extract(JavaElement element) {
            if (element instanceof JavaModelElement) {
                return ((JavaModelElement) element).getDeclaringElement().getSimpleName().toString();
            } else if (element instanceof JavaAnnotationElement) {
                return ((JavaAnnotationElement) element).getAnnotation().getAnnotationType().asElement().getSimpleName().toString();
            } else {
                return null;
            }
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class SignatureExtractor implements DataExtractor<String> {

        @Override
        public String extract(JavaElement element) {
            if (element instanceof JavaModelElement) {
                return Util.toHumanReadableString(((JavaModelElement) element).getModelRepresentation());
            } else if (element instanceof JavaAnnotationElement) {
                return Util.toHumanReadableString(((JavaAnnotationElement) element).getAnnotation());
            } else {
                return null;
            }
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class ErasedSignatureExtractor implements DataExtractor<String> {

        @Override
        public String extract(JavaElement element) {
            if (element instanceof JavaModelElement) {
                Types tps = element.getTypeEnvironment().getTypeUtils();
                return Util.toHumanReadableString(tps.erasure(((JavaModelElement) element).getModelRepresentation()));
            } else if (element instanceof JavaAnnotationElement) {
                return Util.toHumanReadableString(((JavaAnnotationElement) element).getAnnotation());
            } else {
                return null;
            }
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class RepresentationExtractor implements DataExtractor<String> {

        @Override
        public String extract(JavaElement element) {
            return element.getFullHumanReadableString();
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }

    private static final class ParameterIndexExtractor implements DataExtractor<Integer> {

        @Override
        public Integer extract(JavaElement element) {
            if (element instanceof MethodParameterElement) {
                return ((MethodParameterElement) element).getIndex();
            }
            return null;
        }

        @Override
        public Class<Integer> extractedType() {
            return Integer.class;
        }
    }

    private static final class ReturnTypeExtractor implements DataExtractor<String> {

        @Override
        public String extract(JavaElement element) {
            if (element instanceof MethodElement) {
                TypeMirror returnType = ((MethodElement) element).getModelRepresentation().getReturnType();
                return Util.toHumanReadableString(returnType);
            }
            return null;
        }

        @Override
        public Class<String> extractedType() {
            return String.class;
        }
    }
}
