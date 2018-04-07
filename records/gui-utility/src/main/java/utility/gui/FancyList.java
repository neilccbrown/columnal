package utility.gui;

import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * A ListView which allows deletion of selected items using a little cross to the right (or pressing backspace/delete), which is
 * animated by sliding out the items.
 */
public abstract class FancyList<T, CELL_CONTENT extends Node>
{
    private final VBox children = new VBox();
    private final ObservableList<Cell> cells = FXCollections.observableArrayList();
    private final BitSet selection = new BitSet();
    private boolean dragging;
    private boolean hoverOverSelection = false;
    private final boolean allowReordering;
    private final boolean allowDeleting;
    private final ScrollPaneFill scrollPane = new ScrollPaneFill(children);
    private final BorderPane bottomPane = new BorderPane();
    protected final @Nullable Button addButton;

    public FancyList(ImmutableList<T> initialItems, boolean allowDeleting, boolean allowReordering, boolean allowInsertion)
    {
        this.allowDeleting = allowDeleting;
        this.allowReordering = allowReordering;
        children.setFillWidth(true);
        children.setOnKeyPressed(e -> {
            if (allowDeleting && (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE))
            {
                FXUtility.keyboard(this).deleteCells(Utility.later(this).getSelectedCells());
            }
        });
        children.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                selection.clear();
                FXUtility.mouse(this).updateSelectionState();
            }
            e.consume();
        });
        children.setOnMouseDragged(e -> {
            if (allowReordering)
            {
                // TODO update the selection to include source item
                dragging = true;
                FXUtility.mouse(this).updateDragPreview(new Point2D(e.getX(), e.getY()));
            }
        });
        children.setOnMouseReleased(e -> {
            if (dragging && allowReordering)
            {
                int target = FXUtility.mouse(this).findClosestDragTarget(new Point2D(e.getX(), e.getY()));
                if (target != -1)
                {
                    FXUtility.mouse(this).dragSelectionTo(target);
                }
            }
            dragging = false;
            FXUtility.mouse(this).updateDragPreview(null);
        });
        for (T initialItem : initialItems)
        {
            cells.add(new Cell(initialItem, false));
        }
        if (allowInsertion)
        {
            addButton = GUI.button("fancylist.add", () -> {
                addToEnd(null, true);
            });
            bottomPane.setCenter(addButton);
            BorderPane.setMargin(addButton, new Insets(6));
        }
        else
        {
            addButton = null;
        }
        bottomPane.getStyleClass().add("fancy-list-end");
        updateChildren();
    }

    private void dragSelectionTo(int target)
    {
        // We must adjust target for all the items we are removing:
        target -= selection.get(0, target).cardinality();
        List<Cell> selected = new ArrayList<>();
        
        for (int original = 0, adjusted = 0; original < cells.size(); original++) // Adjusted increment is conditional, in loop
        {
            if (selection.get(original))
            {
                selected.add(cells.remove(adjusted));
                // Don't increment adjusted as it already points to the next cell
            }
            else
            {
                adjusted += 1;
            }
        }
        cells.addAll(target, selected);
        selection.clear();
        // Or should we retain selection?
        updateChildren();
        updateSelectionState();
    }

    // If null is passed, we are not dragging, so turn off preview
    private void updateDragPreview(@Nullable Point2D childrenPoint)
    {
        if (children.getChildren().isEmpty())
            return;
        
        int index = childrenPoint == null ? -1 : findClosestDragTarget(childrenPoint);
        for (int i = 0; i < children.getChildren().size(); i++)
        {
            FXUtility.setPseudoclass(children.getChildren().get(i), "drag-target", i == index);
        }
    }

    private int findClosestDragTarget(Point2D childrenPoint)
    {
        for (int i = 0; i < children.getChildren().size(); i++)
        {
            Node item = children.getChildren().get(i);
            double rel = childrenPoint.getY() - item.getLayoutY();
            double itemHeight = item.getBoundsInParent().getHeight();
            if (0 <= rel && rel <= itemHeight / 2.0)
            {
                return i;
            }
            else if (rel <= itemHeight)
            {
                return Math.min(i + 1, children.getChildren().size() - 1);
            }
        }
        return -1;
    }

    public ObservableList<String> getStyleClass(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this)
    {
        return scrollPane.getStyleClass();
    }

    private ImmutableList<Cell> getSelectedCells()
    {
        ImmutableList.Builder<Cell> builder = ImmutableList.builder();
        for (int i = 0; i < cells.size(); i++)
        {
            if (selection.get(i))
                builder.add(cells.get(i));
        }
        return builder.build();
    }

    // For overriding in subclasses:
    @OnThread(Tag.FXPlatform)
    protected abstract Pair<CELL_CONTENT, ObjectExpression<T>> makeCellContent(@Nullable T initialContent, boolean editImmediately);

    private void deleteCells(List<Cell> selectedCells)
    {
        animateOutToRight(selectedCells, () -> {
            cells.removeAll(selectedCells);
            updateChildren();
        });
    }

    private void animateOutToRight(List<Cell> cells, FXPlatformRunnable after)
    {
        SimpleDoubleProperty amount = new SimpleDoubleProperty(0);
        for (Cell cell : cells)
        {
            cell.translateXProperty().bind(amount);
        }
        
        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
                Utility.mapList(cells, c -> new KeyValue(amount, c.getWidth())).toArray(new KeyValue[0])));
        
        t.setOnFinished(e -> after.run());
        t.play();

    }

    @RequiresNonNull({"children", "cells"})
    private void updateChildren(@UnknownInitialization(Object.class) FancyList<T, CELL_CONTENT> this)
    {
        for (int i = 0; i < cells.size(); i++)
        {
            boolean even = (i % 2) == 0;
            FXUtility.setPseudoclass(cells.get(i), "even", even);
            FXUtility.setPseudoclass(cells.get(i), "odd", !even);
        }
        ArrayList<Node> nodes = new ArrayList<>(this.cells);
        nodes.add(bottomPane);
        children.getChildren().setAll(nodes);
        scrollPane.fillViewport();
    }

    public ImmutableList<T> getItems()
    {
        return cells.stream().map(c -> c.value.get()).collect(ImmutableList.toImmutableList());
    }
    
    protected Stream<Cell> streamCells(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this)
    {
        return cells.stream();
    }

    public Region getNode()
    {
        return scrollPane;
    }

    protected void clearSelection()
    {
        selection.clear();
        updateSelectionState();
    }

    /**
     * Gets the nearest gap before/after a cell to the given scene X/Y position.  The first component
     * of the pair is the cell above (may be blank if at top of list), the second component is the
     * one below (ditto if at bottom).  Both parts may be blank if list is empty.
     * @return
     */
    /*
    @OnThread(Tag.FXPlatform)
    public Pair<@Nullable DeletableListCell, @Nullable DeletableListCell> getNearestGap(double sceneX, double sceneY)
    {
        // Y is in scene coords.  We set initial a pixel outside so that any items within bounds will "win" against them:
        Pair<Double, @Nullable DeletableListCell> nearestAbove = new Pair<>(localToScene(0.0, -1.0).getY(), null);
        Pair<Double, @Nullable DeletableListCell> nearestBelow = new Pair<>(localToScene(0.0, getHeight() + 1.0).getY(), null);

        for (WeakReference<DeletableListCell> ref : allCells)
        {
            @Nullable DeletableListCell cell = ref.get();
            if (cell != null && !cell.isEmpty())
            {
                Bounds sceneBounds = cell.localToScene(cell.getBoundsInLocal());
                if (Math.abs(sceneBounds.getMaxY() - sceneY) < Math.abs(nearestAbove.getFirst() - sceneY))
                {
                    nearestAbove = new Pair<>(sceneBounds.getMaxY(), cell);
                }

                if (Math.abs(sceneBounds.getMinY() - sceneY) < Math.abs(nearestBelow.getFirst() - sceneY))
                {
                    nearestBelow = new Pair<>(sceneBounds.getMinY(), cell);
                }
            }
        }

        // If nearest below is above nearest above, we picked both from last cell in the list; only return the nearest above:
        if (nearestBelow.getFirst() < nearestAbove.getFirst())
            return new Pair<>(nearestAbove.getSecond(), null);
        else
            return new Pair<>(nearestAbove.getSecond(), nearestBelow.getSecond());
    }*/

    @OnThread(Tag.FXPlatform)
    protected class Cell extends BorderPane
    {
        protected final SmallDeleteButton deleteButton;
        protected final CELL_CONTENT content;
        private final ObjectExpression<T> value;
        
        public Cell(@Nullable T initialContent, boolean editImmediately)
        {
            getStyleClass().add("fancy-list-cell");
            deleteButton = new SmallDeleteButton();
            deleteButton.setOnAction(() -> {
                if (isSelected(this))
                {
                    // Delete all in selection
                    deleteCells(getSelectedCells());
                }
                else
                {
                    // Just delete this one
                    deleteCells(ImmutableList.of(Utility.later(this)));
                }
            });
            deleteButton.setOnHover(entered -> {
                if (isSelected(this))
                {
                    hoverOverSelection = entered;
                    // Set hover state on all (including us):
                    for (Cell selectedCell : getSelectedCells())
                    {
                        selectedCell.updateHoverState(hoverOverSelection);
                    }

                }
                // If not selected, nothing to do
            });
            setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                {
                    if (e.isShortcutDown())
                    {
                        selection.set(getIndex());
                    }
                    else if (e.isShiftDown())
                    {
                        if (!selection.get(getIndex()))
                        {
                            // Find the next earliest selection above us:
                            int prev = selection.previousSetBit(getIndex());
                            if (prev != -1)
                            {
                                selection.set(prev, getIndex() + 1);
                            }
                            else
                            {
                                // Next beyond us:
                                int next = selection.nextSetBit(getIndex());
                                if (next != -1)
                                {
                                    selection.set(getIndex(), next);
                                }
                                else
                                {
                                    // Just select us, then:
                                    selection.set(getIndex());
                                }
                            }
                        }
                    }
                    else
                    {
                        selection.clear();
                        selection.set(getIndex());
                    }
                    updateSelectionState();
                    e.consume();
                }
            });
            //deleteButton.visibleProperty().bind(deletable);
            setMargin(deleteButton, new Insets(0, 4, 0, 4));
            Pair<CELL_CONTENT, ObjectExpression<T>> pair = makeCellContent(initialContent, editImmediately);
            this.content = pair.getFirst();
            this.value = pair.getSecond();
            setCenter(this.content);
            if (allowDeleting)
                setRight(deleteButton);
        }

        private int getIndex(@UnknownInitialization Cell this)
        {
            return Utility.indexOfRef(cells, this);
        }

        @OnThread(Tag.FXPlatform)
        private void updateHoverState(boolean hovering)
        {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("my_hover_sel"), hovering);
        }

        public CELL_CONTENT getContent()
        {
            return content;
        }
    }

    private void updateSelectionState()
    {
        for (int i = 0; i < cells.size(); i++)
        {
            FXUtility.setPseudoclass(cells.get(i), "selected", selection.get(i));
        }
    }

    private boolean isSelected(@UnknownInitialization Cell cell)
    {
        int index = Utility.indexOfRef(cells, cell);
        return index < 0 ? false : selection.get(index);
    }
    
    protected void addToEnd(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this, @Nullable T content, boolean editImmediately)
    {
        cells.add(new Cell(content, editImmediately));
        updateChildren();
    }
    
    protected void listenForCellChange(@UnknownInitialization(FancyList.class) FancyList<T, CELL_CONTENT> this, FXPlatformConsumer<Change<? extends Cell>> listener)
    {
        FXUtility.listen(cells, listener);
    }
}