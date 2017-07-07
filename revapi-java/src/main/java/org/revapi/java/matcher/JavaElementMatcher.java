/*
 * Copyright 2015-2017 Lukas Krejci
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
 *
 */

package org.revapi.java.matcher;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RuleContext;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.java.matcher.ElementMatcherParser.ExpressionContext;
import org.revapi.java.matcher.ElementMatcherParser.TopExpressionContext;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaFieldElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;

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
        Deque<MatchExpression> expressionStack = new ArrayDeque<>();

        ElementMatcherParser parser = createNewParser(recipe);
        parser.addParseListener(new ElementMatcherBaseListener() {

            @Override
            public void enterTopExpression(TopExpressionContext ctx) {
                expressionStack.clear();
            }

            @Override
            public void exitTopExpression(TopExpressionContext ctx) {
                if (ctx.getChildCount() == 3) {
                    exitLogicalExpression(ctx.getChild(1).getText());
                }
            }

            @Override
            public void exitExpression(ExpressionContext ctx) {
                if (ctx.getChildCount() == 3) {
                    exitLogicalExpression(ctx.getChild(1).getText());
                }
            }

            @Override
            public void exitThrowsExpression(ElementMatcherParser.ThrowsExpressionContext ctx) {
                MatchExpression throwMatch = ctx.subExpression() == null
                        ? null
                        : convertNakedStringOrRegexUsing(expressionStack.pop(), InstantiationExtractor::new);

                MatchExpression expr = new ThrowsExpression(throwMatch);

                if (throwMatch != null && "doesn't".equals(ctx.getChild(0).getText())) {
                    expr = new NegatingExpression(expr);
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitSubTypeExpression(ElementMatcherParser.SubTypeExpressionContext ctx) {
                MatchExpression superType = convertNakedStringOrRegexUsing(expressionStack.pop(), InstantiationExtractor::new);

                int defStart = 0;
                boolean negate = false;
                if ("doesn't".equals(ctx.getChild(0).getText())) {
                    defStart = 1;
                    negate = true;
                }

                boolean directDescendant = "directly".equals(ctx.getChild(defStart).getText());
                if (directDescendant) {
                    defStart += 1;
                }

                boolean searchInterfaces = "implements".equals(ctx.getChild(defStart).getText());

                MatchExpression expr = new SubTypeExpression(superType, directDescendant, searchInterfaces);

                if (negate) {
                    expr = new NegatingExpression(expr);
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitOverridesExpression(ElementMatcherParser.OverridesExpressionContext ctx) {
                MatchExpression overridden = ctx.subExpression() == null
                        ? null
                        : convertNakedStringOrRegexUsing(expressionStack.pop(), InstantiationExtractor::new);

                MatchExpression expr = new OverridesExpression(overridden);

                if ("doesn't".equals(ctx.getChild(0).getText())) {
                    expr = new NegatingExpression(expr);
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitReturnsExpression(ElementMatcherParser.ReturnsExpressionContext ctx) {
                MatchExpression returns = convertNakedStringOrRegexUsing(expressionStack.pop(), InstantiationExtractor::new);

                int defStart = 0;
                boolean negate = false;
                if ("doesn't".equals(ctx.getChild(0).getText())) {
                    defStart = 1;
                    negate = true;
                }

                boolean covariant = !"precisely".equals(ctx.getChild(defStart + 1).getText());

                MatchExpression expr = new ReturnsExpression(returns, covariant);

                if (negate) {
                    expr = new NegatingExpression(expr);
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitHasExpression_basic(ElementMatcherParser.HasExpression_basicContext ctx) {
                MatchExpression expr = expressionStack.pop();

                if ("doesn't".equals(ctx.getChild(0).getText())) {
                    expr = new NegatingExpression(expr);
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitHasExpression_match(ElementMatcherParser.HasExpression_matchContext ctx) {
                int matchIdx = 1;
                DataExtractor<String> extractor;

                if (ctx.getChildCount() == 3) {
                    matchIdx = 2;
                    //this should be the signature match
                    if (!"signature".equals(ctx.getChild(1).getText())) {
                        throw new IllegalArgumentException("Unexpected match expression: " + ctx.getText());
                    }

                    switch (ctx.getChild(0).getText()) {
                        case "erased":
                            extractor = new ErasedSignatureExractor();
                            break;
                        case "generic":
                            extractor = new GenericSignatureExtractor();
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown signature type in: " + ctx.getText());
                    }
                } else {
                    switch (ctx.getChild(0).getText()) {
                        case "name":
                            extractor = new NameExtractor();
                            break;
                        case "signature":
                            extractor = new SignatureExtractor();
                            break;
                        case "representation":
                            extractor = new RepresentationExtractor();
                            break;
                        case "kind":
                            extractor = new ElementKindExtractor();
                            break;
                        case "package":
                            extractor = new PackageExtractor();
                            break;
                        case "simpleName":
                            extractor = new SimpleNameExtractor();
                            break;
                        default:
                            throw new IllegalArgumentException("Unexpected match expression: " + ctx.getText());
                    }
                }

                String stringOrRegex = ctx.getChild(matchIdx).getText();

                MatchExpression expr;

                if (isRegex(stringOrRegex)) {
                    expr = new PatternExpression(extractor, extractStringOrRegex(stringOrRegex));
                } else {
                    expr = new StringExpression(extractor, extractStringOrRegex(stringOrRegex));
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitHasExpression_arguments(ElementMatcherParser.HasExpression_argumentsContext ctx) {
                boolean negate = "doesn't".equals(ctx.getChild(0).getText());
                ComparisonOperator operator;
                int numberIdx;

                if (negate) {
                    if (ctx.getChildCount() == 4) {
                        numberIdx = 2;
                        operator = ComparisonOperator.NE;
                    } else {
                        numberIdx = 4;
                        operator = "more".equals(ctx.getChild(2).getText()) ? ComparisonOperator.LTE : ComparisonOperator.GTE;
                    }
                } else {
                    if (ctx.getChildCount() == 3) {
                        numberIdx = 1;
                        operator = ComparisonOperator.EQ;
                    } else {
                        numberIdx = 3;
                        operator = "more".equals(ctx.getChild(1).getText()) ? ComparisonOperator.GT : ComparisonOperator.LT;
                    }
                }

                int expectedArguments = Integer.parseInt(ctx.getChild(numberIdx).getText());

                expressionStack.push(new NumberOfArgumentsExpression(operator, expectedArguments));
            }

            @Override
            public void exitHasExpression_index(ElementMatcherParser.HasExpression_indexContext ctx) {
                boolean negate = "doesn't".equals(ctx.getChild(0).getText());
                ComparisonOperator operator;
                int numberIdx;

                if (negate) {
                    if (ctx.getChildCount() == 4) {
                        numberIdx = 2;
                        operator = ComparisonOperator.NE;
                    } else {
                        numberIdx = 5;
                        operator = "larger".equals(ctx.getChild(3).getText()) ? ComparisonOperator.LTE : ComparisonOperator.GTE;
                    }
                } else {
                    if (ctx.getChildCount() == 3) {
                        numberIdx = 2;
                        operator = ComparisonOperator.EQ;
                    } else {
                        numberIdx = 4;
                        operator = "larger".equals(ctx.getChild(2).getText()) ? ComparisonOperator.GT : ComparisonOperator.LT;
                    }
                }

                int expectedIndex = Integer.parseInt(ctx.getChild(numberIdx).getText());

                expressionStack.push(new ArgumentIndexExpression(operator, expectedIndex));
            }

            @Override
            public void exitHasExpression_subExpr(ElementMatcherParser.HasExpression_subExprContext ctx) {
                MatchExpression match = expressionStack.pop();

                ChoiceProducer choice = null;

                boolean alreadyPushed = false;

                switch (ctx.getChild(0).getText()) {
                    case "argument":
                        match = convertNakedStringOrRegexUsing(match, SignatureExtractor::new);
                        Integer concreteIdx = null;
                        if (ctx.getChildCount() == 3) {
                            concreteIdx = Integer.parseInt(ctx.getChild(1).getText());
                        }
                        choice = new ArgumentsProducer(concreteIdx);
                        break;
                    case "annotation":
                        match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                        choice = new AnnotationsProducer();
                        break;
                    case "method":
                        match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                        choice = new ContainedElementProducer(false, JavaMethodElement.class);
                        break;
                    case "field":
                        match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                        choice = new ContainedElementProducer(false, JavaFieldElement.class);
                        break;
                    case "outerClass":
                        match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                        expressionStack.push(new HasOuterClassExpression(false, match));
                        alreadyPushed = true;
                        break;
                    case "innerClass":
                        match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                        choice = new ContainedElementProducer(false, JavaTypeElement.class);
                        break;
                    case "superType":
                        match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                        expressionStack.push(new HasSuperTypeExpression(false, match));
                        alreadyPushed = true;
                        break;
                    case "type":
                        match = convertNakedStringOrRegexUsing(match, SignatureExtractor::new);
                        expressionStack.push(match);
                        alreadyPushed = true;
                        break;
                    case "direct":
                        switch (ctx.getChild(1).getText()) {
                            case "outerClass":
                                match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                                expressionStack.push(new HasOuterClassExpression(true, match));
                                alreadyPushed = true;
                                break;
                            case "superType":
                                match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                                expressionStack.push(new HasSuperTypeExpression(true, match));
                                alreadyPushed = true;
                                break;
                        }
                        break;
                    case "declared":
                        switch (ctx.getChild(1).getText()) {
                            case "method":
                                match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                                choice = new ContainedElementProducer(true, JavaMethodElement.class);
                                break;
                            case "field":
                                match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                                choice = new ContainedElementProducer(true, JavaFieldElement.class);
                                break;
                            case "innerClass":
                                match = convertNakedStringOrRegexUsing(match, InstantiationExtractor::new);
                                choice = new ContainedElementProducer(true, JavaTypeElement.class);
                                break;
                        }
                        break;
                }

                if (!alreadyPushed) {
                    expressionStack.push(new ChoiceExpression(match, choice));
                }
            }

            @Override
            public void exitStringExpression(ElementMatcherParser.StringExpressionContext ctx) {
                expressionStack.push(new DataExtractorNeededMarker(DataExtractorNeededMarker.MatcherKind.STRING,
                        extractStringOrRegexValue(ctx)));
            }

            @Override
            public void exitRegexExpression(ElementMatcherParser.RegexExpressionContext ctx) {
                expressionStack.push(new DataExtractorNeededMarker(DataExtractorNeededMarker.MatcherKind.REGEX,
                        extractStringOrRegexValue(ctx)));
            }

            private void exitLogicalExpression(String operator) {
                MatchExpression right = convertNakedStringOrRegexUsing(expressionStack.pop(),
                        RepresentationExtractor::new);
                MatchExpression left = convertNakedStringOrRegexUsing(expressionStack.pop(),
                        RepresentationExtractor::new);
                expressionStack.push(new LogicalExpression(left, LogicalOperator.fromString(operator), right));
            }

            private <E extends DataExtractor<String>>
            MatchExpression convertNakedStringOrRegexUsing(MatchExpression naked, Supplier<E> extractorCtor) {
                if (!(naked instanceof DataExtractorNeededMarker)) {
                    return naked;
                }

                DataExtractorNeededMarker marker = (DataExtractorNeededMarker) naked;

                switch (marker.getMatcherKind()) {
                    case STRING:
                        return new StringExpression(extractorCtor.get(), marker.getValue());
                    case REGEX:
                        return new PatternExpression(extractorCtor.get(), marker.getValue());
                }
                return naked;
            }

            private String extractStringOrRegexValue(RuleContext ctx) {
                return extractStringOrRegex(ctx.getText());
            }

            private boolean isRegex(String stringOrRegex) {
                return stringOrRegex.charAt(0) == '/';
            }

            private String extractStringOrRegex(String value) {
                return value.substring(1, value.length() - 1);
            }

            //TODO implement
        });

        TopExpressionContext ctx = parser.topExpression();
        if (ctx.exception != null) {
            throw new IllegalArgumentException("Failed to parse the expression", ctx.exception);
        }

        return expressionStack.pop();
    }

    private static final class DataExtractorNeededMarker implements MatchExpression {
        private final MatcherKind matcherKind;
        private final String value;

        private DataExtractorNeededMarker(MatcherKind matcherKind, String value) {
            this.matcherKind = matcherKind;
            this.value = value;
        }

        MatcherKind getMatcherKind() {
            return matcherKind;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean matches(JavaModelElement element) {
            throw new IllegalStateException("Internal expression should never be evaluated. This is a bug.");
        }

        @Override
        public boolean matches(JavaAnnotationElement annotation) {
            throw new IllegalStateException("Internal expression should never be evaluated. This is a bug.");
        }

        enum MatcherKind {
            STRING, REGEX
        }
    }

    private static ElementMatcherParser createNewParser(String recipe) {
        ElementMatcherLexer lexer = new ElementMatcherLexer(CharStreams.fromString(recipe));
        return new ElementMatcherParser(new CommonTokenStream(lexer));
    }

    //
//    private static final class ParameterIndexExtractor implements DataExtractor<Integer> {
//
//        @Override
//        public Integer extract(JavaElement element) {
//            if (element instanceof MethodParameterElement) {
//                return ((MethodParameterElement) element).getIndex();
//            }
//            return null;
//        }
//
//        @Override
//        public Class<Integer> extractedType() {
//            return Integer.class;
//        }
//    }
//
//    private static final class ReturnTypeExtractor implements DataExtractor<String> {
//
//        @Override
//        public String extract(JavaElement element) {
//            if (element instanceof MethodElement) {
//                TypeMirror returnType = ((MethodElement) element).getModelRepresentation().getReturnType();
//                return Util.toHumanReadableString(returnType);
//            }
//            return null;
//        }
//
//        @Override
//        public Class<String> extractedType() {
//            return String.class;
//        }
//    }
//
//    private static final class ErasedSignatureExtractor implements DataExtractor<String> {
//
//        @Override
//        public String extract(JavaElement element) {
//            if (element instanceof JavaModelElement) {
//                Types tps = element.getTypeEnvironment().getTypeUtils();
//                return Util.toHumanReadableString(tps.erasure(((JavaModelElement) element).getModelRepresentation()));
//            } else if (element instanceof JavaAnnotationElement) {
//                return Util.toHumanReadableString(((JavaAnnotationElement) element).getAnnotation());
//            } else {
//                return null;
//            }
//        }
//
//        @Override
//        public Class<String> extractedType() {
//            return String.class;
//        }
//    }
//
}
