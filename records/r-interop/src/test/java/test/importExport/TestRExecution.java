package test.importExport;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData;
import records.rinterop.RData.RValue;
import records.rinterop.RExecution;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("valuetype")
@RunWith(JUnitQuickcheck.class)
public class TestRExecution
{
    @Test
    public void testSimple() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = RData.convertRToTable(typeManager, RExecution.runRExpression("c(6, 8)")).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(6, 8), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @Test
    public void testSimple2() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = RData.convertRToTable(typeManager, RExecution.runRExpression("seq(1,10,2)")).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(1, 3, 5, 7, 9), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @Test
    public void testAIC() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = RData.convertRToTable(typeManager, RExecution.runRExpression(
        // From docs
                "lm1 <- lm(Fertility ~ . , data = swiss)\n" +
                "AIC(lm1)\n" +
                "stopifnot(all.equal(AIC(lm1),\n" +
                "                    AIC(logLik(lm1))))\n" +
                "BIC(lm1)\n" +
                "\n" +
                "lm2 <- update(lm1, . ~ . -Examination)\n" +
                "AIC(lm1, lm2)"
        , ImmutableList.of("stats"), ImmutableMap.of())).get(0).getSecond();
        assertEquals(ImmutableList.of(new ColumnId("df"), new ColumnId("AIC")), recordSet.getColumnIds());
        assertEquals(ImmutableList.of(new BigDecimal("326.07156844054867406157427467405796051025390625"), new BigDecimal("325.2408440639818536510574631392955780029296875")), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumn(new ColumnId("AIC")).getType(), recordSet.getLength()));
    }

    @Test
    public void testTable() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = RData.convertRToTable(typeManager, RExecution.runRExpression("foo$bar[2:3]", ImmutableList.of(), ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(rs -> new MemoryNumericColumn(rs, new ColumnId("bar"), NumberInfo.DEFAULT, Stream.of("3", "4", "5"))), 3)))).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(4, 5), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    @SuppressWarnings("valuetype")
    @Test
    public void testTable2() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = RData.convertRToTable(typeManager, RExecution.runRExpression("foo$baz[2:3]", ImmutableList.of(),
            ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                rs -> new MemoryNumericColumn(rs, new ColumnId("bar"), NumberInfo.DEFAULT, Stream.of("3", "4", "5")),
                rs -> new MemoryStringColumn(rs, new ColumnId("baz"), ImmutableList.of(Either.<String, @Value String>right("A"), Either.<String, @Value String>right("B"), Either.<String, @Value String>right("C")), "Z")
            ), 3)))).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of("B", "C"), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    @SuppressWarnings("valuetype")
    @Test
    public void testTable2b() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = RData.convertRToTable(typeManager, RExecution.runRExpression("data.frame(foo)$baz[2:3]", ImmutableList.of(),
            ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                rs -> new MemoryNumericColumn(rs, new ColumnId("bar"), NumberInfo.DEFAULT, Stream.of("3", "4", "5")),
                rs -> new MemoryStringColumn(rs, new ColumnId("baz"), ImmutableList.of(Either.<String, @Value String>right("A"), Either.<String, @Value String>right("B"), Either.<String, @Value String>right("C")), "Z")
            ), 3)))).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of("B", "C"), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    @Test
    public void testCO2() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RValue rValue = RExecution.runRExpression("data.frame(CO2)");
        System.out.println(RData.prettyPrint(rValue));
        RecordSet recordSet = RData.convertRToTable(typeManager, rValue).get(0).getSecond();
        TaggedValue taggedValue = Utility.cast(recordSet.getColumn(new ColumnId("Plant")).getType().getCollapsed(0), TaggedValue.class);
        assertEquals("Qn1", taggedValue.getTagName());
    }
}