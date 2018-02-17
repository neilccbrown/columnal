package records.gui.grid;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import records.data.CellPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A class which manages and supplies nodes of a given type.
 * 
 * When we are displaying a sheet, we don't want to make a GUI node for every cell which would be displayed
 * for, say, a 100,000 long table.  We only actually need GUI nodes for the items currently visible.
 * (We also have a few just off-screen, so that minor scrolling does not cause a delay loading new nodes).
 * 
 * So what we do is have a VirtualGridSupplier for each node type that we may want (one for data cells,
 * but also one for table headers, one for grid lines, and so on).  Each node is responsible for doing
 * the layout of the on-screen nodes, and adding/removing nodes to the GUI pane as we scroll around or as
 * things change (tables get added, resized, etc).  This parent class is very generic: it just has one
 * abstract method for doing the layout.  Most subclasses will want to extend {@link VirtualGridSupplierIndividual},
 * which has extra logic for most common cases.
 */
@OnThread(Tag.FXPlatform)
public abstract class VirtualGridSupplier<T extends Node>
{
    /**
     * Layout the items for the current visible pane.
     * 
     * @param containerChildren The modifiable list of children of the actual GUI pane.  Add/remove nodes to this list. 
     * @param rowBounds The row bounds (vertical) of the current visible items (including any needed for scrolling)
     * @param columnBounds The column bounds (horizontal) of the current visible items (including any needed for scrolling)
     */
    // package-visible
    abstract void layoutItems(List<Node> containerChildren, VisibleDetails rowBounds, VisibleDetails columnBounds);
    
    // Used for both rows and columns, to specify visible extents and divider positions
    public static abstract class VisibleDetails
    {
        // Index of the first column/row visible (inclusive)
        protected int firstItemIncl;
        // Index of the last column/row visible (inclusive)
        protected int lastItemIncl;

        public VisibleDetails(int firstItemIncl, int lastItemIncl)
        {
            this.firstItemIncl = firstItemIncl;
            this.lastItemIncl = lastItemIncl;
        }

        // The X/Y position of the left/top of the given item index
        @OnThread(Tag.FXPlatform)
        protected abstract double getItemCoord(int itemIndex);
    }
}
