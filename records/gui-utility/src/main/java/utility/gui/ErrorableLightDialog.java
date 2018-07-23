package utility.gui;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;

/**
 * A dialog which has an error label, and a single converter function which returns
 * either an error or the result.  Prevents OK button doing anything if we are currently
 * in the error state.
 */
public abstract class ErrorableLightDialog<R> extends LightDialog<R>
{
    private final ErrorLabel errorLabel = new ErrorLabel();
    private @Nullable R result;

    @SuppressWarnings("initialization") // For the OK event filter.  Can't click until dialog shows!
    public ErrorableLightDialog(Window parent, boolean buttonsToSide)
    {
        super(parent, buttonsToSide ? new DialogPaneWithSideButtons() : null);
        getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().lookupButton(ButtonType.OK).getStyleClass().add("ok-button");
        getDialogPane().lookupButton(ButtonType.CANCEL).getStyleClass().add("cancel-button");
        getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            calculateResult().either_(err -> {
                result = null;
                errorLabel.setText(err);
                e.consume(); // Prevent OK doing anything
            }, r -> {result = r;});
        });
        //We bind so that if subclass mistakenly tries to set, it will get an error:
        resultConverterProperty().bind(new ReadOnlyObjectWrapper<>(bt -> {
            if (bt == ButtonType.OK)
            {
                if (result != null)
                {
                    return result;
                }
                else
                {
                    Log.logStackTrace("OK pressed successfully but blank result");
                }
            }
            return null;
        }));
    }

    // Given back as Node because it's only meant for adding to GUI.  Subclasses don't set
    // the text, we do.
    @OnThread(Tag.FXPlatform)
    public final Node getErrorLabel(@UnknownInitialization(ErrorableLightDialog.class) ErrorableLightDialog<R>this)
    {
        return errorLabel;
    }

    /**
     * Gets either an error or the result.  If there is an error, it may be called again.  If a
     * result is returned, it will not be called again.
     */
    @OnThread(Tag.FXPlatform)
    protected abstract Either<@Localized String, R> calculateResult();
}