package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.transformations.expression.visitor.ExpressionVisitor;
import xyz.columnal.typeExp.NumTypeExp;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.typeExp.units.UnitExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.Optional;
import java.util.Random;

/**
 * Created by neil on 10/12/2016.
 */
public class DivideExpression extends BinaryOpExpression
{
    public DivideExpression(@Recorded Expression lhs, @Recorded Expression rhs)
    {
        super(lhs, rhs);
    }

    @Override
    protected String saveOp()
    {
        return "/";
    }

    @Override
    public BinaryOpExpression copy(@Nullable @Recorded Expression replaceLHS, @Nullable @Recorded Expression replaceRHS)
    {
        return new DivideExpression(replaceLHS == null ? lhs : replaceLHS, replaceRHS == null ? rhs : replaceRHS);
    }

    @Override
    @RequiresNonNull({"lhsType", "rhsType"})
    protected @Nullable CheckedExp checkBinaryOp(@Recorded DivideExpression this, ColumnLookup data, TypeState state, ExpressionKind kind, ErrorAndTypeRecorder onError) throws UserException, InternalException
    {
        @NonNull TypeExp lhsTypeFinal = lhsType.typeExp;
        @NonNull TypeExp rhsTypeFinal = rhsType.typeExp;
        UnitExp topUnit = UnitExp.makeVariable();
        UnitExp bottomUnit = UnitExp.makeVariable();
        // You can divide any number by any other number
        if (onError.recordError(this, TypeExp.unifyTypes(new NumTypeExp(this, topUnit), lhsTypeFinal)) == null
            || onError.recordError(this, TypeExp.unifyTypes(new NumTypeExp(this, bottomUnit), rhsTypeFinal)) == null)
        {
            return null;
        }
        return new CheckedExp(onError.recordTypeNN(this, new NumTypeExp(this, topUnit.divideBy(bottomUnit))), state);
    }

    @Override
    protected Pair<ExpressionKind, ExpressionKind> getOperandKinds()
    {
        return new Pair<>(ExpressionKind.EXPRESSION, ExpressionKind.EXPRESSION);
    }

    @Override
    @OnThread(Tag.Simulation)
    public @Value Object getValueBinaryOp(ValueResult lhsValue, ValueResult rhsValue) throws UserException, InternalException
    {
        return Utility.divideNumbers(Utility.cast(lhsValue.value, Number.class), Utility.cast(rhsValue.value, Number.class));
    }

    @Override
    public Optional<Rational> constantFold()
    {
        Optional<Rational> l = lhs.constantFold();
        Optional<Rational> r = rhs.constantFold();
        if (l.isPresent() && r.isPresent())
            return Optional.of(l.get().divides(r.get()));
        else
            return Optional.empty();
    }

    @SuppressWarnings("recorded")
    @Override
    public Expression _test_typeFailure(Random r, _test_TypeVary newExpressionOfDifferentType, UnitManager unitManager) throws UserException, InternalException
    {
        if (r.nextBoolean())
        {
            return copy(newExpressionOfDifferentType.getNonNumericType(), rhs);
        }
        else
        {
            return copy(lhs, newExpressionOfDifferentType.getNonNumericType());
        }
    }

    @Override
    protected LocationInfo argLocationInfo()
    {
        return LocationInfo.UNIT_MODIFYING;
    }

    @Override
    public <T> T visit(ExpressionVisitor<T> visitor)
    {
        return visitor.divide(this, lhs, rhs);
    }
}
