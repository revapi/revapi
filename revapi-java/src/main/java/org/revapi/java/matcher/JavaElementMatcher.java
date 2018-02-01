/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java.matcher;

import static java.util.stream.Collectors.toList;

import java.io.Reader;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.revapi.AnalysisContext;
import org.revapi.ElementGateway;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;
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
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        try {
            MatchExpression expr = createMatcher(recipe);

            return Optional.of((stage, element) -> {
                if (!(element instanceof JavaElement)) {
                    return FilterMatch.DOESNT_MATCH;
                }

                return expr.matches(stage, (JavaElement) element);
            });
        } catch (IllegalArgumentException __) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws Exception {
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
    }

    private MatchExpression createMatcher(String recipe) {
        Deque<MatchExpression> expressionStack = new ArrayDeque<>();

        ElementMatcherParser parser = createNewParser(recipe, new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                throw new ParseCancellationException("Syntax error @ " + line + ":" + charPositionInLine + " - " + msg);
            }
        });

        parser.addParseListener(new ElementMatcherBaseListener() {

            @Override
            public void enterTopExpression(TopExpressionContext ctx) {
                expressionStack.clear();
            }

            @Override
            public void exitTopExpression(TopExpressionContext ctx) {
                if (ctx.getChildCount() == 3) {
                    exitLogicalExpression(ctx.getChild(1).getText());
                } else if (ctx.getChildCount() == 1 && expressionStack.peek() instanceof DataExtractorNeededMarker) {
                    expressionStack.push(
                            convertNakedStringOrRegexUsing(expressionStack.pop(), RepresentationExtractor::new));
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
                            extractor = new ErasedSignatureExtractor();
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

            @SuppressWarnings("Duplicates")
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

                Integer concreteIdx = null;

                switch (ctx.getChild(0).getText()) {
                    case "argument":
                        match = convertNakedStringOrRegexUsing(match, SignatureExtractor::new);
                        if (ctx.getChildCount() == 3) {
                            concreteIdx = Integer.parseInt(ctx.getChild(1).getText());
                        }
                        choice = new ArgumentsProducer(concreteIdx);
                        break;
                    case "annotation":
                        match = convertNakedStringOrRegexUsing(match, NameExtractor::new);
                        choice = new AnnotationsProducer(false);
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
                    case "typeParameter":
                        concreteIdx = null;
                        if (ctx.NUMBER() != null) {
                            concreteIdx = Integer.parseInt(ctx.NUMBER().getText());
                        }
                        match = convertNakedStringOrRegexUsing(match, SignatureExtractor::new);
                        choice = new TypeParameterProducer(concreteIdx);
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
                            case "annotation":
                                match = convertNakedStringOrRegexUsing(match, NameExtractor::new);
                                choice = new AnnotationsProducer(true);
                                break;
                        }
                        break;
                }

                if (!alreadyPushed) {
                    expressionStack.push(new ChoiceExpression(match, choice));
                }
            }

            @SuppressWarnings("Duplicates")
            @Override
            public void exitHasExpression_typeParameters(ElementMatcherParser.HasExpression_typeParametersContext ctx) {
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

                expressionStack.push(new NumberOfTypeParametersExpression(operator, expectedArguments));
            }

            @Override
            public void exitHasExpression_typeParameterBounds(
                    ElementMatcherParser.HasExpression_typeParameterBoundsContext ctx) {
                MatchExpression subExpr = convertNakedStringOrRegexUsing(expressionStack.pop(), SignatureExtractor::new);
                int boundTypeIdx = "doesn't".equals(textAt(ctx, 0)) ? 2 : 1;

                String boundType = textAt(ctx, boundTypeIdx);
                switch (boundType) {
                    case "lower":
                        expressionStack.push(new TypeParameterBoundExpression(subExpr, true));
                        break;
                    case "upper":
                        expressionStack.push(new TypeParameterBoundExpression(subExpr, false));
                        break;
                    default:
                        throw new IllegalStateException("Unrecognized type parameter bound type. Expected 'lower' or 'upper' but got: " + boundType);
                }
            }

            @Override
            public void exitHasExpression_attribute(ElementMatcherParser.HasExpression_attributeContext ctx) {
                boolean explicit;
                String attributeName = null;
                MatchExpression attributeMatch = null;

                switch (ctx.getChildCount()) {
                    case 1:
                        explicit = false;
                        attributeName = null;
                        attributeMatch = null;
                        break;
                    case 2:
                        if ("explicit".equals(ctx.getChild(0).getText())) {
                            explicit = true;
                            attributeName = null;
                            attributeMatch = null;
                        } else {
                            explicit = false;
                            attributeName = ctx.getChild(1).getText();
                            attributeMatch = null;
                        }
                        break;
                    case 3:
                        if ("explicit".equals(ctx.getChild(0).getText())) {
                            explicit = true;
                            if (ctx.hasExpression_attribute_values() == null) {
                                attributeName = ctx.getChild(2).getText();
                            } else {
                                attributeMatch = expressionStack.pop();
                            }
                        } else {
                            explicit = false;
                            attributeName = null;
                            attributeMatch = expressionStack.pop();
                        }
                        break;
                    case 4:
                        if ("explicit".equals(ctx.getChild(0).getText())) {
                            explicit = true;
                            attributeName = null;
                            attributeMatch = expressionStack.pop();
                        } else {
                            explicit = false;
                            attributeName = ctx.getChild(1).getText();
                            attributeMatch = expressionStack.pop();
                        }
                        break;
                    case 5:
                        explicit = true;
                        attributeName = ctx.getChild(2).getText();
                        attributeMatch = expressionStack.pop();
                        break;
                    default:
                        throw new IllegalArgumentException("Expecting 1 to 5 children in expression '" + ctx.getText()
                                + "' but got " + ctx.getChildCount());
                }

                MatchExpression attributeNameMatch = null;
                if (attributeName != null) {
                    String val = extractStringOrRegex(attributeName);
                    if (isRegex(attributeName)) {
                        attributeNameMatch = new StringExpression(new SimpleNameExtractor(), val);
                    } else {
                        attributeNameMatch = new PatternExpression(new SimpleNameExtractor(), val);
                    }
                }

                expressionStack.push(new AttributeExpression(attributeNameMatch, attributeMatch, explicit));
            }

            @Override
            public void exitHasExpression_attribute_values(
                    ElementMatcherParser.HasExpression_attribute_valuesContext ctx) {
                String check;
                AbstractAttributeValueExpression match = null;
                TerminalNode numberNode;
                int hasCheckIndex = 1;
                switch (textAt(ctx, 0)) {
                    case "is":
                        check = textAt(ctx, 1);
                        if ("greater".equals(check) || "less".equals(check)) {
                            String numberString = textAt(ctx, 3);
                            try {
                                Number number = NumberFormat.getNumberInstance(Locale.US).parse(numberString);
                                match = new AttributeGreaterLessThanExpression(number, "less".equals(check));
                            } catch (ParseException e) {
                                parser.notifyErrorListeners(ctx.NUMBER().getSymbol(), "expected a number", null);
                                return;
                            }
                        } else {
                            match = getAttributeValueMatcher(ctx.hasExpression_attribute_values_subExpr());
                            if ("not".equals(check)) {
                                match = new NegatingAttributeValueExpression(match);
                            }
                        }
                        break;
                    case "doesn't":
                        hasCheckIndex = 2;
                        //intentional fallthrough
                    case "has":
                        check = textAt(ctx, hasCheckIndex);
                        switch (check) {
                            case "element":
                                if (!ctx.hasExpression_attribute_values().isEmpty()) {
                                    match = (AbstractAttributeValueExpression) expressionStack.pop();
                                }
                                numberNode = ctx.NUMBER();
                                try {
                                    Number number = numberNode == null
                                            ? null
                                            : NumberFormat.getNumberInstance(Locale.US).parse(numberNode.getText());
                                    match = new AttributeHasElementExpression(match,
                                            number == null ? null : number.intValue());
                                } catch (ParseException e) {
                                    parser.notifyErrorListeners(ctx.NUMBER().getSymbol(), "expected a number", null);
                                    return;
                                }
                                break;
                            default:
                                numberNode = ctx.NUMBER();
                                if (numberNode != null) {
                                    try {
                                        Number number = NumberFormat.getNumberInstance(Locale.US).parse(numberNode.getText());
                                        check = ctx.getChild(1).getText();
                                        Boolean operator;
                                        switch (check) {
                                            case "more":
                                                operator = false;
                                                break;
                                            case "less":
                                                operator = true;
                                                break;
                                            default:
                                                operator = null;
                                        }
                                        match = new AttributeElementCountExpression(number.intValue(), operator);
                                    } catch (ParseException e) {
                                        parser.notifyErrorListeners(ctx.NUMBER().getSymbol(), "expected a number", null);
                                        return;
                                    }
                                } else if (ctx.hasExpression_attribute() != null) {
                                    match = new AttributeChildMatchesExpression((AttributeExpression) expressionStack.pop());
                                }
                                break;
                        }

                        if (hasCheckIndex > 1) {
                            match = new NegatingAttributeValueExpression(match);
                        }
                        break;
                    case "(":
                        match = (AbstractAttributeValueExpression) expressionStack.pop();
                        break;
                    default:
                        LogicalOperator op = LogicalOperator.fromString(ctx.getChild(1).getText());
                        AbstractAttributeValueExpression right = (AbstractAttributeValueExpression) expressionStack.pop();
                        AbstractAttributeValueExpression left = (AbstractAttributeValueExpression) expressionStack.pop();

                        match = new AttributeValueLogicalExpression(left, op, right);
                        break;
                }

                if (match != null) {
                    expressionStack.push(match);
                }
            }

            @Override
            public void exitHasExpression_attribute_type(ElementMatcherParser.HasExpression_attribute_typeContext ctx) {
                boolean negate = "doesn't".equals(textAt(ctx, 0));
                String stringOrRegex = textAt(ctx, negate ? 3 : 2);

                MatchExpression expr;
                if (isRegex(stringOrRegex)) {
                    expr = new AttributeTypeEqualsExpression(Pattern.compile(extractStringOrRegex(stringOrRegex)));
                } else {
                    expr = new AttributeTypeEqualsExpression(extractStringOrRegex(stringOrRegex));
                }

                if (negate) {
                    expr = new NegatingExpression(expr);
                }

                expressionStack.push(expr);
            }

            @Override
            public void exitIsExpression(ElementMatcherParser.IsExpressionContext ctx) {
                boolean negate = false;
                if ("not".equals(textAt(ctx, 1))) {
                    negate = true;
                } else if ("isn't".equals(textAt(ctx, 0))) {
                    negate = true;
                }

                if (negate) {
                    expressionStack.push(new NegatingExpression(expressionStack.pop()));
                }
            }

            @Override
            public void exitIsExpression_kind(ElementMatcherParser.IsExpression_kindContext ctx) {
                String stringOrRegex = textAt(ctx, 1);
                if (isRegex(stringOrRegex)) {
                    expressionStack.push(
                            new PatternExpression(new ElementKindExtractor(), extractStringOrRegex(stringOrRegex)));
                } else {
                    expressionStack.push(
                            new StringExpression(new ElementKindExtractor(), extractStringOrRegex(stringOrRegex)));
                }
            }

            @Override
            public void exitIsExpression_package(ElementMatcherParser.IsExpression_packageContext ctx) {
                String stringOrRegex = textAt(ctx, 2);
                if (isRegex(stringOrRegex)) {
                    expressionStack.push(
                            new PatternExpression(new PackageExtractor(), extractStringOrRegex(stringOrRegex)));
                } else {
                    expressionStack.push(
                            new StringExpression(new PackageExtractor(), extractStringOrRegex(stringOrRegex)));
                }
            }

            @Override
            public void exitIsExpression_inherited(ElementMatcherParser.IsExpression_inheritedContext ctx) {
                // TODO implement
            }

            @Override
            public void exitIsExpression_subExpr(ElementMatcherParser.IsExpression_subExprContext ctx) {
                String token = textAt(ctx, 0);
                boolean immediate = "directly".equals(token) || "declared".equals(token) ||
                        "direct".equals(token);

                if (immediate) {
                    token = textAt(ctx, 1);
                }

                MatchExpression subExpr = convertNakedStringOrRegexUsing(expressionStack.pop(), RepresentationExtractor::new);
                MatchExpression expr = null;

                switch (token) {
                    case "argument":
                        Integer order = ctx.NUMBER() == null ? null : Integer.valueOf(ctx.NUMBER().getText());
                        expr = new IsArgumentOfExpression(subExpr, order);
                        break;
                    case "typeParameter":
                        expr = new IsTypeParameterOfExpression(subExpr);
                        break;
                    case "annotated":
                        // TODO implement
                        break;
                    case "method":
                        // TODO implement
                        break;
                    case "field":
                        // TODO implement
                        break;
                    case "outerClass":
                        // TODO implement
                        break;
                    case "innerClass":
                        // TODO implement
                        break;
                    case "thrown":
                        // TODO implement
                        break;
                    case "superType":
                        // TODO implement
                        break;
                    case "overridden":
                        // TODO implement
                        break;
                    case "in":
                        // TODO implement
                        break;
                    case "used":
                        // TODO implement
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected \"is\" expression: " + ctx.getText());
                }

                expressionStack.push(expr);
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

            private String textAt(RuleContext ctx, int childIndex) {
                return ctx.getChild(childIndex).getText();
            }

            private AbstractAttributeValueExpression getAttributeValueMatcher(
                    ElementMatcherParser.HasExpression_attribute_values_subExprContext ctx) {
                if (ctx.hasExpression_attribute_values_subExpr().isEmpty()) {
                    String val = ctx.getChild(0).getText();
                    if (isRegex(val)) {
                        return new AttributeValueEqualsExpression(Pattern.compile(extractStringOrRegex(val)));
                    } else if (isString(val)) {
                        return new AttributeValueEqualsExpression(extractStringOrRegex(val));
                    } else {
                        return new AttributeValueEqualsExpression(val);
                    }
                } else {
                    return new AttributeArrayValueEqualsExpression(ctx.hasExpression_attribute_values_subExpr().stream()
                            .map(this::getAttributeValueMatcher).collect(toList()));
                }
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

            private boolean isString(String stringOrRegex) {
                return stringOrRegex.charAt(0) == '\'';
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
        public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
            throw new IllegalStateException("Internal expression should never be evaluated. This is a bug.");
        }

        @Override
        public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
            throw new IllegalStateException("Internal expression should never be evaluated. This is a bug.");
        }

        @Override
        public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
            throw new IllegalStateException("Internal expression should never be evaluated. This is a bug.");
        }

        @Override
        public FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
            throw new IllegalStateException("Internal expression should never be evaluated. This is a bug.");
        }

        enum MatcherKind {
            STRING, REGEX
        }
    }

    private static ElementMatcherParser createNewParser(String recipe, ANTLRErrorListener errorListener) {
        ElementMatcherLexer lexer = new ElementMatcherLexer(CharStreams.fromString(recipe));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        ElementMatcherParser parser = new ElementMatcherParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        return parser;
    }
}
