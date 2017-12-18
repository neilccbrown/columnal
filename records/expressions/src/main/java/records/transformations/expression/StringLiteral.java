package records.transformations.expression;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.StringLiteralNode;
import records.loadsave.OutputBuilder;
import records.types.TypeExp;
import utility.Pair;

import java.math.BigInteger;
import java.util.Map;

/**
 * Created by neil on 25/11/2016.
 */
public class StringLiteral extends Literal
{
    private final @Value String value;

    public StringLiteral(String value)
    {
        this.value = DataTypeUtility.value(value);
    }

    @Override
    public TypeExp check(RecordSet data, TypeState state, ErrorRecorder onError) throws InternalException
    {
        return TypeExp.fromConcrete(this, DataType.TEXT);
    }

    @Override
    public @Value Object getValue(int rowIndex, EvaluateState state) throws UserException, InternalException
    {
        return value;
    }

    @Override
    public String save(boolean topLevel)
    {
        return OutputBuilder.quoted(value);
    }

    @Override
    public Formula toSolver(FormulaManager formulaManager, RecordSet src, Map<Pair<@Nullable TableId, ColumnId>, Formula> columnVariables)
    {
        BigInteger[] bits = new BigInteger[] {new BigInteger("0")};
        value.codePoints().forEach(c -> {
            bits[0] = bits[0].shiftLeft(32);
            bits[0] = bits[0].or(BigInteger.valueOf(c));
        });
        return formulaManager.getBitvectorFormulaManager().makeBitvector((int)(MAX_STRING_SOLVER_LENGTH * 32), bits[0]);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StringLiteral that = (StringLiteral) o;

        return value.equals(that.value);
    }

    @Override
    public int hashCode()
    {
        return value.hashCode();
    }

    @Override
    public SingleLoader<OperandNode<Expression>> loadAsSingle()
    {
        return (p, s) -> new StringLiteralNode(editString(), p);
    }

    @Override
    protected String editString()
    {
        return value;
    }

    public String getRawString()
    {
        return value;
    }
}
