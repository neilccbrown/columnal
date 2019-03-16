package records.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.table.TableDisplay;
import records.transformations.ManualEdit;
import records.transformations.ManualEdit.ColumnReplacementValues;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.Workers.Priority;
import utility.gui.DimmableParent;
import utility.gui.FancyList;
import utility.gui.LightDialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

// If the document is closed by clicking a hyperlink, that
// location is returned as a pair of the identifier and
// the replaced column.  If Close is clicked instead then
// empty is returned
@OnThread(Tag.FXPlatform)
public class ManualEditEntriesDialog extends LightDialog<Pair<Optional<Pair<ComparableValue, ColumnId>>, SimulationSupplier<ImmutableMap<ColumnId, ColumnReplacementValues>>>>
{
    public ManualEditEntriesDialog(DimmableParent parent, @Nullable ColumnId keyColumn, ExFunction<ColumnId, DataType> lookupColumnType, ImmutableList<Entry> originalEntries)
    {
        super(parent);
        FancyList<Entry, HBox> fancyList = new FancyList<Entry, HBox>(originalEntries, true, false, null)
        {
            @Override
            protected @OnThread(Tag.FXPlatform) Pair<HBox, FXPlatformSupplier<Entry>> makeCellContent(Entry initialContent, boolean editImmediately)
            {
                HBox content = new HBox(new Label("Loading..."));
                Workers.onWorkerThread("Loading replacement values", Priority.FETCH, () -> {
                    try
                    {
                        String keyValue = DataTypeUtility.valueToString(keyColumn == null ? DataType.NUMBER /* row number */ : lookupColumnType.apply(keyColumn), initialContent.identifierValue.getValue(), null);
                        String replacementValue = initialContent.replacementValue.eitherEx(err -> err, v -> DataTypeUtility.valueToString(lookupColumnType.apply(initialContent.replacementColumn), v.getValue(), null));
                        FXPlatformRunnable jumpTo = () -> {
                            ImmutableList<Entry> items = getItems();
                            ManualEditEntriesDialog.this.setResult(new Pair<>(Optional.of(new Pair<>(initialContent.identifierValue, initialContent.getReplacementColumn())), () -> fromEntries(items, lookupColumnType)));
                            ManualEditEntriesDialog.this.close();
                        };
                        
                        Platform.runLater(() ->
                            content.getChildren().setAll(new HBox(
                                hyperLink(new Label(keyValue), jumpTo),
                                hyperLink(new Label(initialContent.replacementColumn.getRaw()), jumpTo),
                                hyperLink(new Label(replacementValue), jumpTo)
                        )));
                    }
                    catch (UserException | InternalException e)
                    {
                        if (e instanceof InternalException)
                            Log.log(e);
                        Platform.runLater(() -> content.getChildren().setAll(new Label("Error: " + e.getLocalizedMessage())));
                    }
                });
                
                return new Pair<>(content, () -> initialContent);
            }
        };
        
        getDialogPane().setContent(fancyList.getNode());
        getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        getDialogPane().lookupButton(ButtonType.CLOSE).getStyleClass().add("close-button");
    
        setResultConverter(bt -> {
            ImmutableList<Entry> items = fancyList.getItems();
            return new Pair<>(Optional.empty(), () -> fromEntries(items, lookupColumnType));
        });
    }

    private static Label hyperLink(Label label, FXPlatformRunnable jumpTo)
    {
        label.getStyleClass().add("jump-to-link");
        label.setOnMouseClicked(e -> {
            jumpTo.run();
        });
        return label;
    }

    @OnThread(Tag.Simulation)
    public static ImmutableList<Entry> getEntries(ManualEdit manualEdit)
    {
        return manualEdit.getReplacements().entrySet().stream().flatMap(e -> e.getValue().streamAll().map(r -> new Entry(r.getFirst(), e.getKey(), r.getSecond())
        )).collect(ImmutableList.<Entry>toImmutableList());
    }
    
    @OnThread(Tag.Simulation)
    private static ImmutableMap<ColumnId, ColumnReplacementValues> fromEntries(ImmutableList<Entry> entries, ExFunction<ColumnId, DataType> lookupColumnType) throws InternalException, UserException
    {
        HashMap<ColumnId, TreeMap<ComparableValue, Either<String, ComparableValue>>> items = new HashMap<>();

        for (Entry entry : entries)
        {
            items.computeIfAbsent(entry.getReplacementColumn(), c -> new TreeMap<>()).put(entry.getIdentifierValue(), entry.getReplacementValue());
        }
        
        ImmutableMap.Builder<ColumnId, ColumnReplacementValues> r = ImmutableMap.builder();

        for (Map.Entry<ColumnId, TreeMap<ComparableValue, Either<String, ComparableValue>>> entry : items.entrySet())
        {
            ColumnId c = entry.getKey();
            TreeMap<ComparableValue, Either<String, ComparableValue>> m = entry.getValue();
            r.put(c, new ColumnReplacementValues(lookupColumnType.apply(c), m.entrySet().stream().<Pair<@Value Object, Either<String, @Value Object>>>map(e -> new Pair<@Value Object, Either<String, @Value Object>>(e.getKey().getValue(), e.getValue().<@Value Object>map(v -> v.getValue()))).collect(ImmutableList.<Pair<@Value Object, Either<String, @Value Object>>>toImmutableList())));
        }

        return r.build();
    }

    public static class Entry
    {
        private final ComparableValue identifierValue;
        private final ColumnId replacementColumn;
        private final ComparableEither<String, ComparableValue> replacementValue;

        public Entry(ComparableValue identifierValue, ColumnId replacementColumn, ComparableEither<String, ComparableValue> replacementValue)
        {
            this.identifierValue = identifierValue;
            this.replacementColumn = replacementColumn;
            this.replacementValue = replacementValue;
        }

        public ComparableValue getIdentifierValue()
        {
            return identifierValue;
        }

        public ColumnId getReplacementColumn()
        {
            return replacementColumn;
        }

        public ComparableEither<String, ComparableValue> getReplacementValue()
        {
            return replacementValue;
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry entry = (Entry) o;
            return identifierValue.equals(entry.identifierValue) &&
                    replacementColumn.equals(entry.replacementColumn) &&
                    replacementValue.equals(entry.replacementValue);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(identifierValue, replacementColumn, replacementValue);
        }
    }
}
