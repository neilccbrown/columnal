package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.columntype.NumericColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataTypeValue;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileNumericColumn extends TextFileColumn
{
    private final NumericColumnStorage loadedValues;

    public TextFileNumericColumn(RecordSet recordSet, File textFile, long fileStartPosition, byte sep, ColumnId columnName, int columnIndex, NumericColumnType type) throws InternalException, UserException
    {
        super(recordSet, textFile, fileStartPosition, sep, columnName, columnIndex);
        loadedValues = new NumericColumnStorage(new NumberDisplayInfo(type.displayPrefix, type.minDP));
    }

    private Number getWithProgress(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        try
        {
            // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
            // true if user scrolled in table, false if we are performing a calculation
            while (index >= loadedValues.filled())
            {
                double startedAt = loadedValues.filled();
                ArrayList<String> next = new ArrayList<>();
                lastFilePosition = Utility.readColumnChunk(textFile, lastFilePosition, sep, columnIndex, next);
                if (!lastFilePosition.isEOF())
                {
                    for (String s : next)
                    {
                        loadedValues.addRead(s);
                    }
                    if (progressListener != null)
                        progressListener.progressUpdate(((double)loadedValues.filled() - startedAt) / ((double)index - startedAt));
                }
                else
                    throw new FetchException("Error reading line: " + loadedValues.filled(), new EOFException());
                // TODO handle case where file changed outside.
            }

            return getObject(index);
        }
        catch (IOException e)
        {
            throw new FetchException("Error reading " + textFile, e);
        }
    }

    @SuppressWarnings("nullness")
    private Number getObject(int index) throws InternalException
    {
        return loadedValues.get(index);
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return loadedValues.getType();
    }
}
