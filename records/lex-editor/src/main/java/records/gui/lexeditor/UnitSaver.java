package records.gui.lexeditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.input.DataFormat;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
import records.gui.expressioneditor.UnitEntry.UnitOp;
import records.gui.lexeditor.EditorLocationAndErrorRecorder.Span;
import records.gui.lexeditor.UnitSaver.Context;
import records.transformations.expression.InvalidOperatorUnitExpression;
import records.transformations.expression.InvalidSingleUnitExpression;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitDivideExpression;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitExpressionIntLiteral;
import records.transformations.expression.UnitRaiseExpression;
import records.transformations.expression.UnitTimesExpression;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class UnitSaver extends SaverBase<UnitExpression, UnitSaver, UnitOp, UnitBracket, Context, Void>// implements ErrorAndTypeRecorder
{
    public static final DataFormat UNIT_CLIPBOARD_TYPE = FXUtility.getDataFormat("application/records-type");
    
    final ImmutableList<OperatorExpressionInfo> OPERATORS = ImmutableList.of(
        new OperatorExpressionInfo(ImmutableList.of(
            opD(UnitOp.MULTIPLY, "op.times")), UnitSaver::makeTimes),
        new OperatorExpressionInfo(
            opD(UnitOp.DIVIDE, "op.divide"), UnitSaver::makeDivide),
        new OperatorExpressionInfo(
            opD(UnitOp.RAISE, "op.raise"), UnitSaver::makeRaise));

    public UnitSaver()
    {
        super();
    }
    
    
    private static UnitExpression makeTimes(ImmutableList<@Recorded UnitExpression> expressions, List<Pair<UnitOp, Span>> operators)
    {
        return new UnitTimesExpression(expressions);
    }

    private static UnitExpression makeDivide(@Recorded UnitExpression lhs, Span opNode, @Recorded UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver, ?> bracketedStatus, EditorLocationAndErrorRecorder locationRecorder)
    {
        return new UnitDivideExpression(lhs, rhs);
    }

    private static UnitExpression makeRaise(@Recorded UnitExpression lhs, Span opNode, @Recorded UnitExpression rhs, BracketAndNodes<UnitExpression, UnitSaver, ?> bracketedStatus, EditorLocationAndErrorRecorder locationRecorder)
    {
        if (rhs instanceof UnitExpressionIntLiteral)
            return new UnitRaiseExpression(lhs, ((UnitExpressionIntLiteral) rhs).getNumber());
        else
            return new InvalidOperatorUnitExpression(ImmutableList.<@Recorded UnitExpression>of(
                    lhs, locationRecorder.<InvalidSingleUnitExpression>recordUnit(opNode, new InvalidSingleUnitExpression("^")), rhs
            ));
    };

    @Override
    public BracketAndNodes<UnitExpression, UnitSaver, Void> expectSingle(@UnknownInitialization(Object.class)UnitSaver this, EditorLocationAndErrorRecorder locationRecorder, Span location)
    {
        return new BracketAndNodes<>(new ApplyBrackets<Void, UnitExpression>()
        {
            @Override
            public @Nullable @Recorded UnitExpression apply(@NonNull Void items)
            {
                // Should not be possible anyway
                throw new IllegalStateException();
            }

            @Override
            public @NonNull @Recorded UnitExpression applySingle(@NonNull @Recorded UnitExpression singleItem)
            {
                return singleItem;
            }
        }, location, ImmutableList.of());
    }

    //UnitManager getUnitManager();

    class Context {}
    
    @Override
    protected @Recorded UnitExpression makeExpression(Span location, List<Either<@Recorded UnitExpression, OpAndNode>> content, BracketAndNodes<UnitExpression, UnitSaver, Void> brackets)
    {
        if (content.isEmpty())
            return record(location, new InvalidOperatorUnitExpression(ImmutableList.of()));

        CollectedItems collectedItems = processItems(content);

        if (collectedItems.isValid())
        {
            ArrayList<@Recorded UnitExpression> validOperands = collectedItems.getValidOperands();
            ArrayList<OpAndNode> validOperators = collectedItems.getValidOperators();
            
            // Single expression?
            if (validOperands.size() == 1 && validOperators.size() == 0)
                return validOperands.get(0);

            // Raise is a special case as it doesn't need to be bracketed:
            for (int i = 0; i < validOperators.size(); i++)
            {
                if (validOperators.get(i).op.equals(UnitOp.RAISE))
                {
                    if (validOperands.get(i) instanceof SingleUnitExpression && i + 1 < validOperands.size() && validOperands.get(i + 1) instanceof UnitExpressionIntLiteral)
                    {
                        validOperators.remove(i);
                        @Recorded UnitExpressionIntLiteral power = (UnitExpressionIntLiteral) validOperands.remove(i + 1);
                        Span recorder = locationRecorder.recorderFor(validOperands.get(i));
                        validOperands.set(i, record(Span.fromTo(recorder, locationRecorder.recorderFor(power)), new UnitRaiseExpression(validOperands.get(i), power.getNumber())));
                    }
                }
            }
            
            // Now we need to check the operators can work together as one group:
            @Nullable UnitExpression e = makeExpressionWithOperators(ImmutableList.of(OPERATORS), locationRecorder, (ImmutableList<Either<OpAndNode, @Recorded UnitExpression>> arg) ->
                    makeInvalidOp(brackets.location, arg)
                , ImmutableList.copyOf(validOperands), ImmutableList.copyOf(validOperators), brackets);
            if (e != null)
            {
                return record(location, e);
            }

        }

        return collectedItems.makeInvalid(location, InvalidOperatorUnitExpression::new);
    }

    @Override
    protected UnitExpression opToInvalid(UnitOp unitOp)
    {
        return new InvalidSingleUnitExpression(unitOp.getContent());
    }

    @Override
    protected @Nullable Supplier<@Recorded UnitExpression> canBeUnary(OpAndNode operator, UnitExpression followingOperand)
    {
        return null;
    }

    @Override
    protected @Recorded UnitExpression makeInvalidOp(Span location, ImmutableList<Either<OpAndNode, @Recorded UnitExpression>> items)
    {
        return locationRecorder.recordUnit(location, new InvalidOperatorUnitExpression(Utility.<Either<OpAndNode, @Recorded UnitExpression>, @Recorded UnitExpression>mapListI(items, x -> x.<@Recorded UnitExpression>either(op -> locationRecorder.recordUnit(op.sourceNode, new InvalidSingleUnitExpression(op.op.getContent())), y -> y))));
    }

    private static Pair<UnitOp, @Localized String> opD(UnitOp op, @LocalizableKey String key)
    {
        return new Pair<>(op, TranslationUtility.getString(key));
    }

    public void saveBracket(UnitBracket bracket, Span errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (bracket == UnitBracket.OPEN_ROUND)
        {
            currentScopes.push(new Scope(errorDisplayer, new Terminator()
            {
                @Override
                public void terminate(FetchContent<UnitExpression, UnitSaver, Void> makeContent, @Nullable UnitBracket terminator, Span keywordErrorDisplayer, FXPlatformConsumer<Context> keywordContext)
                {
                    BracketAndNodes<UnitExpression, UnitSaver, Void> brackets = expectSingle(locationRecorder, Span.fromTo(errorDisplayer, keywordErrorDisplayer));
                    if (terminator == UnitBracket.CLOSE_ROUND)
                    {
                        // All is well:
                        @Recorded UnitExpression result = makeContent.fetchContent(brackets);
                        currentScopes.peek().items.add(Either.left(result));
                    } 
                    else
                    {
                        // Error!
                        locationRecorder.addErrorAndFixes(keywordErrorDisplayer, StyledString.s("Expected ) but found " + terminator), ImmutableList.of());
                        // Important to call makeContent before adding to scope on the next line:
                        ImmutableList.Builder<@Recorded UnitExpression> items = ImmutableList.builder();
                        items.add(record(errorDisplayer, new InvalidSingleUnitExpression(bracket.getContent())));
                        items.add(makeContent.fetchContent(brackets));
                        if (terminator != null)
                            items.add(record(errorDisplayer, new InvalidSingleUnitExpression(terminator.getContent())));
                        @Recorded UnitExpression invalid = record(Span.fromTo(brackets.location, keywordErrorDisplayer), new InvalidOperatorUnitExpression(items.build()));
                        currentScopes.peek().items.add(Either.left(invalid));
                    }
                }
            }));
        }
        else
        {
            Scope cur = currentScopes.pop();
            if (currentScopes.size() == 0)
            {
                addTopLevelScope();
            }
            cur.terminator.terminate((BracketAndNodes<UnitExpression, UnitSaver, Void> brackets) -> makeExpression(brackets.location, cur.items, brackets), bracket, errorDisplayer, withContext);
        }
    }

    @Override
    protected UnitExpression keywordToInvalid(UnitBracket unitBracket)
    {
        return new InvalidSingleUnitExpression(unitBracket.getContent());
    }

    @Override
    protected Span recorderFor(@Recorded UnitExpression unitExpression)
    {
        return locationRecorder.recorderFor(unitExpression);
    }

    @Override
    protected @Recorded UnitExpression record(Span location, UnitExpression unitExpression)
    {
        return locationRecorder.recordUnit(location, unitExpression);
    }

    public static ImmutableList<OperatorExpressionInfo> getOperators()
    {
        return new UnitSaver().OPERATORS;
    }

    @Override
    protected Map<DataFormat, Object> toClipboard(@UnknownIfRecorded UnitExpression expression)
    {
        return ImmutableMap.of(
                UNIT_CLIPBOARD_TYPE, expression.save(true, true),
                DataFormat.PLAIN_TEXT, expression.save(false, true)
        );
    }

    @Override
    protected BracketAndNodes<UnitExpression, UnitSaver, Void> unclosedBrackets(BracketAndNodes<UnitExpression, UnitSaver, Void> closed)
    {
        return new BracketAndNodes<UnitExpression, UnitSaver, Void>(new ApplyBrackets<Void, UnitExpression>()
        {
            @Nullable
            @Override
            public @Recorded UnitExpression apply(@NonNull Void items)
            {
                // Can't happen
                throw new IllegalStateException();
            }

            @NonNull
            @Override
            public @Recorded UnitExpression applySingle(@NonNull @Recorded UnitExpression singleItem)
            {
                return singleItem;
            }
        }, closed.location, ImmutableList.of(closed.applyBrackets));
    }
}