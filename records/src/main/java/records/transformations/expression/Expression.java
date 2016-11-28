package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.FormulaManager;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableId;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.DataTypeValue.SpecificDataTypeVisitorGet;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.grammar.ExpressionParser.BinaryOpExpressionContext;
import records.grammar.ExpressionParser.ColumnRefContext;
import records.grammar.ExpressionParser.NumericLiteralContext;
import records.grammar.ExpressionParser.TableIdContext;
import records.grammar.ExpressionParserBaseVisitor;
import records.transformations.expression.BinaryOpExpression.Op;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.stream.Stream;

/**
 * Created by neil on 24/11/2016.
 */
public abstract class Expression
{

    @OnThread(Tag.Simulation)
    public boolean getBoolean(RecordSet data, int rowIndex, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        return getTypeValue(data).applyGet(new SpecificDataTypeVisitorGet<Boolean>(new UserException("Type must be boolean")) {
            @Override
            @OnThread(Tag.Simulation)
            public Boolean bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return g.getWithProgress(rowIndex, prog);
            }
        });
    }

    public DataType getType(RecordSet data) throws UserException, InternalException
    {
        return getTypeValue(data);
    }
    public abstract DataTypeValue getTypeValue(RecordSet data) throws UserException, InternalException;

    // Note that there will be duplicates if referred to multiple times
    public abstract Stream<ColumnId> allColumnNames();

    @OnThread(Tag.FXPlatform)
    public abstract String save();

    public static Expression parse(@Nullable String keyword, String src) throws UserException, InternalException
    {
        if (keyword != null)
        {
            src = src.trim();
            if (src.startsWith(keyword))
                src = src.substring(keyword.length());
            else
                throw new UserException("Missing keyword: " + keyword);
        }
        return Utility.parseAsOne(src.replace("\r", "").replace("\n", ""), ExpressionLexer::new, ExpressionParser::new, p -> {
            return new CompileExpression().visit(p.expression());
        });
    }

    public abstract Formula toSolver(FormulaManager formulaManager, RecordSet src) throws InternalException, UserException;

    private static class CompileExpression extends ExpressionParserBaseVisitor<Expression>
    {
        @Override
        public Expression visitColumnRef(ColumnRefContext ctx)
        {
            TableIdContext tableIdContext = ctx.tableId();
            if (ctx.columnId() == null)
                throw new RuntimeException("WTF, yo? " + ctx.getText() + " " + ctx.columnId() + " " + ctx.tableId() + " " + ctx.children.size());
            return new ColumnReference(tableIdContext == null ? null : new TableId(tableIdContext.getText()), new ColumnId(ctx.columnId().getText()));
        }

        @Override
        public Expression visitNumericLiteral(NumericLiteralContext ctx)
        {
            return new NumericLiteral(Utility.parseNumber(ctx.getText()));
        }

        @Override
        public Expression visitBinaryOpExpression(BinaryOpExpressionContext ctx)
        {
            @Nullable Op op = Op.parse(ctx.binaryOp().getText());
            if (op == null)
                throw new RuntimeException("Broken operator parse: " + ctx.binaryOp().getText());
            return new BinaryOpExpression(visitExpression(ctx.expression().get(0)), op, visitExpression(ctx.expression().get(1)));
        }

    }
}
