package records.gui;

import javafx.beans.Observable;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExSupplier;
import utility.FXPlatformFunction;
import utility.Utility;
import utility.gui.FXUtility;


/**
 * A wrapper for a TextField which supports two extras:
 *
 * - A converter from text to a custom type, which supports giving an error if conversion fails
 * - Ability to display an error (either because of conversion failure, or any other reason)
 */
@OnThread(Tag.FXPlatform)
public class ErrorableTextField<T>
{
    private final TextField field = new TextField();
    private final FXPlatformFunction<String, ConversionResult<T>> converter;
    private final ObjectProperty<ConversionResult<T>> converted;
    private final ObjectExpression<@Nullable T> value;
    private final ObjectExpression<String> error;

    public ErrorableTextField(FXPlatformFunction<String, ConversionResult<T>> converter, ObservableValue... conversionDependencies)
    {
        field.getStyleClass().add("errorable-text-field");
        this.converter = converter;
        this.converted = new SimpleObjectProperty<>(converter.apply(""));
        Utility.addChangeListenerPlatformNN(field.textProperty(), s -> converted.setValue(converter.apply(s)));
        for (ObservableValue dependency : conversionDependencies)
        {
            Utility.addChangeListenerPlatform(dependency, o -> converted.setValue(converter.apply(field.getText())));
        }
        this.value = FXUtility.<ConversionResult<T>, @Nullable T>mapBindingEager(converted, ConversionResult::getValue);
        this.error = FXUtility.<ConversionResult<T>, String>mapBindingEager(converted, ConversionResult::getError);
        Utility.addChangeListenerPlatform(error, err -> {
            FXUtility.setPseudoclass(field, "has-error", err != null);
            // TODO show a tooltip
        });
    }

    public BooleanProperty disableProperty()
    {
        return field.disableProperty();
    }

    public ObjectExpression<@Nullable T> valueProperty()
    {
        return value;
    }

    public Node getNode()
    {
        return field;
    }

    public static class ConversionResult<T>
    {
        private final boolean success;
        private final @Nullable T value;
        private final @Nullable String error;

        private ConversionResult(T value)
        {
            this.success = true;
            this.value = value;
            this.error = null;
        }

        private ConversionResult(String error)
        {
            this.success = false;
            this.value = null;
            this.error = error;
        }

        public @Nullable T getValue()
        {
            return value;
        }

        @Pure
        public @Nullable String getError()
        {
            return error;
        }

        public static <T> ConversionResult<T> success(T value)
        {
            return new ConversionResult<T>(value);
        }

        public static <T> ConversionResult<T> error(String error)
        {
            return new ConversionResult<T>(error);
        }
    }

    public static <T> ConversionResult<T> validate(ExSupplier<T> getValue)
    {
        try
        {
            return new ConversionResult<T>(getValue.get());
        }
        catch (InternalException e)
        {
            return new ConversionResult<T>("Internal Error: " + e.getLocalizedMessage());
        }
        catch (UserException e)
        {
            return new ConversionResult<T>(e.getLocalizedMessage());
        }
    }
}
