grammar ElementMatcher;

WS : [ \t\r\n]+ -> skip;
REGEX: '/' ~('/')+ '/';
STRING: '\'' ~('\'')* '\'';
NUMBER: ('0'..'9')+;
LOGICAL_OPERATOR: 'and' | 'or';

topExpression
    : parenthesizedExpression
    | expression LOGICAL_OPERATOR expression
    | hasExpression
    | isExpression
    | throwsExpression
    | subTypeExpression
    | overridesExpression
    | returnsExpression
    | stringExpression
    | regexExpression
    ;

expression
    : parenthesizedExpression
    | expression LOGICAL_OPERATOR expression
    | hasExpression
    | isExpression
    | throwsExpression
    | subTypeExpression
    | overridesExpression
    | returnsExpression
    | stringExpression
    | regexExpression
    ;

subExpression
    : stringExpression
    | regexExpression
    | 'that'? parenthesizedExpression
    | 'that' expression
    ;

parenthesizedExpression
    : '(' expression ')'
    ;

stringExpression
    : STRING
    ;

regexExpression
    : REGEX
    ;

throwsExpression
    : 'throws' subExpression
    | 'doesn\'t' 'throw' subExpression?
    ;

subTypeExpression
    : 'directly'? ('extends' | 'implements') subExpression
    | 'doesn\'t' 'directly'? ('extend' | 'implement') subExpression
    ;

overridesExpression
    : 'overrides' subExpression
    | 'doesn\'t' 'override' subExpression?
    ;

returnsExpression
    : 'returns' 'precisely'? subExpression
    | 'doesn\'t' 'return' 'precisely'? subExpression
    ;

hasExpression
    : hasExpression_basic
    | hasExpression_arguments
    | hasExpression_index
    | hasExpression_typeParameters
    | hasExpression_typeParameterBounds
    ;

hasExpression_basic
    : 'has' (hasExpression_subExpr | hasExpression_match | hasExpression_attribute)
    | 'doesn\'t' 'have' (hasExpression_subExpr | hasExpression_match | hasExpression_attribute)
    ;

hasExpression_arguments
    : 'has' (('more' | 'less') 'than')? NUMBER 'arguments'
    | 'doesn\'t' 'have' (('more' | 'less') 'than')? NUMBER 'arguments'
    ;

hasExpression_index
    : 'has' 'index' (('larger' | 'less') 'than')? NUMBER
    | 'doesn\'t' 'have' 'index' (('larger' | 'less') 'than')? NUMBER
    ;

hasExpression_typeParameters
    : 'has' (('more' | 'less') 'than')? NUMBER 'typeParameters'
    | 'doesn\'t' 'have' (('more' | 'less') 'than')? NUMBER 'typeParameters'
    ;

hasExpression_typeParameterBounds
    : 'has' ('lower' | 'upper') 'bound' subExpression
    | 'doesn\'t' 'have' ('lower' | 'upper') 'bound' subExpression
    ;

hasExpression_attribute
    : 'explicit'? 'attribute' (STRING | REGEX)? ('that' (hasExpression_attribute_values | hasExpression_attribute_type))?
    ;

hasExpression_attribute_values
    : 'is' 'not'? 'equal' 'to' hasExpression_attribute_values_subExpr
    | 'is' ('greater' | 'less') 'than' NUMBER
    | 'has' (('more' | 'less') 'than')? NUMBER 'elements'
    | 'doesn\'t' 'have' (('more' | 'less') 'than')? NUMBER 'elements'
    | 'has' 'element' NUMBER?
    | 'has' 'element' NUMBER? 'that' hasExpression_attribute_values
    | 'doesn\'t' 'have' 'element' NUMBER?
    | 'doesn\'t' 'have' 'element' NUMBER? 'that' hasExpression_attribute_values
    | 'has' hasExpression_attribute
    | 'doesn\'t' 'have' hasExpression_attribute
    | '(' hasExpression_attribute_values ')'
    | hasExpression_attribute_values LOGICAL_OPERATOR hasExpression_attribute_values
    ;

hasExpression_attribute_type
    : 'has' 'type' (STRING | REGEX)
    | 'doesn\'t' 'have' 'type' (STRING | REGEX)
    ;

hasExpression_attribute_values_subExpr
    : STRING
    | REGEX
    | NUMBER
    | '{' hasExpression_attribute_values_subExpr (',' hasExpression_attribute_values_subExpr)* '}'
    ;

hasExpression_subExpr
    : ('argument' NUMBER?
        | 'typeParameter' NUMBER?
        | 'declared'? 'annotation'
        | 'declared'? 'method'
        | 'declared'? 'field'
        | 'declared'? 'innerClass'
        | 'direct'? 'outerClass'
        | 'direct'? 'superType'
        | 'type'
        )
        subExpression
    ;

hasExpression_match
    : ('name'
        | ('erased' | 'generic')? 'signature'
        | 'representation'
        | 'kind'
        | 'simpleName'
      ) (STRING | REGEX)
    ;

isExpression
    : 'is' 'not'? ( isExpression_subExpr | isExpression_kind | isExpression_package | isExpression_inherited)
    | 'isn\'t' ( isExpression_subExpr | isExpression_kind | isExpression_package | isExpression_inherited)
    ;

isExpression_subExpr
    : ('argument' NUMBER? 'of'
        | 'typeParameter' 'of'
        | 'directly'? 'annotated' 'by'
        | 'declared'? 'method' 'of'
        | 'declared'? 'field' 'of'
        | 'outerClass' 'of'
        | 'innerClass' 'of'
        | 'thrown' 'from'
        | 'direct'? 'superType' 'of'
        | 'overridden' 'by'
        | 'in' 'class'
        | 'directly'? 'used' 'by'
        )
        subExpression
    ;

isExpression_kind
    : 'a' STRING | REGEX
    ;

isExpression_package
    : 'in' 'package' (STRING | REGEX)
    ;

isExpression_inherited
    : 'inherited' ('from' subExpression)?
    ;
