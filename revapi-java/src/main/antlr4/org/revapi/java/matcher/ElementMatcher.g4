grammar ElementMatcher;

DIGIT: '0'..'9';
WS : [ \t\r\n]+ -> skip;
REGEX: '/' ~('/')+ '/';
STRING: '\'' ~('\'')* '\'';
NUMBER: DIGIT+;
GENERIC_BINARY_OPERATOR: '=' | '!=' ;
NUMERIC_BINARY_OPERATOR: '>' | '<' | '>=' | '<=';
UNARY_OPERATOR
    : 'contains'
    | 'isContainedIn'
    | 'implements'
    | 'isImplementedBy'
    | 'extends'
    | 'super'
    | 'annotates'
    | 'isAnnotatedBy'
    | 'overrides'
    | 'isOverriddenBy'
    | 'hasType'
    | 'hasReturnType'
    | 'throws'
    | 'isThrownFrom'
    | 'isParameterizedBy'
    | 'parameterizes'
    | 'isArgumentOf'
    ;

WHERE_QUALIFIER
    : 'argument'
    | 'typeParameter'
    | 'annotation'
    ;

ATTACHMENT
    : 'kind'
    | 'package'
    | 'name'
    | 'class'
    | 'signature'
    | 'erasedSignature'
    | 'signatureWithRealizedTypeVariables'
    | 'representation'
    | 'index'
    | 'returnType'
    | 'index'
    ;

expression
    : binaryExpression
    | unaryExpression
    | parenthesizedExpression
    | expression ('&' | '|') expression
    | notExpression
    | stringExpression
    | regexExpression
    | whereExpression
    ;

binaryExpression
    : ATTACHMENT GENERIC_BINARY_OPERATOR ( REGEX | STRING | NUMBER )
    | ATTACHMENT NUMERIC_BINARY_OPERATOR NUMBER
    ;

unaryExpression
    : UNARY_OPERATOR expression
    ;

parenthesizedExpression
    : '(' expression ')'
    ;

notExpression
    : '!' expression
    ;

stringExpression
    : STRING
    ;

regexExpression
    : REGEX
    ;

whereExpression
    : 'has' (
        ('argument' ('[' NUMBER ']')?)
        | 'typeParameter'
        | 'annotation'
        | 'method'
        | 'field'
        | 'class'
        )
        expression
    ;
