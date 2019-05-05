package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.TypeManager;
import records.transformations.expression.FixHelper;
import records.transformations.expression.type.TypeExpression;
import utility.FXPlatformConsumer;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeLexer, CodeCompletionContext>
{
    public TypeEditor(TypeManager typeManager, @Nullable TypeExpression originalContent, boolean requireConcreteType, boolean emptyAllowed,
                      FixHelper fixHelper, FXPlatformConsumer<@NonNull @Recorded TypeExpression> onChange)
    {
        super(originalContent == null ? null : originalContent.save(false, new TableAndColumnRenames(ImmutableMap.of())), new TypeLexer(typeManager, requireConcreteType, emptyAllowed), fixHelper, onChange, "type-editor");
    }
}
