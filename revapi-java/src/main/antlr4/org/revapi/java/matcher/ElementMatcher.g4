grammar ElementMatcher;

WS : [ \t\r\n]+ -> skip;
REGEX: '/' ~('/')+ '/';
STRING: '\'' ~('\'')* '\'';
NUMBER: ('0'..'9')+;

topExpression
    : parenthesizedExpression
    | expression ('and' | 'or' | 'that') expression
    | hasExpression
    | isExpression
    | throwsExpression
    | subTypeExpression
    | overridesExpression
    | returnExpression
    ;

expression
    : parenthesizedExpression
    | expression ('and' | 'or') expression
    | hasExpression
    | isExpression
    | throwsExpression
    | subTypeExpression
    | overridesExpression
    | returnExpression
    | stringExpression
    | regexExpression
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
    : 'throws' expression
    | 'doesn\'t' 'throw' expression
    ;

subTypeExpression
    : 'directly'? ('extends' | 'implements') expression
    | 'doesn\'t' 'directly'? ('extend' | 'implement') expression
    ;

overridesExpression
    : 'overrides' expression
    | 'doesn\'t' 'override' expression
    ;

returnExpression
    : 'returns' expression
    | 'doesn\'t' 'return' expression
    ;

hasExpression
    : 'has' (hasExpression_subExpr | hasExpression_match)
    | 'doesn\'t' 'have' (hasExpression_subExpr | hasExpression_match)
    | 'has' (('more' | 'less') 'than')? NUMBER 'arguments'
    | 'doesn\'t'? 'have' (('more' | 'less') 'than')? NUMBER 'arguments'
    | 'has' 'index' (('larger' | 'less') 'than')? NUMBER
    | 'doesn\'t'? 'have' 'index' (('larger' | 'less') 'than')? NUMBER
    ;

hasExpression_subExpr
    : ('argument' NUMBER?
        | 'typeParameter'
        | 'annotation'
        | 'method'
        | 'field'
        | 'outerclass'
        | 'innerclass'
        | 'direct'? 'superType'
        | 'type'
        )
        expression
    ;

hasExpression_match
    : ('name'
        | ('erased' | 'realized')? 'signature'
        | 'representation'
        | 'kind'
        | 'package'
      ) (STRING | REGEX)
    ;

isExpression
    : 'is' 'not'? ( isExpression_subExpr | isExpression_kind | isExpression_package )
    | 'isn\'t' ( isExpression_subExpr | isExpression_kind | isExpression_package )
    ;

isExpression_subExpr
    : ('argument' NUMBER? 'of'
        | 'typeParameter' 'of'
        | 'annotated' 'by'
        | 'method' 'of'
        | 'field' 'of'
        | 'outerclass' 'of'
        | 'innerclass' 'of'
        | 'thrown' 'from'
        | 'direct'? 'superType' 'of'
        | 'overriden' 'by'
        )
        expression
    ;

isExpression_kind
    : 'a' STRING | REGEX
    ;

isExpression_package
    : 'in' 'package' (STRING | REGEX)
    ;