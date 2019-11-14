package test;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.EditableRecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import test.gen.GenRandom;
import test.gen.type.GenTypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;

import javax.swing.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by neil on 05/06/2017.
 */
@RunWith(JUnitQuickcheck.class)
public class PropStorageSet
{
    @Property(trials = 100)
    @OnThread(Tag.Simulation)
    public void testSet(@From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen typeAndValueGen, @From(GenRandom.class) Random r) throws UserException, InternalException, Exception
    {
        // Make sure FX is initialised:
        SwingUtilities.invokeAndWait(() -> new JFXPanel());
        Platform.runLater(() -> {});
        
        @SuppressWarnings({"keyfor", "units"})
        EditableRecordSet recordSet = new EditableRecordSet(Collections.singletonList(rs -> typeAndValueGen.getType().makeImmediateColumn(new ColumnId("C"), Collections.emptyList(), typeAndValueGen.makeValue()).apply(rs)), () -> 0);
        Column c = recordSet.getColumns().get(0);
        assertEquals(0, c.getLength());
        int length = 20;
        recordSet.insertRows(0, length);
        
        HashMap<Integer, Either<String, @Value Object>> vals = new HashMap<>();
        for (int i = 0; i < length; i++)
        {
            @Value Object defaultValue = c.getDefaultValue();
            vals.put(i, defaultValue == null ? Either.left("") : Either.right(defaultValue));
        }

        // Do many writes:
        for (int i = 0; i < 100; i++)
        {
            assertEquals(length, c.getLength());
            assertEquals(length, recordSet.getLength());
            
            int rowIndex = r.nextInt(length);
            // Sometimes, add rows and/or remove again:
            if (r.nextInt(4) == 1)
            {
                boolean willRevert = r.nextBoolean();
                SimulationRunnable revert;
                // Insert if small or 2/3 chance:
                if (length < 10 || r.nextInt(3) == 1)
                {
                    // Insert rows:
                    int count = r.nextInt(10);
                    if (!willRevert)
                    {
                        vals = mapKeys(vals, k -> k < rowIndex ? k : k + count);
                        for (int newItem = 0; newItem < count; newItem++)
                        {
                            @Value Object defaultValue = c.getDefaultValue();
                            assertNotNull(defaultValue);
                            if (defaultValue != null)
                                vals.put(rowIndex + newItem, Either.<String, @Value Object>right(defaultValue));
                        }
                        length += count;
                    }
                    revert = recordSet.insertRows(rowIndex, count);
                    //Log.debug("Inserted " + count + " at " + rowIndex + " revert:  " + willRevert);
                }
                else
                {
                    // Remove rows:
                    int count = Math.min(length - rowIndex, r.nextInt(10));
                    revert = recordSet.removeRows(rowIndex, count);
                    if (!willRevert)
                    {
                        // Re map errors:
                        vals = mapKeys(vals, k -> k >= rowIndex ? (k < rowIndex + count ? null : k - count) : (Integer)k);
                        length -= count;
                    }

                    //Log.debug("Removed " + count + " at " + rowIndex + " revert:  " + willRevert);
                }
                
                // Half the time, immediately revert
                if (willRevert)
                {
                    assertNotNull(revert);
                    if (revert != null)
                        revert.run();
                }
            }
            else
            {
                @Value Object value = typeAndValueGen.makeValue();
                DataTypeValue columnType = c.getType();
                Either<String, @Value Object> valueOrErr = r.nextInt(5) == 1 ? Either.left(("Err " + i)) : Either.right(value);
                columnType.setCollapsed(rowIndex, valueOrErr);
                DataTestUtil.assertValueEitherEqual("Type: " + typeAndValueGen.getType() + " index " + rowIndex, valueOrErr, collapseErr(c.getType(), rowIndex));
                vals.put(rowIndex, valueOrErr);
                
                //Log.debug("Set value at " + rowIndex);
            }
            checkAll(typeAndValueGen.getType(), c, vals);
        }

        assertEquals(length, c.getLength());
    }

    @OnThread(Tag.Simulation)
    private void checkAll(DataType dataType, Column c, HashMap<Integer, Either<String, @Value Object>> vals) throws UserException, InternalException
    {
        for (Entry<@KeyFor("vals") Integer, Either<String, @Value Object>> entry : vals.entrySet())
        {
            DataTestUtil.assertValueEitherEqual("Type: " + dataType + " index " + entry.getKey(), entry.getValue(), collapseErr(c.getType(), entry.getKey()));
        }
    }

    @OnThread(Tag.Simulation)
    private Either<String, @Value Object> collapseErr(DataTypeValue type, int rowIndex) throws UserException, InternalException
    {
        try
        {
            return Either.right(type.getCollapsed(rowIndex));
        }
        catch (InvalidImmediateValueException e)
        {
            return Either.left(e.getInvalid());
        }
    }
    
    private static HashMap<Integer, Either<String, @Value Object>> mapKeys(HashMap<Integer, Either<String, @Value Object>> prev, IntFunction<@Nullable Integer> mapper)
    {
        HashMap<Integer, Either<String, @Value Object>> r = new HashMap<>();
        prev.forEach((k, v) -> {
            Integer mapped = mapper.apply(k);
            if (mapped != null)
                r.put(mapped, v);
        });
        return r;
    }
}
