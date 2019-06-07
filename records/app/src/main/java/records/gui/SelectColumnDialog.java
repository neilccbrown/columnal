package records.gui;

import annotation.help.qual.HelpKey;
import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.Table;
import records.error.InternalException;
import records.error.UserException;
import records.gui.AutoComplete.CompletionListener;
import records.gui.AutoComplete.SimpleCompletion;
import records.gui.AutoComplete.WhitespacePolicy;
import records.gui.lexeditor.ExpressionEditor.ColumnPicker;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.TranslationUtility;
import utility.Utility;
import utility.gui.ErrorableLightDialog;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.Instruction;
import utility.gui.LabelledGrid;
import utility.gui.LabelledGrid.Row;

import java.util.function.Predicate;
import java.util.stream.Stream;

// The length of the list of columns returned will be identical to the length of the list of SelectInfo
// passed to the constructor
@OnThread(Tag.FXPlatform)
public class SelectColumnDialog extends ErrorableLightDialog<ImmutableList<ColumnId>>
{
    private final ImmutableList<TextField> columnFields;
    private final ImmutableList<SelectInfo> selectors;
    private @Nullable TextField mostRecentlyFocusedField;

    public SelectColumnDialog(Window parent, @Nullable Table srcTable, ColumnPicker columnPicker, ImmutableList<SelectInfo> selectors)
    {
        super(d -> parent, true);
        initOwner(parent);
        initModality(Modality.NONE);
        this.selectors = selectors;
        
        ImmutableList<Pair<Row, TextField>> items = Utility.mapList_Index(selectors, (index, selectInfo) -> {
            TextField columnField = new TextField(); 
            BorderPane.setMargin(columnField, new Insets(0, 2, 2, 5));
            new AutoComplete<ColumnCompletion>(columnField,
                s -> Utility.streamNullable(srcTable).flatMap(t -> {
                    try
                    {
                        return t.getData().getColumns().stream();
                    } catch (UserException | InternalException e)
                    {
                        Log.log(e);
                        return Stream.empty();
                    }
                }).filter(c -> selectInfo.filterColumn.test(c) && c.getName().getOutput().contains(s)).map(ColumnCompletion::new),
                getListener(index), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM);
            FXUtility.addChangeListenerPlatformNN(columnField.focusedProperty(), focus -> {
                if (focus)
                    mostRecentlyFocusedField = columnField;
            });
            Instruction instruction = new Instruction("pick.column.instruction");
            instruction.showAboveWhenFocused(columnField);
            
            final Row row;
            if (selectInfo.helpKey != null)
                row = GUI.labelledGridRow(selectInfo.labelKey, selectInfo.helpKey, columnField);
            else
                row = new Row(GUI.label(selectInfo.labelKey), null, columnField);            
            return new Pair<>(row, columnField);
        });
        columnFields = Utility.mapListI(items, p -> p.getSecond());
        getDialogPane().setContent(new LabelledGrid(items.stream().map(p -> p.getFirst()).toArray(Row[]::new)));
        setOnShown(e -> {
            columnPicker.enableColumnPickingMode(null, p -> p.getFirst() == srcTable, p -> {
                if (mostRecentlyFocusedField != null)
                {
                    mostRecentlyFocusedField.setText(p.getSecond().getRaw());
                    int index = FXUtility.mouse(this).columnFields.indexOf(mostRecentlyFocusedField);
                    if (index >= 0)
                        FXUtility.mouse(this).nameSetForIndex(p.getSecond().getRaw(), index);
                }
            });
        });
        setOnHidden(e -> {
            columnPicker.disablePickingMode();
        });
        if (!columnFields.isEmpty())
        {
            FXUtility.onceNotNull(columnFields.get(0).sceneProperty(), sc -> {
                FXUtility.runAfter(() -> columnFields.get(0).requestFocus());
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    @Override
    protected Either<@Localized String, ImmutableList<ColumnId>> calculateResult()
    {
        ImmutableList.Builder<ColumnId> results = ImmutableList.builderWithExpectedSize(columnFields.size());
        for (TextField columnField : columnFields)
        {
            @Nullable @ExpressionIdentifier String ident = IdentifierUtility.asExpressionIdentifier(columnField.getText().trim());
            if (ident != null)
                results.add(new ColumnId(ident));
            else
                return Either.left(TranslationUtility.getString("edit.column.invalid.column.name"));
        }
        return Either.right(results.build());
    }

    private CompletionListener<ColumnCompletion> getListener(@UnknownInitialization SelectColumnDialog this, int index)
    {
        return new CompletionListener<ColumnCompletion>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public String doubleClick(String currentText, ColumnCompletion selectedItem)
            {
                String name = selectedItem.c.getName().getOutput();
                Utility.later(SelectColumnDialog.this).nameSetForIndex(name, index);
                return name;
            }

            @Override
            @OnThread(Tag.FXPlatform)
            public @Nullable String keyboardSelect(String textBefore, String textAfter, @Nullable ColumnCompletion selectedItem, boolean tabPressed)
            {
                if (selectedItem != null)
                    return doubleClick("", selectedItem);
                else
                    return null;
            }
        };
    }

    protected void nameSetForIndex(String name, int index)
    {
        if (index + 1 < selectors.size())
        {
            if (selectors.get(index + 1).copyFromPrevious)
                columnFields.get(index + 1).setText(name);
            // Focus next field for entry:
            FXUtility.runAfter(() -> {
                // TODO this doesn't work right if column clicked, because the window isn't focused
                columnFields.get(index + 1).requestFocus();
                columnFields.get(index + 1).selectAll();
            });
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
    
    public static class SelectInfo
    {
        private final @LocalizableKey String labelKey;
        private final @Nullable @HelpKey String helpKey;
        private final Predicate<Column> filterColumn;
        // Once the previous item is set, should it be copied to this if this is blank at the time?
        private final boolean copyFromPrevious;

        public SelectInfo(@LocalizableKey String labelKey, @Nullable @HelpKey String helpKey, Predicate<Column> filterColumn, boolean copyFromPrevious)
        {
            this.labelKey = labelKey;
            this.helpKey = helpKey;
            this.filterColumn = filterColumn;
            this.copyFromPrevious = copyFromPrevious;
        }

        public SelectInfo(@LocalizableKey String labelKey, Predicate<Column> filterColumn)
        {
            this(labelKey, null, filterColumn, false);
        }
    }
}