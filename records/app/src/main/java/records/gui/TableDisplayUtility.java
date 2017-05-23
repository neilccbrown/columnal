package records.gui;

import annotation.qual.Value;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.ReadOnlyStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;
import org.fxmisc.undo.UndoManagerFactory;
import records.data.Column;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberDisplayInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.Utility.RunOrError;
import utility.Workers;
import utility.gui.FXUtility;
import utility.gui.stable.StableView.ColumnHandler;
import utility.gui.stable.StableView.ValueReceiver;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Created by neil on 01/05/2017.
 */
public class TableDisplayUtility
{
    private static final String NUMBER_DOT = "\u00B7"; //"\u2022";
    private static final String ELLIPSIS = "\u2026";//"\u22EF";
    private static class DigitSizes
    {
        private static double LEFT_DIGIT_WIDTH;
        private static double RIGHT_DIGIT_WIDTH;
        private static double DOT_WIDTH;

        @OnThread(Tag.FXPlatform)
        public DigitSizes()
        {
            Text t = new Text();
            Group root = new Group(t);
            root.getStyleClass().add("number-display");
            Scene scene = new Scene(root);
            scene.getStylesheets().addAll(FXUtility.getSceneStylesheets());
            t.setText("0000000000");
            t.getStyleClass().setAll("number-display-int");
            t.applyCss();
            LEFT_DIGIT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
            t.setText("0000000000");
            t.getStyleClass().setAll("number-display-frac");
            t.applyCss();
            RIGHT_DIGIT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
            t.setText("...........");
            t.getStyleClass().setAll("number-display-dot");
            DOT_WIDTH = t.getLayoutBounds().getWidth() / 10.0;
        }
    }
    private static @MonotonicNonNull DigitSizes SIZES;

    // Maps a number of digits on the right side of the decimal place to the amount
    // of digits which there is then room for on the left side of the decimal place:
    @OnThread(Tag.FXPlatform)
    private static int rightToLeft(int right, double totalWidth)
    {
        if (SIZES == null)
            SIZES = new DigitSizes();

        double width = totalWidth - SIZES.DOT_WIDTH;
        width -= right * SIZES.RIGHT_DIGIT_WIDTH;
        return (int)Math.floor(width / SIZES.LEFT_DIGIT_WIDTH);
    }

    private static class ValidationResult
    {
        private final String newReplacement;
        private final @Nullable @Localized String error;
        private final RunOrError storer;

        private ValidationResult(String newReplacement, @Nullable @Localized String error, RunOrError storer)
        {
            this.newReplacement = newReplacement;
            this.error = error;
            this.storer = storer;
        }
    }

    private static interface StringInputValidator
    {
        /**
         *
         * @param rowIndex The row index of the item (mainly needed for storing it)
         * @param before The untouched part of the String before the altered part
         * @param oldPart The old value of the altered part of the String
         * @param newPart The new value of the altered part of the String
         * @param end The untouched part of the String after the altered part
         * @return The value of oldPart/newPart to use.  Return oldPart if you want no change.
         */
        @OnThread(Tag.FXPlatform)
        public ValidationResult validate(int rowIndex, String before, String oldPart, String newPart, String end); // Or should this work on change?
    }

    private static ValidationResult result(String newReplacement, @Nullable @Localized  String error, RunOrError storer)
    {
        return new ValidationResult(newReplacement, error, storer);
    }

    public static List<Pair<String, ColumnHandler>> makeStableViewColumns(RecordSet recordSet)
    {
        return Utility.mapList(recordSet.getColumns(), col -> {
            try
            {
                return getDisplay(col);
            }
            catch (InternalException | UserException e)
            {
                // Show a dummy column with an error message:
                return new Pair<>(col.getName().getRaw(), new ColumnHandler()
                {
                    @Override
                    public void fetchValue(int rowIndex, ValueReceiver receiver, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
                    {
                        receiver.setValue(rowIndex, new Label("Error: " + e.getLocalizedMessage()));
                    }

                    @Override
                    public void columnResized(double width)
                    {

                    }

                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable onFinish)
                    {
                        Utility.logStackTrace("Called edit when not editable");
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return false;
                    }
                });
            }
        });
    }

