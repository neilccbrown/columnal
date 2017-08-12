package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Chars;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 19/02/2017.
 */
public abstract class Consecutive<EXPRESSION extends @NonNull Object, SEMANTIC_PARENT> extends ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT>
{
    protected final EEDisplayNodeParent parent;
    private final ImmutableSet<Character> endCharacters;

    @SuppressWarnings("initialization") // Because of loading
    public Consecutive(OperandOps<EXPRESSION, SEMANTIC_PARENT> operations, EEDisplayNodeParent parent, @Nullable Node prefixNode, @Nullable Node suffixNode, String style, @Nullable Pair<List<FXPlatformFunction<ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT>, OperandNode<EXPRESSION>>>, List<FXPlatformFunction<ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT>, OperatorEntry<EXPRESSION, SEMANTIC_PARENT>>>> content, char... endCharacters)
    {
        super(operations, prefixNode, suffixNode, style);
        this.parent = parent;
        this.endCharacters = ImmutableSet.copyOf(Chars.asList(endCharacters));
        if (content != null)
        {
            atomicEdit.set(true);
            operands.addAll(Utility.mapList(content.getFirst(), f -> f.apply(this)));
            operators.addAll(Utility.mapList(content.getSecond(), f -> f.apply(this)));
            atomicEdit.set(false);
            // Get rid of anything which would go if you got focus and lost it again:
            focusChanged();
        }
        else
        {
            atomicEdit.set(true);
            operators.add(makeBlankOperator());
            operands.add(makeBlankOperand());
            atomicEdit.set(false);
        }
    }

    protected void selfChanged(@UnknownInitialization(ConsecutiveBase.class) Consecutive<EXPRESSION, SEMANTIC_PARENT> this)
    {
        if (parent != null)
            parent.changed(this);
    }

    @Override
    protected void parentFocusRightOfThis()
    {
        parent.focusRightOf(this);
    }

    @Override
    protected void parentFocusLeftOfThis()
    {
        parent.focusLeftOf(this);
    }

    @Override
    protected boolean isMatchNode()
    {
        return parent instanceof ClauseNode && ((ClauseNode)parent).isMatchNode(this);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.<String>concat(parent.getParentStyles(), Stream.<String>of(style));
    }

    @Override
    public ExpressionEditor getEditor()
    {
        return parent.getEditor();
    }

    @Override
    public ImmutableSet<Character> terminatedByChars()
    {
        return endCharacters;
    }
}
