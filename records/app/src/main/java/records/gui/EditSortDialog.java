package records.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.stage.Modality;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.SimpleCompletion;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.transformations.Sort.Direction;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.IdentifierUtility;
import utility.Pair;
import utility.UnitType;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.FancyList;
import utility.gui.LightDialog;
import utility.gui.TranslationUtility;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnThread(Tag.FXPlatform)
public class EditSortDialog extends ErrorableLightDialog<ImmutableList<Pair<ColumnId, Direction>>>
{
    private final @Nullable Table srcTable;
    private final @Nullable RecordSet dataWithColumns;
    private final SortList sortList;

    public EditSortDialog(View parent, @Nullable Point2D lastScreenPos, @Nullable Table srcTable, Table destTable, @Nullable ImmutableList<Pair<ColumnId, Direction>> originalSortBy)
    {
        super(parent, true);
        setResizable(true);
        initModality(Modality.NONE);
        this.srcTable = srcTable;
        @Nullable RecordSet d = null;
        try
        {
            d = srcTable == null ? destTable.getData() : srcTable.getData();
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
        }
        dataWithColumns = d;

        sortList = new SortList(originalSortBy == null ? ImmutableList.of() : Utility.mapListI(originalSortBy, p -> p.mapFirst(c -> c.getRaw())));
        sortList.getNode().setMinWidth(250.0);
        sortList.getNode().setMinHeight(150.0);
        sortList.getNode().setPrefWidth(300.0);
        sortList.getNode().setPrefHeight(250.0);
        getDialogPane().setContent(new BorderPane(sortList.getNode(), new Label("Choose the columns to sort by"), null, null, null));
        getDialogPane().getStylesheets().addAll(
            FXUtility.getStylesheet("general.css"),
            FXUtility.getStylesheet("dialogs.css")
        );
        getDialogPane().getStyleClass().add("sort-list-dialog");
        setOnShowing(e -> {
            //org.scenicview.ScenicView.show(getDialogPane().getScene());
            parent.enableColumnPickingMode(lastScreenPos, p -> Objects.equals(srcTable, p.getFirst()) || Objects.equals(destTable, p.getFirst()),t -> {
                sortList.pickColumnIfEditing(t);
            });
        });
        setOnHiding(e -> {
            parent.disablePickingMode();
        });
        
        if (originalSortBy == null)
        {
            // runAfter to avoid focus stealing:
            FXUtility.runAfter(() -> sortList.addToEnd(new Pair<>("", Direction.ASCENDING), true));
        }
    }

    @Override
    protected @OnThread(Tag.FXPlatform) Either<@Localized String, ImmutableList<Pair<ColumnId, Direction>>> calculateResult()
    {
        return Either.<@Localized String, Pair<ColumnId, Direction>, Pair<String, Direction>>mapM(sortList.getItems(), item -> {
            @ExpressionIdentifier String columnId = IdentifierUtility.asExpressionIdentifier(item.getFirst());
            if (columnId == null)
                return Either.<@Localized String, Pair<ColumnId, Direction>>left(TranslationUtility.getString("edit.column.invalid.column.name"));
            else
                return Either.<@Localized String, Pair<ColumnId, Direction>>right(item.<ColumnId>mapFirst(c -> new ColumnId(columnId)));
        });
    }

    @OnThread(Tag.FXPlatform)
    private class SortList extends FancyList<Pair<String, Direction>, SortPane>
    {
        public SortList(ImmutableList<Pair<String, Direction>> initialItems)
        {
            super(initialItems, true, true, () -> new Pair<>("", Direction.ASCENDING));
            getStyleClass().add("sort-list");
            listenForCellChange(c -> {
                updateButtonWidths();
                // When cell is added, not yet in scene, so run later in case it's the largest one:
                FXUtility.runAfterDelay(Duration.millis(100), () -> updateButtonWidths());
            });
        }

        @Override
        protected Pair<SortPane, FXPlatformSupplier<Pair<String, Direction>>> makeCellContent(@Nullable Pair<String, Direction> initialContent, boolean editImmediately)
        {
            SortPane sortPane = new SortPane(initialContent);
            if (editImmediately)
                FXUtility.onceNotNull(sortPane.sceneProperty(), s -> FXUtility.runAfter(sortPane.columnField::requestFocus));
            return new Pair<>(sortPane, sortPane::getCurrentValue);
        }