    private static Pair<String, ColumnHandler> getDisplay(@NonNull Column column) throws UserException, InternalException
    {
        return new Pair<>(column.getName().getRaw(), column.getType().applyGet(new DataTypeVisitorGet<ColumnHandler>()
        {
            @Override
            public ColumnHandler number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                class NumberDisplay
                {
                    private final @NonNull StyleClassedTextArea textArea;
                    private final String fullFracPart;
                    private final String fullIntegerPart;
                    private String displayFracPart;
                    private String displayIntegerPart;
                    private String displayDot;
                    private boolean displayDotVisible;

                    @OnThread(Tag.FXPlatform)
                    public NumberDisplay(int rowIndex, Number n)
                    {
                        StringInputValidator validator = getNumericValidator(column, g);
                        textArea = new StyleClassedTextArea(false) // plain undo manager
                        {
                            private String valueBeforeFocus = "";
                            private Utility.@Nullable RunOrError storeAction = null;

                            {
                                FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused ->
                                {
                                    if (focused)
                                    {
                                        valueBeforeFocus = getText();
                                    } else
                                    {
                                        if (storeAction != null)
                                        {
                                            Utility.@Initialized @NonNull RunOrError storeActionFinal = storeAction;
                                            Workers.onWorkerThread("Storing value " + getText(), Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(storeActionFinal));
                                        }
                                    }
                                });
                            }

                            @Override
                            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                            public void replaceText(int start, int end, String text)
                            {
                                String old = getText();
                                TableDisplayUtility.ValidationResult result = validator.validate(rowIndex, old.substring(0, start), old.substring(start, end), text, old.substring(end));
                                this.storeAction = result.storer;
                                super.replaceText(start, end, result.newReplacement);
                                //TODO sort out any restyling needed
                                // TODO show error
                            }
                        };
                        textArea.setEditable(column.isEditable());
                        textArea.setUseInitialStyleForInsertion(false);
                        textArea.setUndoManager(UndoManagerFactory.fixedSizeHistoryFactory(3));

                        @Nullable NumberDisplayInfo ndi = displayInfo.getDisplayInfo();
                        if (ndi == null)
                            ndi = NumberDisplayInfo.SYSTEMWIDE_DEFAULT; // TODO use file-wide default
                        fullIntegerPart = Utility.getIntegerPart(n).toString();
                        fullFracPart = Utility.getFracPartAsString(n, 0, -1);
                        displayIntegerPart = fullIntegerPart;
                        displayDot = NUMBER_DOT;
                        displayFracPart = fullFracPart;
                        updateDisplay();
                        textArea.getStyleClass().add("number-display");
                        StackPane.setAlignment(textArea, Pos.CENTER_RIGHT);
                        // Doesn't get mouse events unless focused:
                        textArea.mouseTransparentProperty().bind(textArea.focusedProperty().not());
                    }

                    @SuppressWarnings("initialization") // Due to use of various fields
                    private void updateDisplay(@UnknownInitialization(Object.class) NumberDisplay this)
                    {
                        List<String> dotStyle = new ArrayList<>();
                        dotStyle.add("number-display-dot");
                        if (!displayDotVisible)
                            dotStyle.add("number-display-dot-invisible");
                        textArea.replace(docFromSegments(
                            new StyledText<>(displayIntegerPart, Arrays.asList("number-display-int")),
                            new StyledText<>(displayDot, dotStyle),
                            new StyledText<>(displayFracPart, Arrays.asList("number-display-frac"))
                        ));
                    }
                }

                FXPlatformConsumer<DisplayCache<Number, NumberDisplay>.VisibleDetails> formatVisible = vis -> {
                    // Left length is number of digits to left of decimal place, right length is number of digits to right of decimal place
                    int maxLeftLength = vis.visibleCells.stream().mapToInt(d -> d == null ? 1 : d.fullIntegerPart.length()).max().orElse(1);
                    int maxRightLength = vis.visibleCells.stream().mapToInt(d -> d == null ? 0 : d.fullFracPart.length()).max().orElse(0);
                    double pixelWidth = vis.width - 8; // Allow some padding

                    // We truncate the right side if needed, to a minimum of minimumDP, at which point we truncate the left side
                    // to what remains
                    int minimumDP = displayInfo.getDisplayInfo() == null ? 0 : displayInfo.getDisplayInfo().getMinimumDP();
                    while (rightToLeft(maxRightLength, pixelWidth) < maxLeftLength && maxRightLength > minimumDP && maxRightLength > 1) // can be zero only if already zero
                    {
                        maxRightLength -= 1;
                    }
                    while (rightToLeft(maxRightLength, pixelWidth) < maxLeftLength && maxLeftLength > 1)
                    {
                        maxLeftLength -= 1;
                    }
                    // Still not enough room for everything?  Just set it all to ellipsis if so:
                    boolean onlyEllipsis = rightToLeft(maxRightLength, pixelWidth) < maxLeftLength;

                    for (NumberDisplay display : vis.visibleCells)
                    {
                        if (display != null)
                        {
                            display.textArea.setMaxWidth(vis.width);
                            if (onlyEllipsis)
                            {
                                display.displayIntegerPart = ELLIPSIS;
                                display.displayDot = "";
                                display.displayFracPart = "";
                            }
                            else
                            {
                                display.displayIntegerPart = display.fullIntegerPart;
                                display.displayFracPart = display.fullFracPart;
                                display.displayDot = NUMBER_DOT;

                                while (display.displayFracPart.length() < maxRightLength)
                                    display.displayFracPart += displayInfo.getDisplayInfo() == null ? " " : displayInfo.getDisplayInfo().getPaddingChar();

                                if (display.displayFracPart.length() > maxRightLength)
                                {
                                    display.displayFracPart = display.displayFracPart.substring(0, Math.max(0, maxRightLength - 1)) + ELLIPSIS;
                                }
                                if (display.displayIntegerPart.length() > maxLeftLength)
                                {
                                    display.displayIntegerPart = ELLIPSIS + display.displayIntegerPart.substring(display.displayIntegerPart.length() - maxLeftLength + 1);
                                }

                                display.displayDotVisible = !display.fullFracPart.isEmpty();

                                display.updateDisplay();
                            }
                        }
                    }
                };
                return new DisplayCache<@Value Number, NumberDisplay>(g, formatVisible, (Pair <Integer, @Value Number> p) -> new NumberDisplay(p.getFirst(), p.getSecond()), n -> n.textArea) {
                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable onFinish)
                    {
                        @Nullable NumberDisplay rowIfShowing = getRowIfShowing(rowIndex);
                        if (rowIfShowing != null)
                        {
                            @NonNull StyleClassedTextArea textArea = rowIfShowing.textArea;
                            if (scenePoint != null)
                            {
                                Point2D localPoint = textArea.sceneToLocal(scenePoint);
                                CharacterHit hit = textArea.hit(localPoint.getX(), localPoint.getY());
                                textArea.moveTo(hit.getInsertionIndex());
                            }
                            textArea.requestFocus();
                            FXUtility.onFocusLostOnce(textArea, onFinish);
                            //TODO run onFinish when focus lost again
                        }
                    }

                    @Override
                    public boolean isEditable()
                    {
                        return true;
                    }
                };
            }

            @Override
            public ColumnHandler text(GetValue<@Value String> g) throws InternalException, UserException
            {
                class StringDisplay extends StackPane
                {
                    private final Label label;

                    public StringDisplay(String value)
                    {
                        Label beginQuote = new Label("\u201C");
                        Label endQuote = new Label("\u201D");
                        beginQuote.getStyleClass().add("string-display-quote");
                        endQuote.getStyleClass().add("string-display-quote");
                        StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
                        StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
                        //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
                        //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
                        label = new Label(value);
                        label.setTextOverrun(OverrunStyle.CLIP);
                        getChildren().addAll(beginQuote, label); //endQuote, label);
                        // TODO allow editing, and call column.modified when it happens
                    }
                }

                return new DisplayCache<@Value String, StringDisplay>(g, null, (Pair<Integer, @Value String> p) -> new StringDisplay(p.getSecond()), s -> s) {
                    @Override
                    public void edit(int rowIndex, @Nullable Point2D scenePoint, FXPlatformRunnable onFinish)
                    {

                    }

                    @Override
                    public boolean isEditable()
                    {
                        return false;
                    }
                };
            }

            @Override
            public ColumnHandler bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }

