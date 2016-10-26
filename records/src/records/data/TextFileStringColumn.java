package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import records.error.FetchException;
import records.error.UserException;
import utility.CompleteStringPool;
import utility.Utility;
import utility.Utility.ReadState;
import utility.Workers;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileStringColumn extends TextFileColumn
{
    private String[] loadedValues = new String[0];
    private final CompleteStringPool pool = new CompleteStringPool(1000);

    public TextFileStringColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, String columnName, int columnIndex)
    {
        super(recordSet, textFile, initialFilePosition, sep, columnName, columnIndex);
    }

    @Override
    public String get(int index) throws UserException
    {
        try
        {
            // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
            // true if user scrolled in table, false if we are performing a calculation
            boolean firstChunk = true;
            while (index >= loadedValues.length)
            {
                if (!firstChunk)
                    Workers.maybeYield();
                firstChunk = false;
                ArrayList<String> next = new ArrayList<>();
                lastFilePosition = Utility.readColumnChunk(textFile, lastFilePosition, sep, columnIndex, next);
                if (!lastFilePosition.isEOF())
                {
                    int prevSize = loadedValues.length;
                    // Yes they do become null, but they won't be null
                    // after we've finished the loop:
                    @SuppressWarnings("nullness")
                    String[] newLoadedValues = Arrays.copyOf(loadedValues, prevSize + next.size());
                    for (int i = 0; i < next.size(); i++)
                    {
                        newLoadedValues[prevSize + i] = pool.pool(next.get(i));
                    }
                    loadedValues = newLoadedValues;
                    gotMore();
                }
                else
                    throw new FetchException("Error reading line", new EOFException());
                // TODO handle case where file changed outside.
            }

            return loadedValues[index];
        }
        catch (IOException e)
        {
            throw new FetchException("Error reading " + textFile, e);
        }
    }

    @Override
    protected double indexProgress(int index) throws UserException
    {
        if (index < loadedValues.length)
            return 2.0;
        else if (index == 0)
            return 0.0;
        else
            return (double)(loadedValues.length - 1) / (double)index;
    }

    @Override
    public Class<String> getType()
    {
        return String.class;
    }

    @Override
    public Optional<List<@NonNull ? extends Object>> fastDistinct() throws UserException
    {
        //indexValid(0);
        //return (loadedValues.size() < rowCount || pool.isFull()) ? Optional.<List<@NonNull ? extends Object>>empty() : Optional.<List<@NonNull ? extends Object>>of(pool.get());
        return Optional.empty();
    }
}