        public void pickColumnIfEditing(Pair<Table, ColumnId> t)
        {
            // This is a bit of a hack.  The problem is that clicking the column removes the focus
            // from the edit field, so we can't just ask if the edit field is focused.  Tracking who
            // the focus transfers from/to seems a bit awkward, so we just use a time-based system,
            // where if they were very recently (200ms) editing a field, fill in that field with the table.
            // If they weren't recently editing a field, we append to the end of the list.
            long curTime = System.currentTimeMillis();
            SortPane curEditing = streamCells()
                    .map(cell -> cell.getContent())
                    .filter(p -> p.lastEditTimeMillis() > curTime - 200L).findFirst().orElse(null);
            if (curEditing != null)
            {
                curEditing.setContent(t.getSecond());
                if (addButton != null)
                    addButton.requestFocus();
            }
            else
            {
                // Add to end:
                addToEnd(new Pair<>(t.getSecond().getRaw(), Direction.ASCENDING), false);
            }
        }

        public void updateButtonWidths(@UnknownInitialization(FancyList.class) SortList this)
        {
            ImmutableList<SortPane.DirectionButton> buttons = streamCells().map(c -> c.getContent().button).collect(ImmutableList.<SortPane.DirectionButton>toImmutableList());
            if (buttons.isEmpty())
                return;
            // Find the largest preferred width:
            double largestPrefWidth = buttons.stream().mapToDouble(b -> b.prefWidth(-1)).max().orElse(30.0);
            for (SortPane.DirectionButton button : buttons)
            {
                // Must set min, not pref, otherwise it screws up our calculation above:
                button.setMinWidth(largestPrefWidth);
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    private class SortPane extends BorderPane
    {
        private final TextField columnField;
        private final AutoComplete autoComplete;
        private final DirectionButton button;
        private long lastEditTimeMillis = -1;

        public SortPane(@Nullable Pair<String, Direction> initialContent)
        {
            columnField = new TextField(initialContent == null ? "" : initialContent.getFirst());
            BorderPane.setMargin(columnField, new Insets(0, 2, 2, 5));
            autoComplete = new AutoComplete<ColumnCompletion>(columnField,
                (s, p, q) -> Utility.streamNullable(srcTable).flatMap(t -> {
                    try
                    {
                        return t.getData().getColumns().stream();
                    }
                    catch (UserException | InternalException e)
                    {
                        Log.log(e);
                        return Stream.empty();
                    }
                }).filter(c -> c.getName().getOutput().contains(s)).map(ColumnCompletion::new),
                getListener(), () -> true, WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
            FXUtility.addChangeListenerPlatformNN(columnField.focusedProperty(), focus -> {
                // Update whether focus is arriving or leaving:
                lastEditTimeMillis = System.currentTimeMillis();
            });
            Label label = new Label("Type column name or click on column");
            label.visibleProperty().bind(columnField.focusedProperty());
            setTop(label);
            setCenter(columnField);
            button = new DirectionButton();
            button.setDirection(initialContent == null ? Direction.ASCENDING : initialContent.getSecond());
            FXUtility.addChangeListenerPlatformNNAndCallNow(columnField.textProperty(), c -> {
                button.setType(calculateTypeOf(c));
            });
            setRight(button);
            BorderPane.setMargin(button, new Insets(0, 4, 0, 4));
            getStyleClass().add("sort-pane");
            
        }

        public long lastEditTimeMillis()
        {
            return columnField.isFocused() ? System.currentTimeMillis() : lastEditTimeMillis;
        }

        private CompletionListener<ColumnCompletion> getListener(@UnknownInitialization SortPane this)
        {
            return new CompletionListener<ColumnCompletion>()
            {
                @Override
                public String doubleClick(String currentText, ColumnCompletion selectedItem)
                {
                    // TODO update the sort button
                    return selectedItem.c.getName().getOutput();
                }

                @Override
                public String nonAlphabetCharacter(String textBefore, @Nullable ColumnCompletion selectedItem, String textAfter, OptionalInt positionCaret)
                {
                    return textBefore + textAfter; // Shouldn't happen as not using alphabets
                }

                @Override
                public String keyboardSelect(String textBefore, String textAfter, ColumnCompletion selectedItem)
                {
                    return doubleClick(textBefore + textAfter, selectedItem);
                }

                @Override
                public String exactCompletion(String currentText, ColumnCompletion selectedItem)
                {
                    return doubleClick(currentText, selectedItem);
                }

                @Override
                public String focusLeaving(String currentText, @Nullable ColumnCompletion selectedItem)
                {
                    if (selectedItem != null)
                        return doubleClick(currentText, selectedItem);
                    else
                        return currentText;
                }

                @Override
                public void tabPressed()
                {
                    // TODO focus next item or add button
                }
            };
        }

        public void setContent(ColumnId columnId)
        {
            autoComplete.setContentDirect(columnId.getRaw(), true);
        }

        public Pair<String, Direction> getCurrentValue()
        {
            return new Pair<>(columnField.getText(), button.direction);
        }

        @OnThread(Tag.FXPlatform)
        private class DirectionButton extends Button
        {
            private final Label topLabel;
            private final Label bottomLabel;
            private final Shape icon;
            private String smallItem = "A";
            private String largeItem = "Z";
            private Direction direction = Direction.ASCENDING;

            public DirectionButton()
            {
                icon = new Polygon(4, 0, 8, 25, 0, 25);
                icon.getStyleClass().add("sort-direction-icon");
                BorderPane.setMargin(icon, new Insets(0, 1, 0, 3));
                BorderPane sortGraphic = new BorderPane();
                topLabel = new Label(smallItem);
                bottomLabel = new Label(largeItem);
                sortGraphic.setCenter(new BorderPane(null, topLabel, null, bottomLabel, null));
                sortGraphic.setRight(icon);
                setText("");
                setGraphic(sortGraphic);
                setOnAction(e -> {
                    FXUtility.mouse(this).setDirection(direction == Direction.ASCENDING ? Direction.DESCENDING : Direction.ASCENDING);
                });
                getStyleClass().add("sort-direction-button");
            }

            public void setDirection(Direction direction)
            {
                this.direction = direction;
                icon.setRotate(direction == Direction.ASCENDING ? 0 : 180);
                topLabel.setText(direction == Direction.ASCENDING ? smallItem : largeItem);
                bottomLabel.setText(direction == Direction.ASCENDING ? largeItem : smallItem);
            }
            
            public void setType(@Nullable DataType dataType)
            {
                if (dataType == null)
                    dataType = DataType.TEXT;
                try
                {
                    dataType.apply(new DataTypeVisitor<UnitType>()
                    {
                        @Override
                        public UnitType number(NumberInfo numberInfo) throws InternalException, UserException
                        {
                            smallItem = "1";
                            largeItem = "99";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType text() throws InternalException, UserException
                        {
                            smallItem = "A";
                            largeItem = "Z";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                        {
                            switch (dateTimeInfo.getType())
                            {
                                case YEARMONTHDAY:
                                case YEARMONTH:
                                case DATETIME:
                                case DATETIMEZONED:
                                    smallItem = "1965";
                                    largeItem = "2016";
                                    break;
                                case TIMEOFDAY:
                                //case TIMEOFDAYZONED:
                                    smallItem = "00:00";
                                    largeItem = "11:21";
                                    break;
                                
                            }
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType bool() throws InternalException, UserException
                        {
                            smallItem = "false";
                            largeItem = "true";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                        {
                            if (tags.size() == 1 && tags.get(0).getInner() != null)
                            {
                                // Recurse first:
                                tags.get(0).getInner().apply(this);
                                // Then wrap:
                                smallItem = tags.get(0).getName() + " (" + smallItem + ")";
                                largeItem = tags.get(0).getName() + " (" + largeItem + ")";
                            }
                            else
                            {
                                // Just show tag names:
                                smallItem = tags.get(0).getName() + " \u2026";
                                largeItem = tags.get(tags.size() - 1).getName() + " \u2026";
                            }
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType tuple(ImmutableList<DataType> inner) throws InternalException, UserException
                        {
                            // Recurse first:
                            inner.get(0).apply(this);
                            // Then wrap:
                            smallItem = "(" + smallItem + ", \u2026)";
                            largeItem = "(" + largeItem + ", \u2026)";
                            return UnitType.UNIT;
                        }

                        @Override
                        public UnitType array(@Nullable DataType inner) throws InternalException, UserException
                        {
                            if (inner == null)
                            {
                                smallItem = "[]";
                                largeItem = "[]";
                            }
                            else
                            {
                                // Recurse first:
                                inner.apply(this);
                                // Then wrap:
                                smallItem = "[" + smallItem + ", \u2026]";
                                largeItem = "[" + largeItem + ", \u2026]";
                            }
                            return UnitType.UNIT;
                        }
                    });
                }
                catch (UserException | InternalException e)
                {
                    Log.log(e);
                }
                // Will update labels:
                setDirection(direction);
                if (sortList != null)
                    sortList.updateButtonWidths();
            }
        }
    }

    private @Nullable DataType calculateTypeOf(@Nullable String columnId)
    {
        if (columnId == null || dataWithColumns == null)
            return null;
        @ExpressionIdentifier String s = IdentifierUtility.asExpressionIdentifier(columnId);
        if (s == null)
            return null;
        @Nullable Column c = dataWithColumns.getColumnOrNull(new ColumnId(s));
        if (c == null)
            return null;
        try
        {
            return c.getType().getType();
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
            return null;
        }
    }

    private static class ColumnCompletion extends SimpleCompletion
    {
        private final Column c;

        private ColumnCompletion(Column c)
        {
            super(c.getName().getRaw(), null);
            this.c = c;
        }
    }
}
