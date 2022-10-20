/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.trait;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import javafx.geometry.Bounds;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Orientation;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotException;
import org.testfx.api.FxRobotInterface;
import org.testfx.service.query.NodeQuery;
import org.testfx.service.query.PointQuery;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.error.InternalException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.table.app.TableDisplay;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.VirtualGrid;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.log.Log;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;
import static org.testfx.util.NodeQueryUtils.isVisible;

public interface ScrollToTrait extends FxRobotInterface, FocusOwnerTrait, QueryTrait
{
    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(String nodeLocator)
    {
        scrollTo(TFXUtil.fx(() -> lookup(nodeLocator)));
    }

    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(NodeQuery nodeLocator)
    {
        @Nullable Node targetQ = TFXUtil.fx(() -> nodeLocator.query());
        if (targetQ == null)
        {
            System.err.println("No such node to scroll to: " + nodeLocator);
            return;
        }
        scrollTo(targetQ);
    }

    // Scrolls until the entire node is on screen
    @OnThread(Tag.Any)
    default public void scrollTo(Node target)
    {
        // Find enclosing scroll:
        ScrollPane enclosingScroll = TFXUtil.fx(() -> {
            Node n = target.getParent();
            while (n != null && !(n instanceof ScrollPane))
            {
                n = n.getParent();
            }
            return (ScrollPane) n;
        });
        if (enclosingScroll == null)
        {
            fail("Could not find enclosing scroll pane");
        }

        Bounds targetBeforeScroll = TFXUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()));
        Bounds viewBounds = TFXUtil.fx(() -> enclosingScroll.localToScreen(enclosingScroll.getBoundsInLocal()));
        // Move, ready for potentially scrolling:
        moveTo(viewBounds.getMinX() + 2, viewBounds.getMinY() + 2);

        ScrollBar horizScroll = getScrollBar(enclosingScroll, Orientation.HORIZONTAL);
        ScrollBar vertScroll = getScrollBar(enclosingScroll, Orientation.VERTICAL);
        
        // We limit how many times we will scroll, to avoid an infinite loop in case of test failure:
        for (int i = 0;i < 100 && TFXUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxX()) > viewBounds.getMaxX(); i++)
        {
            System.out.println("Scrolling RIGHT");
            clickOrScroll(TFXUtil.fx(() -> from(horizScroll).lookup(".increment-button")), () -> scroll(HorizontalDirection.RIGHT));
            //scroll(HorizontalDirection.RIGHT);
        }
        for (int i = 0; i < 100 && TFXUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinX()) < viewBounds.getMinX(); i++)
        {
            System.out.println("Scrolling LEFT");
            clickOrScroll(TFXUtil.fx(() -> from(horizScroll).lookup(".decrement-button")), () -> scroll(HorizontalDirection.LEFT));
            //scroll(HorizontalDirection.LEFT);
        }
        for (int i = 0; i < 100 && TFXUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxY()) > viewBounds.getMaxY(); i++)
        {
            System.out.println("Scrolling DOWN");
            //scroll(VerticalDirection.DOWN);
            clickOrScroll(TFXUtil.fx(() -> from(vertScroll).lookup(".increment-button")), () -> scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.UP : VerticalDirection.DOWN));
        }
        for (int i = 0; i < 100 && TFXUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY()) < viewBounds.getMinY(); i++)
        {
            System.out.println("Scrolling UP");
            //scroll(VerticalDirection.UP);
            clickOrScroll(TFXUtil.fx(() -> from(vertScroll).lookup(".decrement-button")), () -> scroll(SystemUtils.IS_OS_MAC_OSX ? VerticalDirection.DOWN : VerticalDirection.UP));
        }
        Bounds targetScreenBounds = TFXUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()));
        assertTrue("View bounds: " + viewBounds + " target: " + targetScreenBounds + " target before scroll: " + targetBeforeScroll, viewBounds.contains(targetScreenBounds));
    }
    
    @OnThread(Tag.Any)
    default void keyboardMoveTo(VirtualGrid virtualGrid, CellPosition target)
    {
        Random r = new Random(target.rowIndex * 100 + target.columnIndex);
        System.out.println("Moving to position " + target);
        // Lots of tests use this method, so to speed things up,
        // we usually skip the GUI step and call a direct method:
        boolean bypassGUI = r.nextInt(10) != 1;
        if (bypassGUI)
        {
            TFXUtil.fx_(() -> virtualGrid._test_keyboardMoveTo(target));
        }
        else
        {

            int pageHeight = TFXUtil.fx(() -> virtualGrid.calcPageHeight());

            push(KeyCode.SHORTCUT, KeyCode.HOME);
            // First go to correct row:
            for (int i = 0; i < (target.rowIndex - 1) / pageHeight; i++)
            {
                push(KeyCode.PAGE_DOWN);
            }
            for (int i = 0; i < (target.rowIndex - 1) % pageHeight; i++)
            {
                push(KeyCode.DOWN);
            }

            int maxAttempts = target.columnIndex + 1;
            int attempts = 0;

            while (TFXUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.positionToEnsureInView().columnIndex).orElse(Integer.MAX_VALUE)) < target.columnIndex && attempts++ < maxAttempts)
            {
                push(KeyCode.RIGHT);
            }
        }
        // Wait for smooth scroll to finish:
        TFXUtil.sleep(300);

        Optional<CellSelection> selection = TFXUtil.fx(() -> virtualGrid._test_getSelection());
        assertTrue("Selected is " + selection.toString() + " aiming for " + target + " focus owner is " + getFocusOwner() + (bypassGUI ? " bypassed GUI" : " used GUI"), TFXUtil.fx(() -> selection.map(s -> s.isExactly(target) || s.getActivateTarget().equals(target)).orElse(false)));
    }

    @OnThread(Tag.Any)
    default CellPosition keyboardMoveTo(VirtualGrid virtualGrid, TableManager tableManager, TableId tableId, @TableDataRowIndex int row, @TableDataColIndex int col) throws UserException
    {
        Random r = new Random(row * 100 + col);
        
        // Chance of using menu is higher for rows further down.
        // Chance at top: 0.05, chance at bottom: ~0.95
        double menuChance = 0.95 - (0.9 / Math.log(row + 11));
        
        boolean usingMenu = r.nextDouble() < menuChance;
        Table table = tableManager.getSingleTableOrThrow(tableId);
        TableDisplay tableDisplay = (TableDisplay) TFXUtil.<@Nullable TableDisplayBase>fx(() -> table.getDisplay());
        assertNotNull(tableDisplay);
        if (tableDisplay == null)
            throw new RuntimeException("Impossible");
        keyboardMoveTo(virtualGrid, TFXUtil.fx(() -> {
            @TableDataRowIndex int rowIndex = usingMenu ? DataItemPosition.row(0) : row;
            return tableDisplay.getDataPosition(rowIndex, col);
        }));
        if (usingMenu)
        {
            Log.debug("Bounds for id-menu-view on-thread " + TFXUtil.fx(new TFXUtil.FXPlatformSupplierEx<Bounds>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public Bounds call() throws InternalException, UserException
                {
                    return ScrollToTrait.this.bounds(ScrollToTrait.this.lookup("#id-menu-view").<Node>query()).query();
                }
            }));
            Log.debug("Point for id-menu-view on-thread " + pointOfVisibleNode("#id-menu-view").query());
            clickOn("#id-menu-view").clickOn(".id-menu-view-goto-row");
            assertShowing("Zero-based row: " + row, ".ok-button");
            TFXUtil.sleep(200);
            // UI expects one-based:
            write(Integer.toString(row + 1));
            push(KeyCode.ENTER);
            assertNotShowing("Zero-based row: " + row, ".ok-button");
        }
        // Wait for complete refresh:
        TFXUtil.sleep(1000);
        return TFXUtil.fx(() -> tableDisplay.getDataPosition(row, col));
    }

    private PointQuery pointOfVisibleNode(String query) {
        NodeQuery nodeQuery = TFXUtil.fx(() -> lookup(query));
        Node node = queryVisibleNode(nodeQuery, "the query \"" + query + "\"");
        return point(node);
    }
    private Node queryVisibleNode(NodeQuery nodeQuery, String queryDescription) {
        Set<Node> resultNodes = nodeQuery.queryAll();
        if (resultNodes.isEmpty()) {
            throw new FxRobotException(queryDescription + " returned no nodes.");
        }
        Optional<Node> resultNode = fromNodes(resultNodes).match(isVisible()).tryQuery();
        if (!resultNode.isPresent()) {
            throw new FxRobotException(queryDescription + " returned " + resultNodes.size() + " nodes" +
                ", but no nodes were visible.");
        }
        return resultNode.get();
    }

    default CellPosition keyboardMoveTo(VirtualGrid virtualGrid, TableManager tableManager, TableId tableId, @TableDataRowIndex int row) throws UserException
    {
        return keyboardMoveTo(virtualGrid, tableManager, tableId, row, DataItemPosition.col(0));
    }

    @OnThread(Tag.Any)
    default CellPosition keyboardMoveTo(VirtualGrid virtualGrid, TableManager tableManager, TableId tableId, ColumnId columnId, @TableDataRowIndex int row) throws UserException
    {
        Table table = tableManager.getSingleTableOrThrow(tableId);
        TableDisplay display = (TableDisplay) TBasicUtil.checkNonNull(TFXUtil.fx(() -> table.getDisplay()));
        return keyboardMoveTo(virtualGrid, tableManager, tableId, row, DataItemPosition.col(Utility.findFirstIndex(TFXUtil.fx(() -> display.getDisplayColumns()), c -> c.getColumnId().equals(columnId)).orElseThrow(RuntimeException::new)));
    }
    
    // Ideally, will be private in later Java:
    @OnThread(Tag.Any)
    default public void clickOrScroll(NodeQuery nodeQuery, Runnable scroll)
    {
        Node scrollButton = TFXUtil.fx(() -> nodeQuery.tryQuery()).orElse(null);
        if (scrollButton != null)
            clickOn(scrollButton, MouseButton.PRIMARY);
        else
            scroll.run();
    }
    
    default public ScrollBar getScrollBar(ScrollPane scrollPane, Orientation orientation)
    {
        return TFXUtil.fx(() -> Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class)
            .filter(sb -> sb.getOrientation().equals(orientation))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Can't find " + orientation + " scroll bar.")));
    }
}
