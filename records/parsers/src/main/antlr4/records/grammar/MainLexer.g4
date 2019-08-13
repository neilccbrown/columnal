lexer grammar MainLexer;

import BasicLexer;

DATA : 'DATA';
TEXTFILE : 'TEXTFILE';
LINKED : 'LINKED';
END : '@END';
BEGIN : '@BEGIN' ' '* ([A-Z][A-Z]+)? '\r'? '\n' -> pushMode(DETAIL);
TRANSFORMATION: 'TRANSFORMATION';
SOURCE: 'SOURCE';
FORMAT : 'FORMAT';
VALUES : 'VALUES';
SKIPROWS : 'SKIP';
TYPES : 'TYPES';
VERSION : 'VERSION';
UNITS : 'UNITS';
DISPLAY : 'DISPLAY';
SOFTWARE : 'COLUMNAL';
COMMENT : 'COMMENT';
CONTENT : 'CONTENT';



mode DETAIL;
DETAIL_END: '@END' -> popMode;
DETAIL_LINE: (~[\n\r])* '\r'? '\n' {!getText().startsWith("@END")}?;
