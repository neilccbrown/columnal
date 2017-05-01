package utility.gui;

import annotation.help.qual.HelpKey;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.PopOver.ArrowLocation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Utility;
import utility.gui.Help.HelpInfo;

import java.util.List;

/**
 * A little question mark in a circle which offers a short toolip when hovered over,
 * or a longer tooltip if clicked.
 */
@OnThread(Tag.FXPlatform)
class HelpBox extends StackPane
{
    private final @HelpKey String helpId;
    private @MonotonicNonNull PopOver popOver;
    // Showing full is also equivalent to whether it is pinned.
    private final BooleanProperty showingFull = new SimpleBooleanProperty(false);
    private final BooleanProperty keyboardFocused = new SimpleBooleanProperty(false);
    private @Nullable FXPlatformRunnable cancelHover;

    public HelpBox(@HelpKey String helpId)
    {
        this.helpId = helpId;
        getStyleClass().add("help-box");
        Circle circle = new Circle(12.0);
        circle.getStyleClass().add("circle");
        Text text = new Text("?");
        text.getStyleClass().add("question");
        getChildren().setAll(circle, text);
        // We extend the node beneath the circle to put some space between the circle
        // and where the arrow of the popover shows, otherwise the popover interrupts the
        // mouseover detection and things get weird:
        minHeightProperty().set(20);
        text.setMouseTransparent(true);

        text.rotateProperty().bind(Bindings.when(showingFull).then(-45.0).otherwise(0.0));

        circle.setOnMouseEntered(e -> {
            cancelHover = FXUtility.runAfterDelay(Duration.millis(400), () -> {
                if (!popupShowing())
                    showPopOver();
            });
        });
        circle.setOnMouseExited(e -> {
            if (cancelHover != null)
            {
                cancelHover.run();
                cancelHover = null;
            }
            if (popupShowing() && !showingFull.get())
            {
                popOver.hide();
            }
        });
        circle.setOnMouseClicked(e -> {
            boolean wasPinned = showingFull.get();
            showingFull.set(true);
            if (!popupShowing())
            {
                showPopOver();
            }
            else
            {
                if (wasPinned)
                    popOver.hide();
            }
        });
    }


    @EnsuresNonNullIf(expression = "popOver", result = true)
    private boolean popupShowing(@UnknownInitialization(StackPane.class) HelpBox this)
    {
        return popOver != null && popOver.isShowing();
    }

    @OnThread(Tag.FXPlatform)
    @RequiresNonNull("helpId")
    private void showPopOver(@UnknownInitialization(StackPane.class) HelpBox this)
    {
        if (popOver == null)
        {
            @Nullable HelpInfo helpInfo = Help.getHelpInfo(helpId);
            if (helpInfo != null)
            {
                Text shortText = new Text(helpInfo.shortText);
                shortText.getStyleClass().add("short");
                TextFlow textFlow = new TextFlow(shortText);
                Text more = new Text();
                more.textProperty().bind(new ReadOnlyStringWrapper("\n\n").concat(
                    Bindings.when(keyboardFocused)
                        .then(TranslationUtility.getString("help.more.keyboard"))
                        .otherwise(TranslationUtility.getString("help.more"))));
                more.getStyleClass().add("more");
                more.visibleProperty().bind(showingFull.not());
                more.managedProperty().bind(more.visibleProperty());
                textFlow.getChildren().add(more);

                textFlow.getChildren().addAll(Utility.mapList(helpInfo.fullParas, p ->
                {
                    // Blank line between paragraphs:
                    Text t = new Text("\n\n" + p);
                    t.getStyleClass().add("full");
                    t.visibleProperty().bind(showingFull);
                    t.managedProperty().bind(t.visibleProperty());
                    return t;
                }));


                BorderPane pane = new BorderPane(textFlow);
                pane.getStyleClass().add("help-content");
                FXUtility.addChangeListenerPlatformNN(showingFull, b -> pane.requestLayout());

                popOver = new PopOver(pane);
                popOver.setTitle(helpInfo.title);
                popOver.setArrowLocation(ArrowLocation.TOP_LEFT);
                popOver.getStyleClass().add("help-popup");
                popOver.setAnimated(false);
                popOver.setArrowIndent(30);
                popOver.setOnHidden(e -> {showingFull.set(false);});
                // Remove minimum height constraint:
                // We can only do this once skin has been set (which is what binds
                // it in the first place):
                FXUtility.onceNotNull(popOver.skinProperty(), sk -> {
                    if (popOver != null)
                    {
                        popOver.getRoot().minHeightProperty().unbind();
                        popOver.getRoot().minHeightProperty().set(0);
                    }
                });
            }

        }
        // Not guaranteed to have been created, if we can't find the hint:
        if (popOver != null)
        {
            popOver.show(this);
            //org.scenicview.ScenicView.show(popOver.getRoot().getScene());
        }
    }

    /**
     * Cycles through: not showing, showing, showing full.
     */
    public void cycleStates()
    {
        if (!popupShowing())
        {
            showPopOver();
        }
        else
        {
            if (!showingFull.get())
            {
                showingFull.set(true);
            }
            else
            {
                popOver.hide();
            }
        }
    }

    public void bindKeyboardFocused(@Nullable BooleanExpression keyboardFocused)
    {
        if (keyboardFocused != null)
            this.keyboardFocused.bind(keyboardFocused);
    }
}
