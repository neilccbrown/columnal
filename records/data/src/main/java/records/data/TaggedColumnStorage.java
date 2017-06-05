package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;
import utility.TaggedValue;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage implements ColumnStorage<TaggedValue>
{
    // This stores the tag index
    private final NumericColumnStorage tagStore;
    // This stores the index at which each inner value resides.
    // For example, let's stay you have the type:
    // \A:Number \B \C:Date \D:Number
    // And you add the following data:
    // \A:0
    // \B
    // \A:4
    // \C:...
    // \D:6
    // \A:5
    // tagStore will have 0, 1, 0, 2, 3, 0 (the tag indexes)
    // innerValueIndex will have 0, -1 [none], 1, 0, 0, 2 (effectively, the per-tag index
    // within the corresponding value store).  i.e. we compress each
    // storage so that we don't leave gaps.  The indexes must be
    // in ascending order
    private final NumericColumnStorage innerValueIndex;
    // This is the list of storage columns for inner types.
    // We could share them among same-types, but it doesn't save memory so
    // we keep it simple.  We put a null entry where there is no inner type.
    // We could also collapse nested tagged columns, but we choose not to,
    // again to keep it simple:
    protected final List<@Nullable ColumnStorage<?>> valueStores;
    private final List<TagType<DataTypeValue>> tagTypes;
    // Effectively a cached version of tagTypes:
    @OnThread(Tag.Any)
    private final DataTypeValue dataType;

    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, List<TagType<DT>> copyTagTypes) throws InternalException
    {
        this(typeName, copyTagTypes, null);
    }

    @SuppressWarnings("initialization")
    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, List<TagType<DT>> copyTagTypes, @Nullable BeforeGet<TaggedColumnStorage> beforeGet) throws InternalException
    {
        tagStore = new NumericColumnStorage();
        innerValueIndex = new NumericColumnStorage();
        valueStores = new ArrayList<>();
        tagTypes = new ArrayList<>();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType<? extends DataType> tagType = copyTagTypes.get(i);
            DataType inner = tagType.getInner();
            if (inner != null)
            {
                ColumnStorage<?> result = DataTypeUtility.makeColumnStorage(inner, null);
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), result.getType().copyReorder((rowIndex, prog) -> {
                    return innerValueIndex.getInt(rowIndex);
                })));
                valueStores.add(result);
            }
            else
            {
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), null));
                valueStores.add(null);
            }
        }
        dataType = DataTypeValue.tagged(typeName, tagTypes, (i, prog) -> {
            if (beforeGet != null)
                beforeGet.beforeGet(this, i, prog);
            return tagStore.getInt(i);
        });
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return dataType;
    }

    @Override
    public SimulationRunnable insertRows(int insertAtIndex, int insertCount) throws InternalException, UserException
    {
        int firstEmptyTag = -1; // Tag with no inner type (preferred)
        int firstNonEmptyTag = -1; // Tag with an inner type
        for (int i = 0; i < tagTypes.size(); i++)
        {
            if (firstEmptyTag == -1 && tagTypes.get(i).getInner() == null)
                firstEmptyTag = i;
            else if (firstNonEmptyTag == -1)
                firstNonEmptyTag = i;
        }

        if (firstEmptyTag != -1)
        {
            // Empty tags are easy:
            tagStore.addAll(insertAtIndex, Utility.<Number>replicate(insertCount, firstEmptyTag));
            innerValueIndex.addAll(insertAtIndex, Utility.<Number>replicate(insertCount, -1));
        }
        else if (firstNonEmptyTag != -1)
        {
            tagStore.addAll(insertAtIndex, Utility.<Number>replicate(insertCount, firstNonEmptyTag));
            int lastInnerValueIndex = -1;
            // Find last used index before us:
            for (int i = 0; i < insertAtIndex; i++)
            {
                if (tagStore.getInt(i) == firstNonEmptyTag)
                    lastInnerValueIndex = innerValueIndex.getInt(i);
            }
            // Add a new bunch:
            List<Number> r = new ArrayList<>(insertCount);
            for (int i = 0; i < insertCount; i++)
            {
                r.add( lastInnerValueIndex + 1 + i);
            }
            innerValueIndex.addAll(insertAtIndex, r);
            @Nullable ColumnStorage<?> valueStore = valueStores.get(firstNonEmptyTag);
            if (valueStore == null)
                throw new InternalException("No value store for inner type tag: " + firstNonEmptyTag);
            valueStore.insertRows(lastInnerValueIndex + 1, insertCount);
            // Increment all after us accordingly:
            for (int i = insertAtIndex + insertCount; i < innerValueIndex.filled(); i++)
            {
                if (tagStore.getInt(i) == firstNonEmptyTag)
                    innerValueIndex.set(OptionalInt.of(i), innerValueIndex.getInt(i) + insertCount);
            }
        }
        else
        {
            throw new InternalException("No tags found in type");
        }

        return () -> removeRows(insertAtIndex, insertCount);
    }

    @Override
    public SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        int[] firstRemovedInnerValueIndex = new int[valueStores.size()];
        int[] lastRemovedInnerValueIndex = new int[valueStores.size()];
        Arrays.fill(firstRemovedInnerValueIndex, -1);
        Arrays.fill(lastRemovedInnerValueIndex, -1);
        // Go through items and remove from innerValue storage:
        for (int i = index; i < index + count; i++)
        {
            int tag = tagStore.getInt(i);
            int valIndex = innerValueIndex.getInt(i);
            if (valIndex != -1)
            {
                if (firstRemovedInnerValueIndex[tag] == -1)
                    firstRemovedInnerValueIndex[tag] = valIndex;
                lastRemovedInnerValueIndex[tag] = valIndex;
            }
        }
        for (int i = index + count; i < filled(); i++)
        {
            int tag = tagStore.getInt(i);
            if (firstRemovedInnerValueIndex[tag] != -1)
            {
                int diff = lastRemovedInnerValueIndex[tag] - firstRemovedInnerValueIndex[tag] + 1;
                // Push all the indexes down:
                innerValueIndex.set(OptionalInt.of(i), innerValueIndex.getInt(i) - diff);
            }
        }
        // Now actually remove everything:
        tagStore.removeRows(index, count);
        innerValueIndex.removeRows(index, count);
        for (int i = 0; i < firstRemovedInnerValueIndex.length; i++)
        {
            if (firstRemovedInnerValueIndex[i] != -1)
            {
                ColumnStorage<?> columnStorage = valueStores.get(i);
                if (columnStorage == null)
                {
                    throw new InternalException("Column storage null yet some indexes point into it: " + i);
                }
                columnStorage.removeRows(firstRemovedInnerValueIndex[i], lastRemovedInnerValueIndex[i] - firstRemovedInnerValueIndex[i] + 1);
            }
        }
        //TODO we could implement this by using get, then insert/set, but we don't have a set!
        return () -> {};
    }

    @Override
    public int filled()
    {
        return tagStore.filled();
    }

    /*
    @Override
    public TaggedValue get(int index) throws InternalException, UserException
    {
        if (index < 0 || index >= filled())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + filled());
        return (TaggedValue) dataType.getCollapsed(index);
    }
    */

    public void addAll(List<TaggedValue> values) throws InternalException
    {
        for (TaggedValue v : values)
            addUnpacked(v);
    }

    protected void addUnpacked(TaggedValue value) throws InternalException
    {
        //Walk the tag structure, adding to store depending on tag:
        int tagIndex = value.getTagIndex();
        tagStore.add(tagIndex);

        @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
        if (inner == null)
        {
            innerValueIndex.add(-1);
            return;
        }
        ColumnStorage storage = valueStores.get(tagIndex);
        if (storage == null)
            throw new InternalException("Value but no store for tag " + tagIndex);
        innerValueIndex.add(storage.filled());
        @Nullable Object innerValue = value.getInner();
        if (innerValue == null)
            throw new InternalException("Inner value despite no inner type");
        storage.add(innerValue);
    }

    public List<TaggedValue> getShrunk(int shrunkLength) throws UserException, InternalException
    {
        List<TaggedValue> s = new ArrayList<>();
        for (int i = 0; i < shrunkLength; i++)
        {
            s.add((TaggedValue)getType().getCollapsed(i));
        }
        return s;
    }
}
