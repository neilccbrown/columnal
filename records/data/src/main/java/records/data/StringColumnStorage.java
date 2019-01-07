package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;
import utility.Either;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage extends SparseErrorColumnStorage<String> implements ColumnStorage<String>
{
    private final ArrayList<@Value String> values;
    @SuppressWarnings("unchecked")
    private final DumbObjectPool<@Value String> pool = new DumbObjectPool<>((Class<@Value String>)(Class)String.class, 1000, null);
    private final @Nullable BeforeGet<StringColumnStorage> beforeGet;
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;

    public StringColumnStorage(@Nullable BeforeGet<StringColumnStorage> beforeGet)
    {
        values = new ArrayList<>();
        this.beforeGet = beforeGet;
    }

    public StringColumnStorage()
    {
        this(null);
    }

    @Override
    public int filled()
    {
        return values.size();
    }
    
    public @Value String get(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        if (beforeGet != null)
            beforeGet.beforeGet(this, index, progressListener);
        if (index < 0 || index >= values.size())
            throw new UserException("Attempting to access invalid element: " + index + " of " + values.size());
        return values.get(index);
    }

    @Override
    public void addAll(Stream<Either<String, String>> items) throws InternalException
    {
        for (Either<String, String> item : Utility.iterableStream(items))
        {
            String s = item.either(err -> "", v -> v);
            item.ifLeft(err -> setError(this.values.size(), err));
            this.values.add(pool.pool(DataTypeUtility.value(s)));
        }
    }

    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        /*
        if (longs != null)
        {
            for (long l : longs)
                if (l == SEE_BIGDEC)
                    return v -> v.number();
        }
        */
        if (dataType == null)
        {
            dataType = DataTypeValue.text(new GetValue<@Value String>()
            {
                @Override
                public @Value String getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                {
                    return StringColumnStorage.this.get(i, prog);
                }

                @Override
                public @OnThread(Tag.Simulation) void set(int index, Either<String, @Value String> value) throws InternalException
                {
                    value.eitherInt_(err -> {
                        setError(index, err);
                        setValue(index, DataTypeUtility.value(""));
                    }, v -> setValue(index, v));
                }
            });
        }
        return dataType;
    }

    public void setValue(int index, @Value String value) throws InternalException
    {
        if (index == values.size())
        {
            values.add(pool.pool(value));

        }
        else
        {
            values.set(index, pool.pool(value));
        }
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable String> items) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to insert rows at invalid index: " + index + " length is: " + values.size());
        values.ensureCapacity(values.size() + items.size());
        for (String item : items)
        {
            if (item == null)
                item = "";
            values.add(pool.pool(DataTypeUtility.value(item)));
        }
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        if (index < 0 || index + count > values.size())
            throw new InternalException("Trying to remove rows at invalid index: " + index + " length is: " + values.size());
        List<@Value String> old = new ArrayList<>(values.subList(index, index + count));
        values.subList(index, index + count).clear();
        return () -> values.addAll(index, old);
    }
}