            @Override
            public ColumnHandler array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                throw new UnimplementedException();
            }
        }));
    }

    @OnThread(Tag.Any)
    private static StringInputValidator getNumericValidator(Column column, GetValue<@Value Number> g)
    {

        return (rowIndex, before, oldPart, newPart, end) -> {
            String altered = newPart.replaceAll("[^0-9.+-]", "");
            // We also disallow + and - except at start, and only allow one dot:
            if (before.contains(".") || end.contains("."))
                altered = altered.replace(".", "");
            if (before.isEmpty())
            {
                // + or - would be allowed at the start
            }
            else
            {
                altered = altered.replace("[+-]","");
            }
            // Check it is actually valid as a number:
            @Nullable Number n;
            @Nullable @Localized String error = null;
            try
            {
                n = Utility.parseNumber(before + altered + end);
            }
            catch (UserException e)
            {
                error = e.getLocalizedMessage();
                n = null;
            }
            @Nullable Number nFinal = n;
            return result(altered, error, () -> {
                if (nFinal != null)
                {
                    g.set(rowIndex, DataTypeUtility.value(nFinal));
                    column.modified();
                }
            });
        };
    }
/*
    @OnThread(Tag.FXPlatform)
    private static Region getNode(DisplayValue item, @Nullable StringInputValidator validator)
    {
        if (item.getNumber() != null)
        {
            @NonNull Number n = item.getNumber();
            StyleClassedTextArea textArea = new StyleClassedTextArea(false) // plain undo manager
            {
                private String valueBeforeFocus = "";
                private @Nullable RunOrError storeAction = null;

                {
                    FXUtility.addChangeListenerPlatformNN(focusedProperty(), focused -> {
                        if (focused)
                        {
                            valueBeforeFocus = getText();
                        }
                        else
                        {
                            if (storeAction != null)
                            {
                                @Initialized @NonNull RunOrError storeActionFinal = storeAction;
                                Workers.onWorkerThread("Storing value " + getText(), Workers.Priority.SAVE_ENTRY, () -> Utility.alertOnError_(storeActionFinal));
                            }
                        }
                    });
                }

                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public void replaceText(int start, int end, String text)
                {
                    String old = getText();
                    if (validator != null)
                    {
                        @OnThread(Tag.FXPlatform) ValidationResult result = validator.validate(item.getRowIndex(), old.substring(0, start), old.substring(start, end), text, old.substring(end));
                        this.storeAction = result.storer;
                        super.replaceText(start, end, result.newReplacement);
                        //TODO sort out any restyling needed
                        // TODO show error
                    }

                }
            };
            textArea.setEditable(validator != null);
            textArea.setUseInitialStyleForInsertion(false);
            textArea.setUndoManager(UndoManagerFactory.fixedSizeHistoryFactory(3));

            @Nullable NumberDisplayInfo ndi = item.getNumberDisplayInfo();
            if (ndi == null)
                ndi = NumberDisplayInfo.SYSTEMWIDE_DEFAULT; // TODO use file-wide default
            String fracPart = Utility.getFracPartAsString(n, ndi.getMinimumDP(), ndi.getMaximumDP());
            fracPart = fracPart.isEmpty() ? "" : "." + fracPart;
            textArea.replace(docFromSegments(
                new StyledText<>(Utility.getIntegerPart(n).toString(), Arrays.asList("number-display-int")),
                new StyledText<>(fracPart, Arrays.asList("number-display-frac"))
            ));
            textArea.getStyleClass().add("number-display");
            return textArea;
        }
        else
        {
            StackPane stringWrapper = new StackPane();
            Label beginQuote = new Label("\u201C");
            Label endQuote = new Label("\u201D");
            beginQuote.getStyleClass().add("string-display-quote");
            endQuote.getStyleClass().add("string-display-quote");
            StackPane.setAlignment(beginQuote, Pos.TOP_LEFT);
            StackPane.setAlignment(endQuote, Pos.TOP_RIGHT);
            //StackPane.setMargin(beginQuote, new Insets(0, 0, 0, 3));
            //StackPane.setMargin(endQuote, new Insets(0, 3, 0, 0));
            Label label = new Label(item.toString());
            label.setTextOverrun(OverrunStyle.CLIP);
            stringWrapper.getChildren().addAll(beginQuote, label); //endQuote, label);
            return stringWrapper;
        }
    }
    */

    private static StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> docFromSegments(StyledText<Collection<String>>... segments)
    {
        ReadOnlyStyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> doc = ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment((StyledText<Collection<String>>)segments[0], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps());
        for (int i = 1; i < segments.length; i++)
        {
            doc = doc.concat(ReadOnlyStyledDocument.<Collection<String>, StyledText<Collection<String>>, Collection<String>>fromSegment(segments[i], Collections.emptyList(), Collections.emptyList(), StyledText.<Collection<String>>textOps()));
        }
        return doc;
    }
}
