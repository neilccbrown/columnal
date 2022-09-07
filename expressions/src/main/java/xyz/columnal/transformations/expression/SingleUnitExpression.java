package xyz.columnal.transformations.expression;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.Scene;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.UnitDeclaration;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.QuickFix.QuickFixAction;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationConsumer;
import xyz.columnal.utility.Utility;

import java.util.stream.Stream;

// Same distinction as IdentExpression/InvalidIdentExpression
public class SingleUnitExpression extends UnitExpression
{
    private final @UnitIdentifier String name;

    public SingleUnitExpression(@UnitIdentifier String text)
    {
        this.name = text;
    }

    @Override
    public JellyUnit asUnit(@Recorded SingleUnitExpression this, UnitManager unitManager) throws UnitLookupException
    {
        try
        {
            return JellyUnit.fromConcrete(unitManager.loadUse(name));
        }
        catch (InternalException | UserException e)
        {
            Stream<QuickFix<UnitExpression>> similarNames = Utility.findAlternatives(name, unitManager.getAllDeclared().stream(), su -> Stream.of(su.getName(), su.getDescription()))
                .<QuickFix<UnitExpression>>map(su -> new QuickFix<UnitExpression>(StyledString.s("Correct"), ImmutableList.of(), this, () -> new SingleUnitExpression(su.getName())));
            QuickFix<UnitExpression> makeNew = new QuickFix<UnitExpression>(StyledString.s("Create unit \"" + name + "\""), ImmutableList.<String>of(), this, new QuickFixAction()
            {
                @Override
                public @OnThread(Tag.FXPlatform) @Nullable SimulationConsumer<Pair<@Nullable ColumnId, Expression>> doAction(TypeManager typeManager, ObjectExpression<Scene> editorSceneProperty)
                {
                    typeManager.getUnitManager().addUserUnit(new Pair<>(name, Either.<@UnitIdentifier String, UnitDeclaration>right(new UnitDeclaration(new SingleUnit(name, "", "", ""), null, ""))));
                    return null;
                }
            });
            ImmutableList<QuickFix<UnitExpression>> fixes = Stream.<QuickFix<UnitExpression>>concat(similarNames, Stream.<QuickFix<UnitExpression>>of(makeNew)).collect(ImmutableList.<QuickFix<UnitExpression>>toImmutableList());
            throw new UnitLookupException(StyledString.s(e.getLocalizedMessage()), this, fixes);
        }
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        return name;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SingleUnitExpression that = (SingleUnitExpression) o;

        return name.equals(that.name);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}