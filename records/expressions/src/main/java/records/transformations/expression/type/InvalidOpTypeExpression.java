package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.TypeEntry;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import records.transformations.expression.BracketedStatus;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Utility;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InvalidOpTypeExpression extends TypeExpression
{
    private final ImmutableList<@Recorded TypeExpression> items;

    public InvalidOpTypeExpression(ImmutableList<@Recorded TypeExpression> items)
    {
        this.items = items;
    }

    @Override
    public @OnThread(Tag.FXPlatform) Stream<SingleLoader<TypeExpression, TypeSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return items.stream().flatMap(x -> x.loadAsConsecutive(BracketedStatus.MISC));
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        if (structured)
            return OutputBuilder.token(FormatLexer.VOCABULARY, FormatLexer.INVALIDOPS) + " (" +
                items.stream().map(item -> item.save(structured, renames)).collect(Collectors.joining(", ")) 
                + ")";
        else
            return items.stream().map(item -> item.save(structured, renames)).collect(Collectors.joining(""));
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException
    {
        throw new UserException("Invalid type expression: " + this);
    }

    @Override
    public boolean isEmpty()
    {
        return items.isEmpty();
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Invalid"); // TODO
    }

    public ImmutableList<@Recorded TypeExpression> _test_getItems()
    {
        return items;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvalidOpTypeExpression that = (InvalidOpTypeExpression) o;
        return Objects.equals(items, that.items);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(items);
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOpTypeExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }
}
