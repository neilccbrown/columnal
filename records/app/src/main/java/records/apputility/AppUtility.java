package records.apputility;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.*;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.expression.type.TypeExpression;
import records.transformations.function.core.AsType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;

import java.util.Map.Entry;

public class AppUtility
{
    @OnThread(Tag.FXPlatform)
    public static Expression valueToExpressionFX(TypeManager typeManager, FunctionLookup functionLookup, DataType dataType, @ImmediateValue Object value) throws UserException, InternalException
    {
        return Utility.launderSimulationEx(() -> valueToExpression(typeManager, functionLookup, dataType, value));
    }
    
    @OnThread(Tag.Simulation)
    @SuppressWarnings("recorded")
    public static Expression valueToExpression(TypeManager typeManager, FunctionLookup functionLookup, DataType dataType, @Value Object value) throws UserException, InternalException
    {
        return dataType.apply(new DataTypeVisitor<Expression>()
        {
            @Override
            public Expression number(NumberInfo numberInfo) throws InternalException, UserException
            {
                return new NumericLiteral(Utility.cast(value, Number.class), numberInfo.getUnit().equals(Unit.SCALAR) ? null : UnitExpression.load(numberInfo.getUnit()));
            }

            @Override
            public Expression text() throws InternalException, UserException
            {
                return new StringLiteral(Utility.cast(value, String.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return new TemporalLiteral(dateTimeInfo.getType(), DataTypeUtility.valueToString(dataType, value, null, false));
            }

            @Override
            public Expression bool() throws InternalException, UserException
            {
                return new BooleanLiteral(Utility.cast(value, Boolean.class));
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                TaggedValue taggedValue = Utility.cast(value, TaggedValue.class);
                TagType<DataType> tag = tags.get(taggedValue.getTagIndex());
                ConstructorExpression constructor = new ConstructorExpression(typeManager, typeName.getRaw(), tag.getName());
                @Value Object innerValue = taggedValue.getInner();
                DataType innerType = tag.getInner();
                Expression expression = innerValue == null || innerType == null ? constructor : new CallExpression(constructor, ImmutableList.of(valueToExpression(typeManager, functionLookup, innerType, innerValue)));
                if (typeVars.isEmpty())
                    return expression;
                else
                {
                    DataType lookedUp = typeManager.lookupType(typeName, typeVars);
                    if (lookedUp == null)
                        throw new UserException("Could not find type: " + dataType);
                    return asType(lookedUp, expression);
                }
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                Record record = Utility.cast(value, Record.class);
                ImmutableList.Builder<Pair<@ExpressionIdentifier String, Expression>> members = ImmutableList.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, DataType> field : fields.entrySet())
                {
                    members.add(new Pair<>(field.getKey(), valueToExpression(typeManager, functionLookup, field.getValue(), record.getField(field.getKey()))));
                }
                return new RecordExpression(members.build());
            }

            @Override
            @OnThread(Tag.Simulation)
            public Expression array(DataType inner) throws InternalException, UserException
            {
                ListEx listEx = Utility.cast(value, ListEx.class);
                int size = listEx.size();
                ImmutableList.Builder<Expression> members = ImmutableList.builderWithExpectedSize(size);

                for (int i = 0; i < size; i++)
                {
                    members.add(valueToExpression(typeManager, functionLookup, inner, listEx.get(i)));
                }
                
                if (size == 0)
                    return asType(inner, new ArrayExpression(members.build()));
                else
                    return new ArrayExpression(members.build());
            }

            private CallExpression asType(DataType inner, Expression expression) throws InternalException
            {
                return new CallExpression(functionLookup, AsType.NAME, new TypeLiteralExpression(TypeExpression.fromDataType(DataType.array(inner))), expression);
            }
        });
    }
}