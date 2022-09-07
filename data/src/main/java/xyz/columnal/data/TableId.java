package xyz.columnal.data;

import annotation.identifier.qual.ExpressionIdentifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.columnal.styled.StyledShowable;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableId implements Comparable<TableId>, StyledShowable
{
    private final @ExpressionIdentifier String tableId;

    public TableId(@ExpressionIdentifier String tableId)
    {
        this.tableId = tableId;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TableId tableId1 = (TableId) o;

        return tableId.equals(tableId1.tableId);

    }

    @Override
    public int hashCode()
    {
        return tableId.hashCode();
    }

    @Override
    public String toString()
    {
        return tableId;
    }

    public String getOutput()
    {
        return tableId;
    }

    public @ExpressionIdentifier String getRaw()
    {
        return tableId;
    }

    @Override
    public int compareTo(@NonNull TableId o)
    {
        return tableId.compareTo(o.tableId);
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s(tableId);
    }
}