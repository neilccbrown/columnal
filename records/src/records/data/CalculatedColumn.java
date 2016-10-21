package records.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class CalculatedColumn<T> extends Column<T>
{
    private final String name;
    private final ArrayList<T> cachedValues = new ArrayList<T>();
    private final ArrayList<Column> dependencies;
    // Version of each of the dependencies at last calculation:
    private final Map<Column, Long> calcVersions = new IdentityHashMap<>();
    private long version = 1;

    public CalculatedColumn(String name, Column... dependencies)
    {
        this.name = name;
        this.dependencies = new ArrayList<>(Arrays.asList(dependencies));
    }

    @Override
    public final T get(int index) throws Exception
    {
        if (checkCacheValid())
        {
            if (index < cachedValues.size())
                return cachedValues.get(index);
        }
        else
        {
            cachedValues.clear();
            version += 1;
        }
        // Fetch values:
        for (int i = 0; i <= index; i++)
        {
            cachedValues.add(calculate(index));
        }
        return cachedValues.get(index);
    }

    private boolean checkCacheValid()
    {
        boolean allValid = true;
        for (Column c : dependencies)
        {
            Long lastVer = calcVersions.get(c);
            if (lastVer == null || lastVer.longValue() != c.getVersion())
            {
                calcVersions.put(c, c.getVersion());
                allValid = false;
            }
        }
        return allValid;
    }

    @Override
    public final String getName()
    {
        return name;
    }

    protected abstract T calculate(int index) throws Exception;

    @Override
    public final long getVersion()
    {
        return version;
    }
}
