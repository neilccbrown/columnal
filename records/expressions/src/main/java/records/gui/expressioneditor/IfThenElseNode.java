package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.LoadableExpression;
import records.transformations.expression.LoadableExpression.SingleLoader;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 21/02/2017.
 */
public class IfThenElseNode extends DeepNodeTree implements OperandNode<Expression, ExpressionNodeParent>, EEDisplayNodeParent, ErrorDisplayer<Expression>, ExpressionNodeParent
{
    private final ConsecutiveBase<Expression, ExpressionNodeParent> parent;
    private final ExpressionNodeParent semanticParent;
    private final @Interned SubConsecutive condition;
    private final @Interned SubConsecutive thenPart;
    private final @Interned SubConsecutive elsePart;
    private final Pair<ErrorTop, ErrorDisplayer<Expression>> ifLabel;
    private final ErrorTop thenLabel;
    private final ErrorTop elseLabel;

    @SuppressWarnings("initialization") // because of Consecutive
    public IfThenElseNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, @Nullable Expression startingCondition, @Nullable Expression startingThen,  @Nullable Expression startingElse)
    {
        this.parent = parent;
        this.semanticParent = semanticParent;

        ifLabel = ExpressionEditorUtil.<Expression, ExpressionNodeParent>keyword("if", "if-keyword", this, parent.getEditor(), e -> parent.replaceLoad(this, e), getParentStyles());
        thenLabel = ExpressionEditorUtil.<Expression, ExpressionNodeParent>keyword("then", "if-keyword", this, parent.getEditor(), e -> parent.replaceLoad(this, e), getParentStyles()).getFirst();
        elseLabel = ExpressionEditorUtil.<Expression, ExpressionNodeParent>keyword("else", "if-keyword", this, parent.getEditor(), e -> parent.replaceLoad(this, e), getParentStyles()).getFirst();

        condition = new SubConsecutive(ifLabel.getFirst(), "if-condition", startingCondition) {
            @Override
            public OperatorOutcome addOperandToRight(OperatorEntry<Expression, ExpressionNodeParent> rightOf, String operatorEntered, String initialContent, boolean focus)
            {
                boolean lastItem = Utility.indexOfRef(operators, rightOf) == operators.size() - 1;

                if (lastItem && operatorEntered.equals(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN)))
                {
                    thenPart.focus(Focus.LEFT);
                    // If we recognised any special ones, blank the operator:
                    return OperatorOutcome.BLANK;
                }
                else
                {
                    return super.addOperandToRight(rightOf, operatorEntered, initialContent, focus);
                }

            }
        };
        thenPart = new SubConsecutive(thenLabel, "if-then", startingThen) {
            @Override
            public OperatorOutcome addOperandToRight(OperatorEntry<Expression, ExpressionNodeParent> rightOf, String operatorEntered, String initialContent, boolean focus)
            {
                boolean lastItem = Utility.indexOfRef(operators, rightOf) == operators.size() - 1;

                if (lastItem && operatorEntered.equals(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE)))
                {
                    elsePart.focus(Focus.LEFT);
                    // If we recognised any special ones, blank the operator:
                    return OperatorOutcome.BLANK;
                }
                else
                {
                    return super.addOperandToRight(rightOf, operatorEntered, initialContent, focus);
                }

            }
        };
        elsePart = new SubConsecutive(elseLabel, "if-else", startingElse) {
            @Override
            public ImmutableSet<Character> terminatedByChars()
            {
                return ImmutableSet.of(')');
            }
        };

        updateNodes();
        updateListeners();
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Stream.of(condition, thenPart, elsePart);
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(condition, thenPart, elsePart).flatMap(n -> n.nodes().stream());
    }

    @Override
    protected void updateDisplay()
    {

    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return parent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        //TODO
    }

    @Override
    public <C extends LoadableExpression<C, ?>> Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> startDist = ConsecutiveChild.closestDropSingle(this, Expression.class, ifLabel.getFirst(), loc, forType);

        return Utility.streamNullable(startDist, condition.findClosestDrop(loc, forType), thenPart.findClosestDrop(loc, forType), elsePart.findClosestDrop(loc, forType))
            .filter(x -> x != null).min(Comparator.comparing(p -> p.getSecond())).get();
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        //TODO
    }

    @Override
    public void focusChanged()
    {
        condition.focusChanged();
        thenPart.focusChanged();
        elsePart.focusChanged();
    }

    @Override
    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return Arrays.asList(
            ifLabel.getFirst()._test_getHeaderState(),
            condition._test_getHeaders(),
            thenLabel._test_getHeaderState(),
            thenPart._test_getHeaders(),
            elseLabel._test_getHeaderState(),
            elsePart._test_getHeaders()
        ).stream().flatMap(s -> s);
    }

    @Override
    public void focus(Focus side)
    {
        if (side == Focus.LEFT)
            condition.focus(Focus.LEFT);
        else
            elsePart.focus(Focus.RIGHT);
    }

    @Override
    public void prompt(String prompt)
    {
        // Not applicable
    }

    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.record(this, new IfThenElseExpression(condition.save(errorDisplayer, onError), thenPart.save(errorDisplayer, onError), elsePart.save(errorDisplayer, onError)));
    }

    @Override
    public void focusWhenShown()
    {
        condition.focusWhenShown();
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child || condition.isOrContains(child) || thenPart.isOrContains(child) || elsePart.isOrContains(child);
    }

    @Override
    public void cleanup()
    {
        condition.cleanup();
        thenPart.cleanup();
        elsePart.cleanup();
    }

    @Override
    @SuppressWarnings("nullness") // Because we return non-null item
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return new ReadOnlyStringWrapper("if-inner");
    }

    @Override
    public boolean isFocused()
    {
        return condition.childIsFocused() || thenPart.childIsFocused() || elsePart.childIsFocused();
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        if (child == condition)
            return Collections.singletonList(new Pair<>(DataType.BOOLEAN, Collections.emptyList()));
        else
            return Collections.emptyList(); // TODO: could infer from it other branch
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
    {
        return semanticParent.getAvailableVariables(this);
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode chid)
    {
        return false;
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Focus side)
    {
        if (child == condition)
            thenPart.focus(side);
        else if (child == thenPart)
            elsePart.focus(side);
        else
            parent.focusRightOf(this, side);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == condition)
            parent.focusLeftOf(this);
        else if (child == thenPart)
            condition.focus(Focus.RIGHT);
        else
            thenPart.focus(Focus.RIGHT);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.<String>concat(parent.getParentStyles(), Stream.of("if-parent"));
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<ErrorAndTypeRecorder.QuickFix<Expression>> quickFixes)
    {
        condition.addErrorAndFixes(error, quickFixes);
    }

    @Override
    public void clearAllErrors()
    {
        condition.clearAllErrors();
        thenPart.clearAllErrors();
        elsePart.clearAllErrors();
    }

    @Override
    public boolean isShowingError()
    {
        return ifLabel.getSecond().isShowingError();
    }

    @Override
    public void showType(String type)
    {
        ifLabel.getSecond().showType(type);
    }

    @Override
    public ImmutableList<Pair<String, @Localized String>> operatorKeywords()
    {
        return ImmutableList.of(
            opD(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.THEN), "op.then"),
            opD(Utility.literal(ExpressionLexer.VOCABULARY, ExpressionLexer.ELSE), "op.else"));
    }

    private class SubConsecutive extends Consecutive<Expression, ExpressionNodeParent>
    {
        public SubConsecutive(Node label, String style, @Nullable Expression startingContent)
        {
            super(ConsecutiveBase.EXPRESSION_OPS, IfThenElseNode.this, label, null, style, startingContent == null ? null : SingleLoader.withSemanticParent(startingContent.loadAsConsecutive(false), IfThenElseNode.this));
        }

        @Override
        protected ExpressionNodeParent getThisAsSemanticParent()
        {
            return IfThenElseNode.this;
        }

        @Override
        public boolean isFocused()
        {
            return childIsFocused();
        }

        @Override
        protected boolean hasImplicitRoundBrackets()
        {
            return false;
        }

        public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
        {
            return errorDisplayer.record(this, saveUnrecorded(errorDisplayer, onError));
        }
    }
}
