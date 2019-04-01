package records.data.datatype;

import annotation.qual.Value;
import records.data.Column;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility.ListEx;

/**
 * A ListEx wrapped around a column
 */
@OnThread(Tag.Simulation)
public class ListExDTV extends ListEx
{
    private final int length;
    private final DataTypeValue columnType;

    public ListExDTV(Column column) throws UserException, InternalException
    {
        this.length = column.getLength();
        this.columnType = column.getType();
    }
    
    public ListExDTV(int length, DataTypeValue values)
    {
        this.length = length;
        this.columnType = values;
    }

    @Override
    public int size() throws InternalException, UserException
    {
        return length;
    }

    @Override
    public @Value Object get(int index) throws InternalException, UserException
    {
        return columnType.getCollapsed(index);
    }
}