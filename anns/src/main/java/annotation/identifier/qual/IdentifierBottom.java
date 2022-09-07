package annotation.identifier.qual;

import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeUseLocation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Created by neil on 11/01/2017.
 */
@SubtypeOf({ExpressionIdentifier.class, UnitIdentifier.class})
@DefaultFor(TypeUseLocation.LOWER_BOUND)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
public @interface IdentifierBottom
{
}