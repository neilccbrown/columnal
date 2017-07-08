package records.gui.stf;

import com.google.common.collect.ImmutableList;
import records.gui.stf.StructuredTextField.ErrorFix;
import records.gui.stf.StructuredTextField.Item;
import records.gui.stf.StructuredTextField.ItemVariant;
import utility.Either;

import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 28/06/2017.
 */
public class TextEntry extends TerminalComponent<String>
{
    public TextEntry(ImmutableList<Component<?>> parents, String initial)
    {
        super(parents);
        items.addAll(Arrays.asList(new Item(getItemParents(), "\""), new Item(getItemParents(), initial, ItemVariant.EDITABLE_TEXT, ""), new Item(getItemParents(), "\"")));
    }

    @Override
    public Either<List<ErrorFix>, String> endEdit(StructuredTextField<?> field)
    {
        return Either.right(getItem(endResult, ItemVariant.EDITABLE_TEXT));
    }
}
