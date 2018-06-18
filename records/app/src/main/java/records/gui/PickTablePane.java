package records.gui;

import com.google.common.collect.ImmutableSet;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.Table;
import records.data.TableId;
import records.gui.expressioneditor.AutoComplete;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.CompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.stream.Collectors;

@OnThread(Tag.FXPlatform)
public class PickTablePane extends BorderPane
{
    private final TextField tableField = new TextField();
    private final AutoComplete autoComplete;
    private final FXPlatformConsumer<Table> setResultAndClose;
    private long lastEditTimeMillis = -1;
    private final Label label;

    public PickTablePane(View view, ImmutableSet<Table> exclude, TableId initial, FXPlatformConsumer<Table> setResultAndFinishEditing)
    {
        this.setResultAndClose = setResultAndFinishEditing;
        tableField.setText(initial.getRaw());
        autoComplete = new AutoComplete<TableCompletion>(tableField,
            (s, q) -> view.getManager().getAllTables().stream().filter(t -> !exclude.contains(t) && t.getId().getOutput().contains(s)).map(TableCompletion::new).collect(Collectors.toList()),
            getListener(), WhitespacePolicy.ALLOW_ONE_ANYWHERE_TRIM, (cur, next) -> false);
        
        setCenter(tableField);
        label = new Label("Click on a table or type table name");
        setTop(label);
        setMargin(label, new Insets(2));
        setMargin(tableField, new Insets(0, 4, 4, 4));
        
        FXUtility.addChangeListenerPlatformNN(tableField.focusedProperty(), focus -> {
            // Update whether focus is arriving or leaving:
            lastEditTimeMillis = System.currentTimeMillis();
        });
        getStyleClass().add("pick-table-pane");
    }
    
    public void showLabelOnlyWhenFocused()
    {
        label.visibleProperty().bind(tableField.focusedProperty());
    }

    public void focusEntryField()
    {
        tableField.requestFocus();
    }

    @RequiresNonNull("setResultAndClose")
    private CompletionListener<TableCompletion> getListener(@UnknownInitialization(BorderPane.class) PickTablePane this)
    {
        @NonNull FXPlatformConsumer<Table> setResultAndCloseFinal = setResultAndClose;
        return new CompletionListener<TableCompletion>()
        {
            @Override
            public String doubleClick(String currentText, TableCompletion selectedItem)
            {
                setResultAndCloseFinal.consume(selectedItem.t);
                return ((TableCompletion) selectedItem).t.getId().getOutput();
            }

            @Override
            public String nonAlphabetCharacter(String textBefore, @Nullable TableCompletion selectedItem, String textAfter)
            {
                return textBefore + textAfter; // Shouldn't happen as not using alphabets
            }

            @Override
            public String keyboardSelect(String currentText, TableCompletion selectedItem)
            {
                return doubleClick(currentText, selectedItem);
            }

            @Override
            public @Nullable String exactCompletion(String currentText, TableCompletion selectedItem)
            {
                // Don't close dialog just because they typed the exact name:
                return null;
            }

            @Override
            public String focusLeaving(String currentText, @Nullable TableCompletion selectedItem)
            {
                if (selectedItem != null)
                    return doubleClick(currentText, selectedItem);
                else
                    return currentText;
            }

            @Override
            public void tabPressed()
            {
                // TODO focus Ok button or equivalent
            }
        };
    }

    public void setContent(@Nullable Table table)
    {
        autoComplete.setContentDirect(table == null ? "" : table.getId().getRaw());
        if (table != null)
            setResultAndClose.consume(table);
    }

    public BooleanExpression currentlyEditing()
    {
        return tableField.focusedProperty();
    }
    
    public long lastEditTimeMillis()
    {
        return tableField.isFocused() ? System.currentTimeMillis() : lastEditTimeMillis;
    }

    private static class TableCompletion extends Completion
    {
        private final Table t;

        public TableCompletion(Table t)
        {
            this.t = t;
        }

        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(t.getId().getOutput(), null);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return true;
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            boolean match = input.equals(t.getId().getOutput());
            if (match && onlyAvailableCompletion)
                return CompletionAction.COMPLETE_IMMEDIATELY;
            else if (match || (onlyAvailableCompletion && !input.isEmpty() && t.getId().getOutput().startsWith(input)))
                return CompletionAction.SELECT;
            else
                return CompletionAction.NONE;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            // I don't believe this will end up being called anyway as we don't use alphabets:
            return Utility.containsCodepoint(t.getId().getOutput(), character);
        }
    }
}
