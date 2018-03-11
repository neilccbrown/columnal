package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Shape;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.CellPosition;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table.InitialLoadDetails;
import records.data.TableAndColumnRenames;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableManager.TableManagerListener;
import records.data.TableOperations;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataOrTransformChoice.DataOrTransform;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridLineSupplier;
import records.gui.grid.VirtualGridSupplierFloating;
import records.transformations.Transform;
import records.transformations.TransformationEditable;
import records.transformations.TransformationInfo;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class View extends StackPane
{
    private static final double DEFAULT_SPACE = 150.0;

    //private final ObservableMap<Transformation, Overlays> overlays;
    private final TableManager tableManager;
    // The pane which actually holds the TableDisplay items:
    private final VirtualGrid mainPane;
    private final Pane overlayPane;
    private final Pane snapGuidePane;
    // The STF supplier for the main pane:
    private final DataCellSupplier dataCellSupplier = new DataCellSupplier();
    // The supplier for buttons to add rows and columns:
    private final ExpandTableArrowSupplier expandTableArrowSupplier = new ExpandTableArrowSupplier();
    // The supplier for row labels:
    private final RowLabelSupplier rowLabelSupplier = new RowLabelSupplier();
    
    // This is only put into our children while we are doing special mouse capture, but it is always non-null.
    private @Nullable Pane pickPaneMouse;
    @OnThread(Tag.FXPlatform)
    private final ObjectProperty<File> diskFile;
    // Null means modified since last save
    private final ObjectProperty<@Nullable Instant> lastSaveTime = new SimpleObjectProperty<>(null);
    // Cancels a delayed save operation:
    private @Nullable FXPlatformRunnable cancelDelayedSave;
    // Currently only used for testing:
    private @Nullable EditTransformationDialog currentlyShowingEditTransformationDialog;

    private final ObservableList<Shape> snapGuides = FXCollections.observableArrayList();
    // Pass true when empty
    private final FXPlatformConsumer<Boolean> emptyListener;

    private void save()
    {
        File dest = diskFile.get();
        class Fetcher extends FullSaver
        {
            private final Iterator<Table> it;

            public Fetcher(List<Table> allTables)
            {
                it = allTables.iterator();
            }

            @Override
            public @OnThread(Tag.Simulation) void saveTable(String s)
            {
                super.saveTable(s);
                getNext();
            }

            @OnThread(Tag.Simulation)
            private void getNext()
            {
                if (it.hasNext())
                {
                    it.next().save(dest, this, TableAndColumnRenames.EMPTY);
                }
                else
                {
                    String completeFile = getCompleteFile();
                    try
                    {
                        FileUtils.writeStringToFile(dest, completeFile, "UTF-8");
                        Instant now = Instant.now();
                        Platform.runLater(() -> lastSaveTime.setValue(now));
                    }
                    catch (IOException ex)
                    {
                        FXUtility.logAndShowError("save.error", ex);
                    }
                }
            }
        };
        //Exception e = new Exception();
        Workers.onWorkerThread("Saving", Priority.SAVE_TO_DISK, () ->
        {
            List<Table> allTablesUnordered = getAllTables();

            Map<TableId, Table> getById = new HashMap<>();
            Map<TableId, List<TableId>> edges = new HashMap<>();
            HashSet<TableId> allIds = new HashSet<>();
            for (Table t : allTablesUnordered)
            {
                allIds.add(t.getId());
                getById.put(t.getId(), t);
                if (t instanceof Transformation)
                {
                    edges.put(t.getId(), ((Transformation)t).getSources());
                }
            }
            List<TableId> linearised = GraphUtility.lineariseDAG(allIds, edges, Collections.emptyList());

            @SuppressWarnings("nullness")
            List<@NonNull Table> linearTables = Utility.mapList(linearised, id -> getById.get(id));
            new Fetcher(linearTables).getNext();

        });
    }

    @OnThread(Tag.Any)
    @NonNull
    private synchronized List<Table> getAllTables()
    {
        return tableManager.getAllTables();
    }

    @OnThread(Tag.Any)
    @NonNull
    private synchronized Stream<Table> streamAllTables()
    {
        return tableManager.streamAllTables();
    }

    @OnThread(Tag.Any)
    public TableManager getManager()
    {
        return tableManager;
    }
    
    @OnThread(Tag.Simulation)
    private void removeTable(Table t, int remainingCount)
    {
        FXUtility.runFX(() ->
        {
            save();
            //overlays.remove(t); // Listener removes them from display
            TableDisplay display = (TableDisplay) t.getDisplay();
            if (display != null)
            {
                // Call this first so that the nodes are actually removed when we call removeGridArea:
                display.cleanupFloatingItems();
                dataCellSupplier.removeGrid(display);
                mainPane.removeGridArea(display);
                expandTableArrowSupplier.removeGrid(display);
                rowLabelSupplier.removeGrid(display);
            }
            emptyListener.consume(remainingCount == 0);
        });
    }

    public void setDiskFileAndSave(File newDest)
    {
        diskFile.set(newDest);
        save();
        Utility.usedFile(newDest);
    }

    public ObservableObjectValue<@Nullable Instant> lastSaveTime()
    {
        return lastSaveTime;
    }

    public void ensureSaved()
    {
        //TODO
    }

    public void modified()
    {
        lastSaveTime.set(null);
        // TODO use a timer rather than saving instantly every time?
        save();
    }

    public VirtualGrid getGrid()
    {
        return mainPane;
    }

    public void disableTablePickingMode()
    {
        if (pickPaneMouse != null)
        {
            mainPane.stopHighlightingGridArea();
            getChildren().remove(pickPaneMouse);
            pickPaneMouse = null;
        }
    }
    
    public void enableTablePickingMode(Point2D screenPos, FXPlatformConsumer<Table> onPick)
    {
        if (pickPaneMouse != null)
            disableTablePickingMode();
        
        final @NonNull Pane pickPaneMouseFinal = pickPaneMouse = new Pane();
        
        pickPaneMouseFinal.setPickOnBounds(true);
        @Nullable Table[] picked = new @Nullable Table[1];
        pickPaneMouseFinal.setOnMouseMoved(e -> {
            @Nullable TableDisplay tableDisplay = (TableDisplay)mainPane.highlightGridAreaAtScreenPos(new Point2D(e.getScreenX(), e.getScreenY()), g -> g instanceof TableDisplay, pickPaneMouseFinal::setCursor);
            picked[0] = tableDisplay != null ? tableDisplay.getTable() : null;
            e.consume();
        });
        pickPaneMouseFinal.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && picked[0] != null)
                onPick.consume(picked[0]);
        });
        getChildren().add(pickPaneMouseFinal);
        mainPane.highlightGridAreaAtScreenPos(screenPos, g -> g instanceof TableDisplay, pickPaneMouseFinal::setCursor);
    }
    
    // If any sources are invalid, they are skipped
    private ImmutableList<Table> getSources(Table table)
    {
        if (table instanceof Transformation)
        {
            List<TableId> sourceIds = ((Transformation) table).getSources();
            return sourceIds.stream().flatMap(id -> Utility.streamNullable(tableManager.getSingleTableOrNull(id))).collect(ImmutableList.toImmutableList());
        }
        else
            return ImmutableList.of();
    }

    // Doesn't really need to be generic in both, but better type safety checking this way:
    private static <A,B> Pair<@Nullable A, @Nullable B> findFirstLeftAndFirstRight(Stream<Either<A, B>> stream)
    {
        // Atomic is a bit overkill, but creating arrays of generic types is a pain:
        AtomicReference<@Nullable A> left = new AtomicReference<>(null);
        AtomicReference<@Nullable B> right = new AtomicReference<>(null);
        for (Either<A, B> v : Utility.iterableStream(stream))
        {
            // Overwrite null only; leave other values intact:
            v.either_(x -> left.compareAndSet(null, x), x -> right.compareAndSet(null, x));

            // No point continuing if we've already found both:
            if (left.get() != null && right.get() != null)
                break;
        }

        return new Pair<>(left.get(), right.get());
    }

    /* TODO
    private boolean overlapsAnyExcept(List<TableDisplay> except, double x, double y)
    {
        List<Bounds> exceptHeaders = Utility.mapList(except, e -> new BoundingBox(x, y, e.getHeaderBoundsInParent().getWidth(), e.getHeaderBoundsInParent().getHeight()));
        return tableManager.getAllTables().stream()
                .flatMap(t -> Utility.streamNullable(t.getDisplay()))
                .filter(d -> !except.contains(d))
                .map(t -> t.getHeaderBoundsInParent())
                .anyMatch(r -> exceptHeaders.stream().anyMatch(e -> r.intersects(e)));
    }
    */

    /* TODO
    @OnThread(Tag.FXPlatform)
    private class Overlays
    {
        private final QuadCurve arrowFrom;
        private final HBox name;
        private final QuadCurve arrowTo;
        private final TableDisplay dest;
        private @MonotonicNonNull TableDisplay source;

        @OnThread(Tag.FXPlatform)
        public Overlays(List<TableDisplay> sources, String text, TableDisplay dest, FXPlatformRunnable edit)
        {
            this.dest = dest;
            Button button = new Button(text);
            button.setOnAction(e -> edit.run());
            name = new HBox(button);
            arrowFrom = new QuadCurve();
            arrowTo = new QuadCurve();
            Utility.addStyleClass(arrowFrom, "transformation-arrow");
            Utility.addStyleClass(arrowTo, "transformation-arrow");

            ChangeListener<Object> recalculate = new RecalcListener();
            
            if (!sources.isEmpty())
            {
                this.source = sources.get(0);  
                source.layoutXProperty().addListener(recalculate);
                source.layoutYProperty().addListener(recalculate);
                source.widthProperty().addListener(recalculate);
                source.heightProperty().addListener(recalculate);
            }
            
            dest.layoutXProperty().addListener(recalculate);
            dest.layoutYProperty().addListener(recalculate);
            dest.widthProperty().addListener(recalculate);
            dest.heightProperty().addListener(recalculate);

            recalculate.changed(new ReadOnlyBooleanWrapper(false), false, false);
        }

        @OnThread(Tag.FXPlatform)
        private void recalculatePosition()
        {
            double namePrefWidth = name.prefWidth(Double.MAX_VALUE);
            double namePrefHeight = name.prefHeight(Double.MAX_VALUE);
            
            if (source != null)
            {
                Pair<Point2D, Point2D> closestSrcDest = source.closestPointTo(dest.getBoundsInParent());
                double midX = 0.5 * (closestSrcDest.getFirst().getX() + closestSrcDest.getSecond().getX());
                double midY = 0.5 * (closestSrcDest.getFirst().getY() + closestSrcDest.getSecond().getY());
                
                Bounds predictedBounds = new BoundingBox(midX - namePrefWidth * 0.5, midY - namePrefHeight  * 0.5, namePrefWidth, namePrefHeight);
                
                if (!streamAllTables().<@Nullable TableDisplayBase>map(t -> t.getDisplay()).anyMatch(d -> d != null && d.getBoundsInParent().intersects(predictedBounds)))
                {
                    
                    name.layoutXProperty().unbind();
                    name.layoutXProperty().bind(name.widthProperty().multiply(-0.5).add(midX));
                    name.layoutYProperty().unbind();
                    name.layoutYProperty().bind(name.heightProperty().multiply(-0.5).add(midY));

                    Point2D from = source.closestPointTo(midX, midY - 100);
                    Point2D to = dest.closestPointTo(midX, midY + 100);

                    // Should use nearest point, not top-left:
                    arrowFrom.setLayoutX(from.getX());
                    arrowFrom.setLayoutY(from.getY());
                    arrowFrom.setControlX(midX - arrowFrom.getLayoutX());
                    arrowFrom.setControlY(midY - 100 - arrowFrom.getLayoutY());
                    arrowFrom.setEndX(midX - arrowFrom.getLayoutX());
                    arrowFrom.setEndY(midY - 50 - arrowFrom.getLayoutY());
                    arrowFrom.setVisible(true);
                    arrowTo.setLayoutX(midX);
                    arrowTo.setLayoutY(midY + 50);
                    arrowTo.setControlX(midX - arrowTo.getLayoutX());
                    arrowTo.setControlY(midY + 100 - arrowTo.getLayoutY());
                    arrowTo.setEndX(to.getX() - arrowTo.getLayoutX());
                    arrowTo.setEndY(to.getY() - arrowTo.getLayoutY());
                    arrowTo.setVisible(true);

                    return;
                }
            }
            
            // if we reach here, snap to top of table:
            double midX = dest.getBoundsInParent().getMinX() + namePrefWidth / 2.0;
            double midY = dest.getBoundsInParent().getMinY() - namePrefHeight * 0.7;
            
            
            arrowTo.setVisible(false);
            if (source != null)
            {
                Point2D from = source.closestPointTo(midX, midY - 100);
                arrowFrom.setLayoutX(from.getX());
                arrowFrom.setLayoutY(from.getY());
                arrowFrom.setControlX(midX - arrowFrom.getLayoutX());
                arrowFrom.setControlY(midY - 100 - arrowFrom.getLayoutY());
                arrowFrom.setEndX(midX - arrowFrom.getLayoutX());
                arrowFrom.setEndY(midY - 50 - arrowFrom.getLayoutY());
            }
            else
            {
                arrowFrom.setVisible(false);
            }
                
            name.layoutXProperty().unbind();
            name.layoutXProperty().bind(name.widthProperty().multiply(-0.5).add(midX));
            name.layoutYProperty().unbind();
            name.layoutYProperty().bind(name.heightProperty().multiply(-0.5).add(midY));
        }

        @OnThread(Tag.FXPlatform)
        private class RecalcListener implements ChangeListener<Object>
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<?> a, Object b, Object c)
            {
                Overlays.this.recalculatePosition();
            }
        }
    }
    */

    // The type of the listener really throws off the checkers so suppress them all:
    @SuppressWarnings({"keyfor", "interning", "userindex", "valuetype", "helpfile"})
    public View(File location, FXPlatformConsumer<Boolean> emptyListener) throws InternalException, UserException
    {
        this.emptyListener = emptyListener;
        diskFile = new SimpleObjectProperty<>(location);
        tableManager = new TableManager(TransformationManager.getInstance(), new TableManagerListener()
        {
            // No-one will add tables after the constructor, so this is okay:
            @SuppressWarnings("initialization")
            private final View thisView = View.this;

            @Override
            public void removeTable(Table t, int tablesRemaining)
            {
                thisView.removeTable(t, tablesRemaining);
            }

            @Override
            public void addSource(DataSource dataSource)
            {
                FXUtility.runFX(() -> {
                    thisView.emptyListener.consume(false);
                    VirtualGridSupplierFloating floatingSupplier = FXUtility.mouse(View.this).getGrid().getFloatingSupplier();
                    thisView.addDisplay(new TableDisplay(thisView, floatingSupplier, dataSource), null);
                    thisView.save();
                });
            }

            @Override
            public void addTransformation(Transformation transformation)
            {
                FXUtility.runFX(() ->
                {
                    thisView.emptyListener.consume(false);
                    VirtualGridSupplierFloating floatingSupplier = FXUtility.mouse(View.this).getGrid().getFloatingSupplier();
                    TableDisplay tableDisplay = new TableDisplay(thisView, floatingSupplier, transformation);
                    thisView.addDisplay(tableDisplay, thisView.getTableDisplayOrNull(transformation.getSources().get(0)));

                    List<TableDisplay> sourceDisplays = new ArrayList<>();
                    for (TableId t : transformation.getSources())
                    {
                        TableDisplay td = thisView.getTableDisplayOrNull(t);
                        if (td != null)
                            sourceDisplays.add(td);
                    }
                    /*overlays.put(transformation, new Overlays(sourceDisplays, transformation.getTransformationLabel(), tableDisplay, () ->
                    {
                        View.this.editTransform((TransformationEditable)transformation);
                    }));*/

                    thisView.save();
                });
            }
        });
        
        mainPane = new VirtualGrid((CellPosition cellPosition, Point2D mouseScreenPos, VirtualGrid virtualGrid) -> {
                // Data table if there are none, or if we ask and they say data

                View thisView = FXUtility.mouse(this);
                
                Optional<Pair<Point2D, DataOrTransform>> choice = Optional.of(new Pair<>(mouseScreenPos, DataOrTransform.DATA));
                if (!tableManager.getAllTables().isEmpty())
                {
                    // Ask what they want
                    GaussianBlur blur = new GaussianBlur(4.0);
                    blur.setInput(new ColorAdjust(0.0, 0.0, -0.2, 0.0));
                    virtualGrid.setEffectOnNonOverlays(blur);
                    choice = new DataOrTransformChoice(thisView.getWindow()).showAndWaitCentredOn(mouseScreenPos);
                    
                }
                if (choice.isPresent())
                {
                    InitialLoadDetails initialLoadDetails = new InitialLoadDetails(null, cellPosition, null);
                    switch (choice.get().getSecond())
                    {
                        case DATA:
                            Workers.onWorkerThread("Creating table", Priority.SAVE_ENTRY, () -> {
                                FXUtility.alertOnError_(() -> {
                                    
                                    ImmediateDataSource data = new ImmediateDataSource(tableManager, initialLoadDetails, EditableRecordSet.newRecordSetSingleColumn());
                                    tableManager.record(data);
                                });
                            });
                            break;
                        case TRANSFORM:
                            
                            Optional<Pair<Point2D, TransformationInfo>> optTrans =
                                new PickTransformationDialog(thisView.getWindow()).showAndWaitCentredOn(mouseScreenPos);
                                //new EditTransformationDialog(FXUtility.mouse(this).getWindow(), FXUtility.mouse(View.this), null, initialLoadDetails, new Transform.Info().editNew(FXUtility.mouse(View.this), FXUtility.mouse(this).getManager(), null, null)).showAndWait();
                                                                                    
                            optTrans.ifPresent(createTrans -> {
                                Optional<Table> optSource = new PickTableDialog(thisView, createTrans.getFirst()).showAndWait();
                                
                                Workers.onWorkerThread("Creating transformation", Priority.SAVE_ENTRY, () -> {
                                    FXUtility.alertOnError_(() -> {
                                        Transformation trans = createTrans.getSecond().makeWithSource(thisView, thisView.getManager(), cellPosition, optSource.get());
                                        tableManager.record(trans);
                                    });
                                });
                            });
                            break;
                    }
                }
                virtualGrid.setEffectOnNonOverlays(null);
        }, "main-view-grid");
        mainPane.addNodeSupplier(new VirtualGridLineSupplier());
        mainPane.addNodeSupplier(dataCellSupplier);
        mainPane.addNodeSupplier(expandTableArrowSupplier);
        mainPane.addNodeSupplier(rowLabelSupplier);
        overlayPane = new Pane();
        overlayPane.setPickOnBounds(false);
        snapGuidePane = new Pane();
        snapGuidePane.setMouseTransparent(true);
        pickPaneMouse = new Pane();
        getChildren().addAll(mainPane.getNode(), overlayPane, snapGuidePane);
        getStyleClass().add("view");

        snapGuides.addListener((ListChangeListener<Shape>) c -> {
            while (c.next())
            {
                snapGuidePane.getChildren().removeAll(c.getRemoved());
                snapGuidePane.getChildren().addAll(c.getAddedSubList());
            }
        });
    }

    private @Nullable Table pickAt(double x, double y)
    {
        return null; //TODO
        /*
        Point2D sceneLocation = localToScene(x, y);
        @Nullable Table picked = null;
        // This is paint order, so later nodes are drawn on top
        // Thus we just need to pick the last node in the list:
        for (Node node : mainPane.getChildren())
        {
            if (node instanceof TableDisplay && node.isVisible() && node.contains(node.sceneToLocal(sceneLocation)))
            {
                picked = ((TableDisplay)node).getTable();
            }
        }
        return picked;
        */
    }

    private void addDisplay(TableDisplay tableDisplay, @Nullable TableDisplay alignToRightOf)
    {
        dataCellSupplier.addGrid(tableDisplay, tableDisplay.getDataGridCellInfo());
        mainPane.addGridAreas(ImmutableList.of(tableDisplay));
        rowLabelSupplier.addTable(mainPane, tableDisplay);
        @OnThread(Tag.Any) TableOperations tableOps = tableDisplay.getTable().getOperations();
        if (tableOps.addColumn != null || tableOps.appendRows != null)
        {
            expandTableArrowSupplier.addTable(tableDisplay);
        }
    }

    private @Nullable TableDisplay getTableDisplayOrNull(TableId tableId)
    {
        @Nullable Table table = tableManager.getSingleTableOrNull(tableId);
        if (table == null)
            return null;
        else
            return (TableDisplay)table.getDisplay();
    }

    public void editTransform(TransformationEditable existing)
    {
        //EditTransformationDialog dialog = new EditTransformationDialog(getWindow(), this, existing.getId(), existing.edit(this));
        //showEditDialog(dialog, existing, existing.getMostRecentPosition());
    }

    @SuppressWarnings("nullness") // Can't be a View without an actual window
    Window getWindow()
    {
        return getScene().getWindow();
    }

    public void newTransformFromSrc(Table src)
    {
        //EditTransformationDialog dialog = new EditTransformationDialog(getWindow(), this, src.getId());
        //showEditDialog(dialog, null, null);
    }

    private void showEditDialog(EditTransformationDialog dialog, @Nullable TransformationEditable replaceOnOK, @Nullable CellPosition position)
    {
        currentlyShowingEditTransformationDialog = dialog;
        // add will re-run any dependencies:
        dialog.showAndWait().ifPresent(t -> {
            //if (replaceOnOK != null)
            //    overlays.remove(replaceOnOK);
            Workers.onWorkerThread("Updating tables", Priority.SAVE_ENTRY, () -> FXUtility.alertOnError_(() -> {
                @Nullable TableId tableId = replaceOnOK == null ? null : replaceOnOK.getId();
                tableManager.edit(tableId, t, TableAndColumnRenames.EMPTY);
            }));
        });
        currentlyShowingEditTransformationDialog = null;
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }

    public ObjectExpression<String> titleProperty()
    {
        return FXUtility.mapBindingLazy(diskFile, f -> f.getName() + " [" + f.getParent() + "]");
    }

    public @Nullable EditTransformationDialog _test_getCurrentlyShowingEditTransformationDialog()
    {
        return currentlyShowingEditTransformationDialog;
    }


    @OnThread(Tag.FXPlatform)
    public class FindEverywhereDialog extends Dialog<Void>
    {

        private final ListView<Result> results;

        private class Result
        {
            // TODO separate show text from find text
            private final String text;
            private final FXPlatformRunnable onPick;

            public Result(String text, FXPlatformRunnable onPick)
            {
                this.text = text;
                this.onPick = onPick;
            }

            public boolean matches(String findString)
            {
                return text.toLowerCase().contains(findString.toLowerCase());
            }

            @Override
            public String toString()
            {
                return text;
            }
        }

        public FindEverywhereDialog()
        {
            TextField findField = new TextField();
            results = new ListView<>();

            List<Result> allPossibleResults = findAllPossibleResults();
            results.getItems().setAll(allPossibleResults);

            FXUtility.addChangeListenerPlatformNN(findField.textProperty(), text -> {
                results.getItems().setAll(allPossibleResults.stream().filter(r -> r.matches(text)).collect(Collectors.toList()));
                results.getSelectionModel().selectFirst();
            });
            findField.setOnAction(e -> selectResult());

            BorderPane content = new BorderPane();
            content.setTop(findField);
            content.setCenter(results);
            getDialogPane().setContent(content);
            getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

            setOnShown(e -> findField.requestFocus());
        }

        @RequiresNonNull("results")
        @SuppressWarnings("initialization")
        private void selectResult(@UnknownInitialization(Object.class) FindEverywhereDialog this)
        {
            @Nullable Result result = results.getSelectionModel().getSelectedItem();
            if (result != null)
            {
                result.onPick.run();
            }
            close();
        }

        private List<Result> findAllPossibleResults(@UnknownInitialization(Object.class) FindEverywhereDialog this)
        {
            List<Result> r = new ArrayList<>();
            // Tables:
            r.addAll(Utility.mapList(getAllTables(), t -> new Result(t.getId().getRaw(), () -> {
                TableDisplay tableDisplay = (TableDisplay) t.getDisplay();
            })));

            return r;
        }
    }


}

