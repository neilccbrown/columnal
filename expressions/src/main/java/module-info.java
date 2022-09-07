module expressions
{
    exports xyz.columnal.transformations.expression;
    exports xyz.columnal.transformations.expression.explanation;
    exports xyz.columnal.transformations.expression.function;
    exports xyz.columnal.transformations.expression.type;
    exports xyz.columnal.transformations.expression.visitor;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires parsers;
    requires xyz.columnal.utility;

    //requires common;
    requires com.google.common;
    requires javafx.graphics;
    //requires static org.checkerframework.checker;
}