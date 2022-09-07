module stf
{
    exports records.gui.dtf;
    exports records.gui.grid;
    exports records.gui.stable;
    
    requires static anns;
    requires static annsthreadchecker;
    requires data;
    requires xyz.columnal.utility.gui;
    requires parsers;
    requires xyz.columnal.utility;

    requires com.google.common;
    requires javafx.controls;
    requires javafx.graphics;
    //requires static org.checkerframework.checker;
    requires wellbehavedfx;
}