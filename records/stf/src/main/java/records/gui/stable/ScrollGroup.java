package records.gui.stable;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.input.ScrollEvent;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.VirtScrollStrTextGrid.ScrollLock;
import records.gui.stable.VirtScrollStrTextGrid.SmoothScroller;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiFunction;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.IdentityHashMap;

/**
 * A scroll group is for the following notion.  A grid has a scroll position, both horizontally, and vertically.
 * It also has two scroll bars, which have a horizontal or vertical position respectively.  Added to this,
 * tables can lock together so that they scroll in unison.  So you have a morass of grids and scroll bars, all of
 * which somehow scroll together (at least in one of the dimensions).  How can we manage this?
 *
 * The answer is that all scroll events are treated by each item as a scroll request, and forwarded up to the
 * scroll group.  Every morass that is somehow scroll-locked together belongs to one scroll group.  Once the
 * scroll group decides where to scroll to, it makes calls back down to all the member items
 */
@OnThread(Tag.FXPlatform)
public class ScrollGroup
{
    private @Nullable ScrollGroup parent;
    private VirtScrollStrTextGrid.SmoothScroller smoothScrollX;

    private VirtScrollStrTextGrid.SmoothScroller smoothScrollY;
    final DoubleProperty translateXProperty = new SimpleDoubleProperty(0.0);
    final DoubleProperty translateYProperty = new SimpleDoubleProperty(0.0);
    final IntegerProperty extraRows = new SimpleIntegerProperty(0);
    final IntegerProperty extraCols = new SimpleIntegerProperty(0);
    // All the items that depend on us -- ScrollBindable items (like individual grids or scroll bars), and other scroll groups:
    private final IdentityHashMap<ScrollBindable, ScrollLock> directScrollDependents = new IdentityHashMap<>();
    private final IdentityHashMap<ScrollGroup, ScrollLock> dependentGroups = new IdentityHashMap<>();
    private boolean inUpdateClip;

    public ScrollGroup(FXPlatformBiFunction<Double, Token, ScrollResult> scrollLayoutXBy, FXPlatformFunction<Double, Integer> calcExtraCols, FXPlatformBiFunction<Double, Token, ScrollResult> scrollLayoutYBy, FXPlatformFunction<Double, Integer> calcExtraRows)
    {
        smoothScrollX = new SmoothScroller(translateXProperty, extraCols, d -> {
            ScrollResult r = scrollLayoutXBy.apply(d, new Token());
            FXUtility.mouse(this).doShowAtOffset(null, r.scrollPosition);
            return r.scrolledByPixels;
        }, calcExtraCols, FXUtility.mouse(this)::updateClip);
        smoothScrollY = new SmoothScroller(translateYProperty, extraRows, d -> {
            ScrollResult r = scrollLayoutYBy.apply(d, new Token());
            FXUtility.mouse(this).doShowAtOffset(r.scrollPosition, null);
            return r.scrolledByPixels;
        }, calcExtraRows, FXUtility.mouse(this)::updateClip);
    }

    void requestScroll(ScrollEvent scrollEvent)
    {
        requestScrollBy(-scrollEvent.getDeltaX(), -scrollEvent.getDeltaY());
    }

    void requestScrollBy(double deltaX, double deltaY)
    {
        // If we're not the root group, forward up the chain:
        if (parent != null)
        {
            parent.requestScrollBy(deltaX, deltaY);
        }
        else
        {
            if (deltaX != 0.0)
                smoothScrollX.smoothScroll(deltaX);
            
            if (deltaY != 0.0)
                smoothScrollY.smoothScroll(deltaY);
        }
    }

    void add(ScrollBindable scrollBindable, ScrollLock scrollLock)
    {
        directScrollDependents.put(scrollBindable, scrollLock);
        // TODO we need to set the scroll to right place immediately
    }

    void add(ScrollGroup scrollGroup, ScrollLock scrollLock)
    {
        dependentGroups.put(scrollGroup, scrollLock);
        // TODO we need to set the scroll to right place immediately
    }

    private void doShowAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset)
    {
        directScrollDependents.forEach((member, lock) -> {
            @Nullable Pair<Integer, Double> targetRow = lock.includesVertical() ? rowAndPixelOffset : null;
            @Nullable Pair<Integer, Double> targetCol = lock.includesHorizontal() ? colAndPixelOffset : null;
            member.showAtOffset(targetRow, targetCol);
        });
        dependentGroups.forEach((member, lock) -> {
            @Nullable Pair<Integer, Double> targetRow = lock.includesVertical() ? rowAndPixelOffset : null;
            @Nullable Pair<Integer, Double> targetCol = lock.includesHorizontal() ? colAndPixelOffset : null;
            member.doShowAtOffset(targetRow, targetCol);
        });
    }

    public void updateClip()
    {
        if (parent != null)
        {
            parent.updateClip();
        }
        // Members may call back this same method, so need to avoid an infinite loop:
        if (!inUpdateClip)
        {
            inUpdateClip = true;
            for (ScrollBindable scrollBindable : directScrollDependents.keySet())
            {
                scrollBindable.updateClip();
            }
            for (ScrollGroup scrollGroup : dependentGroups.keySet())
            {
                scrollGroup.updateClip();
            }
            inUpdateClip = false;
        }
    }
    
    public class Token
    {
        private Token() {}
    }
}
