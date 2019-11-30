package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.primitives.Booleans;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import records.data.datatype.TaggedTypeDefinition.TypeVariableKind;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.jellytype.JellyType.UnknownTypeException;
import records.rinterop.RVisitor.PairListEntry;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.Utility.Record;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class RData
{    
    public static final int STRING_SINGLE = 9;
    public static final int LOGICAL_VECTOR = 10;
    public static final int INT_VECTOR = 13;
    public static final int DOUBLE_VECTOR = 14;
    public static final int STRING_VECTOR = 16;
    public static final int GENERIC_VECTOR = 19;
    public static final int PAIR_LIST = 2;
    public static final int NIL = 254;
    public static final int NA_AS_INTEGER = 0x80000000;
    public static final int SYMBOL = 1;
    public static final String[] CLASS_DATA_FRAME = {"data.frame"};
    public static final String[] CLASS_TIBBLE = {"tbl_df", "tbl", "data.frame"};

    private static int addAtom(HashMap<Integer, String> atoms, String atom)
    {
        int newKey = atoms.size() + 1;
        atoms.put(newKey, atom);
        return newKey;
    }
    
    private static String getAtom(HashMap<Integer, String> atoms, int key) throws UserException
    {
        String atom = atoms.get(key);
        if (atom != null)
            return atom;
        throw new UserException("Could not find atom: " + key + " in atoms sized: " + atoms.size());
    }
    
    private static ColumnId getColumnName(@Nullable RValue listColumnNames, int index) throws UserException, InternalException
    {
        @SuppressWarnings("identifier")
        @ExpressionIdentifier String def = "Column " + index;
        if (listColumnNames != null)
        {
            @Value String s = RUtility.getString(RUtility.getListItem(listColumnNames, index));
            return new ColumnId(IdentifierUtility.fixExpressionIdentifier(s == null ? "" : s, def));
        }
        return new ColumnId(def);
    }
    
    public static RValue readRData(File rFilePath) throws IOException, InternalException, UserException
    {
        InputStream rds = openInputStream(rFilePath);
        DataInputStream d = new DataInputStream(rds);
        byte[] header = new byte[5];
        d.mark(10);
        d.readFully(header);
        boolean rData = false;
        if (header[0] == 'R' && header[1] == 'D' && header[2] == 'X' && header[4] == '\n')
        {
            rData = true;
        }
        else
        {
            d.reset();
        }
        
        V2Header v2Header = new V2Header(d);
        
        if (v2Header.formatVersion == 3)
        {
            String encoding = readLenString(d);
        }

        RValue result = readItem(d, new HashMap<>());
        int after = d.read();
        if (after != -1)
            throw new UserException("Unexpected data at end of file: " + after);
        return result;
    }

    static RValue readItem(DataInputStream d, HashMap<Integer, String> atoms) throws IOException, UserException, InternalException
    {
        TypeHeader objHeader = new TypeHeader(d, atoms);
        String indent = Arrays.stream(Thread.currentThread().getStackTrace()).map(s -> s.getMethodName().equals("readItem") ? "  " : "").collect(Collectors.joining());
        //System.out.println(indent + "Read: " + objHeader.getType() + " " + objHeader.hasAttributes() + "/" + objHeader.hasTag());

        switch (objHeader.getType())
        {
            case 242: // Empty environment; not sure how to handle:
            case NIL: // Nil
                return new RValue() {

                    @Override
                    public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                    {
                        return visitor.visitNil();
                    }
                };
            case 255: // Ref
            {
                int ref = objHeader.getReference(d);
                String atom = getAtom(atoms, ref);
                return RUtility.string(atom, false);
            }
            case SYMBOL: // Symbol
            {
                RValue symbol = readItem(d, atoms);
                symbol.visit(new SpecificRVisitor<@Nullable @Value String>()
                {
                    @Override
                    public @Nullable @Value String visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
                    {
                        if (s != null)
                            addAtom(atoms, s);
                        return s;
                    }
                });
                return RUtility.string(RUtility.getString(symbol), true);
            }
            case 6:
            case PAIR_LIST: // Pair list
            {
                RValue attr = objHeader.readAttributes(d, atoms);
                RValue tag = objHeader.readTag(d, atoms);
                RValue head = readItem(d, atoms);
                RValue tail = readItem(d, atoms);
                ImmutableList<PairListEntry> items = flattenPairList(head, tail, attr, tag).collect(ImmutableList.<PairListEntry>toImmutableList());
                //System.out.println(indent + "List had " + prettyPrint(itemsBuilder.get(0).getSecond()) + " and " + prettyPrint(itemsBuilder.get(1).getSecond()));
                //if (objHeader.getType() != 254)
                    //throw new UserException("Unexpected type in pair list (identifier: " + objHeader.getType() + ")");
                /*
                if (items.size() == 2 && TODO items.get(1).getFirst().factor)
                {
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(itemsBuilder.get(0).getSecond().visit(new SpecificRVisitor<int[]>()
                            {
                                @Override
                                public int[] visitIntList(int[] values) throws InternalException, UserException
                                {
                                    return values;
                                }
                            }), itemsBuilder.get(1).getSecond().visit(new SpecificRVisitor<String[]>()
                            {
                                @Override
                                public String[] visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes) throws InternalException, UserException
                                {
                                    return Utility.mapListExI(values, v -> v.visit(new SpecificRVisitor<String>()
                                    {
                                        @Override
                                        public String visitString(String s) throws InternalException, UserException
                                        {
                                            return s;
                                        }
                                    })).toArray(new String[0]);
                                }
                            }));
                        }
                    };
                    
                }
                else
                */
                {
                    /*
                    ImmutableList<Pair<@Nullable String, RValue>> items = Utility.<Pair<TypeHeader, RValue>, Pair<@Nullable String, RValue>>mapListI(itemsBuilder, p -> {
                        String key = null;
                        if (p.getFirst().pairListRef != 0)
                            key = atoms.get(p.getFirst().pairListRef);
                        return new Pair<@Nullable String, RValue>(key, p.getSecond());
                    });
                    */
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitPairList(items);
                        }
                    };
                }
            }
            case STRING_SINGLE: // String
            {
                @Nullable String s = readLenString(d);
                return RUtility.string(s, false);
            }
            case INT_VECTOR: // Integer vector
            {
                int vecLen = d.readInt();
                int[] values = new int[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readInt();
                }
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                ImmutableList<String> factorNames = attr == null ? null : attr.visit(new DefaultRVisitor<@Nullable ImmutableList<String>>(null) {
                    @Override
                    public @Nullable ImmutableList<String> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
                    {
                        if (items.size() >= 1 && items.get(0).tag != null && items.get(0).tag.visit(new DefaultRVisitor<Boolean>(false)
                        {
                            @Override
                            public Boolean visitString(@Nullable String s, boolean isSymbol) throws InternalException, UserException
                            {
                                return "levels".equals(s);
                            }
                        }))
                            return items.get(0).item.visit(new SpecificRVisitor<ImmutableList<String>>()
                            {
                                @SuppressWarnings("optional")
                                @Override
                                public ImmutableList<String> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
                                {
                                    return Utility.<Optional<@Value String>, String>mapListExI(values, v -> v.orElseThrow(() -> new UserException("Unexpected NA in factors")));
                                }

                                @Override
                                public ImmutableList<String> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
                                {
                                    return Utility.mapListExI(values, RUtility::getStringNN);
                                }
                            });
                        return null;
                    }
                });
                
                if (factorNames != null)
                {
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(values, factorNames);
                        }
                    };
                }
                else
                    return RUtility.intVector(values, attr);
            }
            case LOGICAL_VECTOR:
            {
                int vecLen = d.readInt();
                boolean[] values = new boolean[vecLen];
                boolean @MonotonicNonNull [] isNA = null;
                for (int i = 0; i < vecLen; i++)
                {
                    int n = d.readInt();
                    values[i] = n != 0;
                    if (n == NA_AS_INTEGER)
                    {
                        if (isNA == null)
                            isNA = new boolean[vecLen];
                        isNA[i] = true;
                    }
                }
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return RUtility.logicalVector(values, isNA, attr);
            }
            case DOUBLE_VECTOR: // Floating point vector
            {
                int vecLen = d.readInt();
                double[] values = new double[vecLen];
                for (int i = 0; i < vecLen; i++)
                {
                    values[i] = d.readDouble();
                }
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attr);
                if (RUtility.isClass(attrMap, "Date"))
                    return dateVector(values, attr);
                else if (RUtility.isClass(attrMap, "POSIXct"))
                    return dateTimeZonedVector(values, attr);
                else 
                    return RUtility.doubleVector(values, attr);
            }
            case STRING_VECTOR: // Character vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<Optional<@Value String>> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(Optional.ofNullable(RUtility.getString(readItem(d, atoms))));
                }
                ImmutableList<Optional<@Value String>> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return RUtility.stringVector(values, attr);
            }
            case GENERIC_VECTOR: // Generic vector
            {
                int vecLen = d.readInt();
                ImmutableList.Builder<RValue> valueBuilder = ImmutableList.builderWithExpectedSize(vecLen);
                for (int i = 0; i < vecLen; i++)
                {
                    valueBuilder.add(readItem(d, atoms));
                }
                ImmutableList<RValue> values = valueBuilder.build();
                final @Nullable RValue attr = objHeader.readAttributes(d, atoms);
                return RUtility.genericVector(values, attr, objHeader.isObject());
            }
            case 238: // ALTREP_SXP
            {
                RValue info = readItem(d, atoms);
                RValue state = readItem(d, atoms);
                RValue attr = readItem(d, atoms);
                return lookupClass(RUtility.getStringNN(RUtility.getListItem(info, 0)), RUtility.getStringNN(RUtility.getListItem(info, 1))).load(state, attr);
            }
            default:
                throw new UserException("Unsupported R object type (identifier: " + objHeader.getType() + ")");
        }
    }

    private static RValue dateTimeZonedVector(double[] values, @Nullable RValue attr) throws InternalException, UserException
    {
        ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attr);
        RValue tzone = attrMap.get("tzone");
        if (tzone != null)
        {
            ImmutableList.Builder<Optional<@Value TemporalAccessor>> b = ImmutableList.builderWithExpectedSize(values.length);
            for (double value : values)
            {
                if (Double.isNaN(value))
                    b.add(Optional.empty());
                else
                {
                    @SuppressWarnings("valuetype")
                    @Value ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((long) roundTowardsZero(value), (long) (1_000_000_000.0 * (value - roundTowardsZero(value)))), ZoneId.of(RUtility.getStringNN(RUtility.getListItem(tzone, 0))));
                    b.add(Optional.of(zdt));
                }
            }
            return temporalVector(new DateTimeInfo(DateTimeType.DATETIMEZONED), b.build());
        }
        else
        {
            ImmutableList.Builder<Optional<@Value TemporalAccessor>> b = ImmutableList.builderWithExpectedSize(values.length);
            for (double value : values)
            {
                if (Double.isNaN(value))
                    b.add(Optional.empty());
                else
                {
                    @SuppressWarnings("valuetype")
                    @Value LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond((long) roundTowardsZero(value)), ZoneId.of("UTC"));
                    b.add(Optional.of(ldt));
                }
            }
            return temporalVector(new DateTimeInfo(DateTimeType.DATETIME), b.build());
        }
    }

    private static double roundTowardsZero(double seconds)
    {
        return Math.signum(seconds) * Math.floor(Math.abs(seconds));
    }

    private static RValue dateVector(double[] values, @Nullable RValue attr) throws InternalException
    {
        if (DoubleStream.of(values).allMatch(d -> Double.isNaN(d) || d == (int)d))
        {
            ImmutableList<Optional<@Value TemporalAccessor>> dates = DoubleStream.of(values).<Optional<@Value TemporalAccessor>>mapToObj(d -> {
                if (Double.isNaN(d))
                    return Optional.empty();
                @SuppressWarnings("valuetype")
                @Value LocalDate date = LocalDate.ofEpochDay((int) d);
                return Optional.of(date);
            }).collect(ImmutableList.<Optional<@Value TemporalAccessor>>toImmutableList());
            return new RValue()
            {
                @Override
                public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                {
                    return visitor.visitTemporalList(DateTimeType.YEARMONTHDAY, dates, attr);
                }
            };
        }
        else
        {
            ImmutableList<Optional<@Value TemporalAccessor>> dates = DoubleStream.of(values).<Optional<@Value TemporalAccessor>>mapToObj(d -> {
                if (Double.isNaN(d))
                    return Optional.empty();
                double seconds = d * (60.0 * 60.0 * 24.0);
                double wholeSeconds = Math.floor(seconds);
                @SuppressWarnings("valuetype")
                @Value LocalDateTime date = LocalDateTime.ofEpochSecond((long)wholeSeconds, (int)(1_000_000_000 * (seconds - wholeSeconds)), ZoneOffset.UTC);
                return Optional.of(date);
            }).collect(ImmutableList.<Optional<@Value TemporalAccessor>>toImmutableList());
            return new RValue()
            {
                @Override
                public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                {
                    return visitor.visitTemporalList(DateTimeType.DATETIME, dates, attr);
                }
            };
        }
    }

    private static Stream<PairListEntry> flattenPairList(RValue head, RValue tail, @Nullable RValue attr, @Nullable RValue tag) throws UserException, InternalException
    {
        return Stream.<PairListEntry>concat(Stream.<PairListEntry>of(new PairListEntry(attr, tag, head)), tail.<Stream<PairListEntry>>visit(new DefaultRVisitor<Stream<PairListEntry>>(Stream.of(new PairListEntry(null, null, tail)))
        {
            @Override
            public Stream<PairListEntry> visitNil() throws InternalException, UserException
            {
                // No need to add to list
                return Stream.of();
            }

            @Override
            public Stream<PairListEntry> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                return items.stream();
            }
        }));
    }

    private static interface RClassLoad
    {
        public RValue load(RValue state, RValue attr) throws IOException, InternalException, UserException;
    }

    private static RClassLoad lookupClass(String c, String p) throws InternalException, UserException
    {
        switch (p + "#" + c)
        {
            case "base#wrap_integer":
                return (state, attr) -> {
                    return state;
                };
            case "base#compact_intseq":
                return (state, attr) -> {
                    // It's a triple: number of steps, the start value, and the step increment
                    return state.visit(new SpecificRVisitor<RValue>()
                    {
                        @Override
                        public RValue visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
                        {
                            double[] expanded = new double[(int)values[0]];
                            double x = values[1];
                            for (int i = 0; i < expanded.length; i++)
                            {
                                expanded[i] = x;
                                x += values[2];
                            }
                            return RUtility.doubleVector(expanded, null);
                        }
                    });
                };
        }
        throw new UserException("Unsupported R class: " + p + " " + c);
    }

    private static @Nullable String readLenString(DataInputStream d) throws IOException
    {
        int len = d.readInt();
        if (len < 0)
            return null;
        byte chars[] = new byte[len];
        d.readFully(chars);
        return new String(chars, StandardCharsets.UTF_8);
    }

    private static BufferedInputStream openInputStream(File rFilePath) throws IOException, UserException
    {
        byte[] firstBytes = new byte[10];
        new DataInputStream(new FileInputStream(rFilePath)).readFully(firstBytes);
        if (firstBytes[0] == 0x1F && Byte.toUnsignedInt(firstBytes[1]) == 0x8B)
        {
            return new BufferedInputStream(new GZIPInputStream(new FileInputStream(rFilePath))); 
        }
        //throw new UserException("Unrecognised file format");
        return new BufferedInputStream(new FileInputStream(rFilePath));
    }

    public static Pair<DataType, @Value Object> convertRToTypedValue(TypeManager typeManager, RValue rValue) throws InternalException, UserException
    {
        return rValue.visit(new RVisitor<Pair<DataType, @Value Object>>()
        {
            @Override
            public Pair<DataType, @Value Object> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot turn nil into value");
            }

            @Override
            public Pair<DataType, @Value Object> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                if (s != null)
                    return new Pair<>(DataType.TEXT, DataTypeUtility.value(s));
                else
                    return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.TEXT)), typeManager), typeManager.maybeMissing());
            }

            @Override
            public Pair<DataType, @Value Object> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.BOOLEAN, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.BOOLEAN), RUtility.valueImmediate(values));
            }

            @Override
            public Pair<DataType, @Value Object> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.NUMBER, DataTypeUtility.value(values[0]));
                else
                    return new Pair<>(DataType.array(DataType.NUMBER), RUtility.valueImmediate(values));
            }

            @Override
            public Pair<DataType, @Value Object> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.length == 1)
                    return new Pair<>(DataType.NUMBER, DataTypeUtility.value(doubleToValue(values[0])));
                else
                    return new Pair<>(DataType.array(DataType.NUMBER), DataTypeUtility.<@ImmediateValue @NonNull Object>valueImmediate(DoubleStream.of(values).<@ImmediateValue Object>mapToObj(d -> doubleToValue(d)).collect(ImmutableList.<@ImmediateValue @NonNull Object>toImmutableList())));
            }

            @Override
            @SuppressWarnings("optional")
            public Pair<DataType, @Value Object> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.stream().allMatch(v -> v.isPresent()))
                {
                    if (values.size() == 1)
                        return new Pair<>(DataType.TEXT, values.get(0).get());
                    else
                        return new Pair<>(DataType.array(DataType.TEXT), DataTypeUtility.value(Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.get())));
                }
                else 
                {
                    if (values.size() == 1) // Must actually be empty if not all present:
                        return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.TEXT)), typeManager), typeManager.maybeMissing());
                    else
                        return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.TEXT)), typeManager), DataTypeUtility.<@Value Object>value(Utility.<Optional<@Value String>, @Value Object>mapListI(values, v -> v.<@Value Object>map(s -> typeManager.maybePresent(s)).orElseGet(typeManager::maybeMissing))));
                }
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<DataType, @Value Object> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                if (values.stream().allMatch(v -> v.isPresent()))
                {
                    if (values.size() == 1)
                        return new Pair<>(DataType.date(t), values.get(0).get());
                    else
                        return new Pair<>(DataType.array(DataType.date(t)), DataTypeUtility.value(Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.get())));
                }
                else
                {
                    if (values.size() == 1) // Must actually be empty if not all present:
                        return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.date(t))), typeManager), typeManager.maybeMissing());
                    else
                        return new Pair<>(typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(DataType.date(t))), typeManager), DataTypeUtility.<@Value Object>value(Utility.<Optional<@Value TemporalAccessor>, @Value Object>mapListI(values, v -> v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing))));
                }
            }

            @Override
            public Pair<DataType, @Value Object> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                Pair<DataType, ImmutableList<@Value Object>> inner = rListToValueList(typeManager, values);
                return new Pair<>(DataType.array(inner.getFirst()), new ListExList(inner.getSecond()));
            }

            @Override
            public Pair<DataType, @Value Object> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                throw new UserException("List found when single value expected: " + RPrettyPrint.prettyPrint(rValue));
            }

            @Override
            public Pair<DataType, @Value Object> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                if (values.length == 1)
                    return new Pair<>(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager), new TaggedValue(values[0] - 1, null, taggedTypeDefinition));
                else
                    return new Pair<>(DataType.array(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager)), DataTypeUtility.value(IntStream.of(values).mapToObj(n -> new TaggedValue(n - 1, null, taggedTypeDefinition)).collect(ImmutableList.<@Value Object>toImmutableList())));
            }
        });
    }

    private static TaggedTypeDefinition getTaggedTypeForFactors(ImmutableList<String> levelNames, TypeManager typeManager) throws InternalException, UserException
    {
        ImmutableList<@ExpressionIdentifier String> processedNames = Streams.<String, @ExpressionIdentifier String>mapWithIndex(levelNames.stream(), (s, i) -> IdentifierUtility.fixExpressionIdentifier(s, IdentifierUtility.identNum("Factor", (int) i))).collect(ImmutableList.<@ExpressionIdentifier String>toImmutableList());

        ImmutableSet<@ExpressionIdentifier String> namesAsSet = ImmutableSet.copyOf(processedNames);
        TaggedTypeDefinition existing = typeManager.getKnownTaggedTypes().values().stream().filter(ttd ->
                ttd.getTags().stream().<@ExpressionIdentifier String>map(tt -> tt.getName()).collect(ImmutableSet.<@ExpressionIdentifier String>toImmutableSet()).equals(namesAsSet)
        ).findFirst().orElse(null);
        if (existing != null)
            return existing;
        
        for (int i = 0; i < 100; i++)
        {
            @SuppressWarnings("identifier")
            @ExpressionIdentifier String hint = "F " + i;
            @ExpressionIdentifier String typeName = IdentifierUtility.fixExpressionIdentifier(levelNames.stream().sorted().findFirst().orElse("F") + " " + levelNames.size(), hint);
            TaggedTypeDefinition taggedTypeDefinition = typeManager.registerTaggedType(typeName, ImmutableList.<Pair<TypeVariableKind, @ExpressionIdentifier String>>of(), levelNames.stream().map(s -> new TagType<JellyType>(IdentifierUtility.fixExpressionIdentifier(s, "Factor"), null)).collect(ImmutableList.<TagType<JellyType>>toImmutableList()));
            if (taggedTypeDefinition != null)
                return taggedTypeDefinition;
        }
        throw new UserException("Type named F1 through F100 already exists but with different tags");
    }

    public static Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> convertRToColumn(TypeManager typeManager, RValue rValue, ColumnId columnName) throws UserException, InternalException
    {
        return rValue.visit(new RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>()
        {
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitNil() throws InternalException, UserException
            {
                throw new UserException("Cannot make column from nil value");
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return visitStringList(ImmutableList.<Optional<@Value String>>of(Optional.<@Value String>ofNullable(s)), null);
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (values.stream().allMatch(v -> v.isPresent()))
                    return new Pair<>(rs -> new MemoryStringColumn(rs, columnName, Utility.mapList(values, v -> Either.<String, String>right(v.get())), ""), values.size());
                else
                    return makeMaybeColumn(DataType.TEXT, Utility.mapListI(values, v -> Either.<String, TaggedValue>right(v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing))));
            }

            @SuppressWarnings("optional")
            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                DateTimeInfo t = new DateTimeInfo(dateTimeType);
                if (values.stream().allMatch(v -> v.isPresent()))
                    return new Pair<>(rs -> new MemoryTemporalColumn(rs, columnName, t, Utility.mapListI(values, v -> Either.<String, TemporalAccessor>right(v.get())), t.getDefaultValue()), values.size());
                else
                    return makeMaybeColumn(DataType.date(t), Utility.mapListI(values, v -> Either.<String, TaggedValue>right(v.map(typeManager::maybePresent).orElseGet(typeManager::maybeMissing))));
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                if (isNA == null)
                    return new Pair<>(rs -> new MemoryBooleanColumn(rs, columnName, Booleans.asList(values).stream().map(n -> Either.<String, Boolean>right(n)).collect(ImmutableList.<Either<String, Boolean>>toImmutableList()), false), values.length);
                else
                {
                    ImmutableList.Builder<Either<String, @Value TaggedValue>> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                    for (int i = 0; i < values.length; i++)
                    {
                        if (isNA[i])
                            maybeValues.add(Either.right(typeManager.maybeMissing()));
                        else
                            maybeValues.add(Either.right(typeManager.maybePresent(DataTypeUtility.value(values[i]))));
                    }

                    return makeMaybeColumn(DataType.BOOLEAN, maybeValues.build());
                }
            }

            private Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> makeMaybeColumn(DataType inner, ImmutableList<Either<String, @Value TaggedValue>> maybeValues) throws TaggedInstantiationException, InternalException, UnknownTypeException
            {
                ImmutableList<Either<Unit, DataType>> typeVar = ImmutableList.of(Either.<Unit, DataType>right(inner));
                DataType maybeDataType = typeManager.getMaybeType().instantiate(typeVar, typeManager);
                return new Pair<>(rs -> new MemoryTaggedColumn(rs, columnName, typeManager.getMaybeType().getTaggedTypeName(), typeVar, maybeDataType.apply(new SpecificDataTypeVisitor<ImmutableList<TagType<DataType>>>() {
                    @Override
                    public ImmutableList<TagType<DataType>> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
                    {
                        return tags;
                    }
                }), maybeValues, typeManager.maybeMissing()), maybeValues.size());
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, IntStream.of(values).mapToObj(n -> Either.<String, Number>right(n)).collect(ImmutableList.<Either<String, Number>>toImmutableList()), DataTypeUtility.value(0)), values.length);
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                boolean hasNaNs = false;
                for (int i = 0; i < values.length; i++)
                {
                    if (Double.isNaN(values[i]))
                    {
                        hasNaNs = true;
                        break;
                    }
                }
                if (hasNaNs)
                {
                    ImmutableList.Builder<Either<String, @Value TaggedValue>> maybeValues = ImmutableList.builderWithExpectedSize(values.length);
                    for (int i = 0; i < values.length; i++)
                    {
                        if (Double.isNaN(values[i]))
                            maybeValues.add(Either.right(typeManager.maybeMissing()));
                        else
                            maybeValues.add(Either.right(typeManager.maybePresent(doubleToValue(values[i]))));
                    }

                    return makeMaybeColumn(DataType.NUMBER, maybeValues.build());
                }
                else
                {
                    return new Pair<>(rs -> new MemoryNumericColumn(rs, columnName, NumberInfo.DEFAULT, DoubleStream.of(values).mapToObj(n -> {
                        try
                        {
                            return Either.<String, Number>right(doubleToValue(n));
                        }
                        catch (NumberFormatException e)
                        {
                            return Either.<String, Number>left(Double.toString(n));
                        }
                    }).collect(ImmutableList.<Either<String, Number>>toImmutableList()), DataTypeUtility.value(0)), values.length);
                }
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                boolean hasNAs = false;
                for (int value : values)
                {
                    if (value == NA_AS_INTEGER)
                    {
                        hasNAs = true;
                        break;
                    }
                }

                TaggedTypeDefinition taggedTypeDefinition = getTaggedTypeForFactors(levelNames, typeManager);
                ImmutableList.Builder<Either<String, TaggedValue>> factorValueBuilder = ImmutableList.builderWithExpectedSize(values.length);
                for (int n : values)
                {
                    if (hasNAs)
                    {
                        factorValueBuilder.add(Either.<String, TaggedValue>right(n == NA_AS_INTEGER ? typeManager.maybeMissing() : typeManager.maybePresent(new TaggedValue(lookupTag(n, levelNames, taggedTypeDefinition), null, taggedTypeDefinition))));
                    }
                    else
                    {
                        factorValueBuilder.add(Either.<String, TaggedValue>right(new TaggedValue(lookupTag(n, levelNames, taggedTypeDefinition), null, taggedTypeDefinition)));
                    }
                }
                ImmutableList<Either<String, TaggedValue>> factorValues = factorValueBuilder.build();
                
                if (hasNAs)
                {
                    
                    return makeMaybeColumn(taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager), factorValues);
                }
                else
                {
                    return new Pair<>(rs -> {
                        return new MemoryTaggedColumn(rs, columnName, taggedTypeDefinition.getTaggedTypeName(), ImmutableList.of(), Utility.mapList(taggedTypeDefinition.getTags(), t -> new TagType<>(t.getName(), null)), factorValues, new TaggedValue(0, null, taggedTypeDefinition));
                    }, values.length);
                }
            }

            private int lookupTag(int tagIndex, ImmutableList<String> levelNames, TaggedTypeDefinition taggedTypeDefinition) throws UserException
            {
                if (tagIndex > levelNames.size())
                    throw new UserException("Factor index does not have name");
                // Map one-based back to zero-based:
                @SuppressWarnings("identifier")
                String name = IdentifierUtility.fixExpressionIdentifier(levelNames.get(tagIndex - 1), levelNames.get(tagIndex - 1));
                return Utility.findFirstIndex(taggedTypeDefinition.getTags(), tt -> tt.getName().equals(name)).orElseThrow(() -> new UserException("Could not find tag named " + name + " in definition"));
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                Pair<DataType, ImmutableList<@Value Object>> loaded = rListToValueList(typeManager, values);
                return new Pair<>(loaded.getFirst().makeImmediateColumn(columnName, Utility.<@Value Object, Either<String, @Value Object>>mapListExI(loaded.getSecond(), v -> Either.<String, @Value Object>right(v)), DataTypeUtility.makeDefaultValue(loaded.getFirst())), loaded.getSecond().size());
            }

            @Override
            public Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                // For some reason, factor columns appear as pair list of two with an int[] as second item:
                if (items.size() == 2)
                {
                    RVisitor<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> outer = this;
                    @Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> asFactors = items.get(0).item.visit(new DefaultRVisitor<@Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>>(null)
                    {
                        @Override
                        public @Nullable Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
                        {
                            return outer.visitFactorList(values, levelNames);
                        }
                    });
                    if (asFactors != null)
                        return asFactors;
                }
                
                throw new UserException("Pair list found when column expected: " + RPrettyPrint.prettyPrint(rValue));
            }
        });
    }

    private static @ImmediateValue BigDecimal doubleToValue(double value)
    {
        // Go through Double.toString which zeroes out the boring end part:
        return DataTypeUtility.value(new BigDecimal(Double.toString(value)));
    }

    private static SimulationFunction<@Value Object, @Value Object> getOrInternal(ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>> map, DataType key) throws InternalException
    {
        SimulationFunction<@Value Object, @Value Object> f = map.get(key);
        if (f == null)
            throw new InternalException("No conversion found for type " + key);
        return f;
    }
    
    // The DataType is the type of the list *elements*, not the list as a whole.
    // e.g. the return may be (Number, [1,2,3])
    private static Pair<DataType, ImmutableList<@Value Object>> rListToValueList(TypeManager typeManager, List<RValue> values) throws UserException, InternalException
    {
        ImmutableList<Pair<DataType, @Value Object>> typedPairs = Utility.<RValue, Pair<DataType, @Value Object>>mapListExI(values, v -> convertRToTypedValue(typeManager, v));
        Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> m = generaliseType(Utility.mapListExI(typedPairs, p -> p.getFirst()));
        ImmutableList<@Value Object> loaded = Utility.<Pair<DataType, @Value Object>, @Value Object>mapListExI(typedPairs, p -> getOrInternal(m.getSecond(), p.getFirst()).apply(p.getSecond()));
        return new Pair<DataType, ImmutableList<@Value Object>>(m.getFirst(), loaded);
    }
    
    private static Pair<DataType, ImmutableMap<DataType, SimulationFunction<@Value Object, @Value Object>>> generaliseType(ImmutableList<DataType> types) throws UserException
    {
        if (types.isEmpty())
            return new Pair<>(DataType.TEXT, ImmutableMap.<DataType, SimulationFunction<@Value Object, @Value Object>>of());
        DataType curType = types.get(0);
        HashMap<DataType, SimulationFunction<@Value Object, @Value Object>> conversions = new HashMap<>();
        conversions.put(curType, x -> x);
        for (DataType nextType : types.subList(1, types.size()))
        {
            if (nextType.equals(curType))
                continue;
            if (DataTypeUtility.isNumber(nextType) && DataTypeUtility.isNumber(curType))
            {
                conversions.put(nextType, x -> x);
                continue;
            }
            if (nextType.equals(DataType.array(curType)))
            {
                conversions.put(curType, x -> DataTypeUtility.value(ImmutableList.<@Value Object>of(x)));
                conversions.put(nextType, x -> x);
                curType = nextType;
                continue;
            }
            if (curType.equals(DataType.array(nextType)))
            {
                conversions.put(nextType, x -> DataTypeUtility.value(ImmutableList.<@Value Object>of(x)));
                continue;
            }
            throw new UserException("Cannot generalise " + curType + " and " + nextType + " into a single type");
        }
        return new Pair<>(curType, ImmutableMap.copyOf(conversions));
    }
    
    public static ImmutableList<Pair<String, EditableRecordSet>> convertRToTable(TypeManager typeManager, RValue rValue) throws UserException, InternalException
    {
        // R tables are usually a list of columns, which suits us:
        return rValue.visit(new RVisitor<ImmutableList<Pair<String, EditableRecordSet>>>()
        {
            private ImmutableList<Pair<String, EditableRecordSet>> singleColumn() throws UserException, InternalException
            {
                Pair<SimulationFunction<RecordSet, EditableColumn>, Integer> p = convertRToColumn(typeManager, rValue, new ColumnId("Result"));
                return ImmutableList.<Pair<String, EditableRecordSet>>of(new Pair<>("Value", new <EditableColumn>EditableRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(p.getFirst()), () -> p.getSecond())));
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitNil() throws InternalException, UserException
            {
                return ImmutableList.of();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitLogicalList(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitIntList(int[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitDoubleList(double[] values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitTemporalList(DateTimeType dateTimeType, ImmutableList<Optional<@Value TemporalAccessor>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitFactorList(int[] values, ImmutableList<String> levelNames) throws InternalException, UserException
            {
                return singleColumn();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                for (PairListEntry item : items)
                {
                    ImmutableList<Pair<String, EditableRecordSet>> found = convertRToTable(typeManager, item.item);
                    if (item.tag != null && found.size() == 1)
                    {
                        String name = RUtility.getString(item.tag);
                        if (name != null)
                        {
                            // Skip the random seed; very unlikely they want that as a table:
                            if (name.equals(".Random.seed"))
                                continue;
                            
                            found = ImmutableList.<Pair<String, EditableRecordSet>>of(found.get(0).<String>replaceFirst(name));
                        }
                    }
                    r.addAll(found);
                }
                return r.build();
            }

            @Override
            public ImmutableList<Pair<String, EditableRecordSet>> visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                // Tricky; could be a list of tables, a list of columns or a list of values!
                // First try as table (list of columns):
                final ImmutableMap<String, RValue> attrMap = RUtility.pairListToMap(attributes);

                boolean isDataFrame = isObject && isDataFrameOrTibble(attrMap);
                if (isDataFrame)
                {
                    ImmutableList<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>> columns = Utility.mapListExI_Index(values, (i, v) -> convertRToColumn(typeManager, v, getColumnName(attrMap.get("names"), i)));
                    // Check columns are all the same length:
                    if (!columns.isEmpty() && columns.stream().mapToInt(p -> p.getSecond()).distinct().count() == 1)
                    {
                        return ImmutableList.of(new Pair<>("Table", new <EditableColumn>EditableRecordSet(Utility.<Pair<SimulationFunction<RecordSet, EditableColumn>, Integer>, SimulationFunction<RecordSet, EditableColumn>>mapList(columns, p -> p.getFirst()), () -> columns.get(0).getSecond())));
                    }
                    throw new UserException("Columns are of differing lengths: " + columns.stream().map(p -> "" + p.getSecond()).collect(Collectors.joining(", ")));
                }
                else
                {
                    boolean hasDataFrames = false;
                    for (RValue value : values)
                    {
                        boolean valueIsDataFrame = value.visit(new DefaultRVisitor<Boolean>(false)
                        {
                            @Override
                            public Boolean visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
                            {
                                final ImmutableMap<String, RValue> valueAttrMap = RUtility.pairListToMap(attributes);
                                return isObject && isDataFrameOrTibble(valueAttrMap);
                            }
                        });
                        
                        if (valueIsDataFrame)
                        {
                            hasDataFrames = true;
                            break;
                        }
                    }
                    if (!hasDataFrames)
                        return singleColumn();
                    
                    ImmutableList.Builder<Pair<String, EditableRecordSet>> r = ImmutableList.builder();
                    for (RValue value : values)
                    {
                        r.addAll(convertRToTable(typeManager, value));
                    }
                    return r.build();
                }
            }
        });
    }

    private static boolean isDataFrameOrTibble(ImmutableMap<String, RValue> attrMap) throws InternalException, UserException
    {
        return RUtility.isClass(attrMap, CLASS_DATA_FRAME) || RUtility.isClass(attrMap, CLASS_TIBBLE);
    }

    public static enum TableType { DATA_FRAME, TIBBLE }
    
    public static RValue convertTableToR(RecordSet recordSet, TableType tableType) throws UserException, InternalException
    {
        // A table is a generic list of columns with class data.frame
        return RUtility.genericVector(Utility.mapListExI(recordSet.getColumns(), c -> convertColumnToR(c, tableType)),
            RUtility.makeClassAttributes(tableType == TableType.DATA_FRAME ? CLASS_DATA_FRAME : CLASS_TIBBLE, ImmutableMap.<String, RValue>of(
                "names", RUtility.stringVector(Utility.<Column, Optional<@Value String>>mapListExI(recordSet.getColumns(), c -> Optional.of(DataTypeUtility.value(usToRColumn(c.getName(), tableType)))), null),
                "row.names", RUtility.intVector(new int[] {NA_AS_INTEGER, -recordSet.getLength()}, null)
            )), true);
    }

    public static String usToRColumn(ColumnId columnId, TableType tableType)
    {
        return tableType == TableType.TIBBLE ? columnId.getRaw() : columnId.getRaw().replace(" ", ".");
    }

    public static String usToRTable(TableId tableId)
    {
        return tableId.getRaw().replace(" ", ".");
    }

    private static RValue convertColumnToR(Column column, TableType tableType) throws UserException, InternalException
    {
        DataTypeValue dataTypeValue = column.getType();
        int length = column.getLength();
        return convertListToR(dataTypeValue, length, tableType == TableType.TIBBLE);
    }
    
    private static RValue convertListToR(DataTypeValue dataTypeValue, int length, boolean allowSubLists) throws UserException, InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGet<RValue>()
        {
            @Override
            public RValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                // Need to work out if they are all ints, otherwise must use doubles.
                // Start with ints and go from there
                int[] ints = new int[length];
                int i;
                for (i = 0; i < length; i++)
                {
                    @Value Number n = g.get(i);
                    @Value Integer nInt = getIfInteger(n);
                    if (nInt != null)
                        ints[i] = nInt;
                    else
                        break;
                }
                if (i == length)
                    return RUtility.intVector(ints, null);
                
                // Convert all ints to doubles:
                double[] doubles = new double[length];
                for (int j = 0; j < i; j++)
                {
                    doubles[j] = ints[j];
                }
                for (; i < length; i++)
                {
                    doubles[i] = Utility.toBigDecimal(g.get(i)).doubleValue();
                }
                return RUtility.doubleVector(doubles, null);
            }

            @Override
            public RValue text(GetValue<@Value String> g) throws InternalException, UserException
            {
                ImmutableList.Builder<Optional<@Value String>> list = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    list.add(Optional.of(g.get(i)));
                }
                return RUtility.stringVector(list.build(), null);
            }

            @Override
            public RValue bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                boolean[] bools = new boolean[length];
                for (int i = 0; i < length; i++)
                {
                    bools[i] = g.get(i);
                }
                return RUtility.logicalVector(bools, null, null);
            }

            @Override
            public RValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                ImmutableList.Builder<Optional<@Value TemporalAccessor>> valueBuilder = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    valueBuilder.add(Optional.of(g.get(i)));
                }
                ImmutableList<Optional<@Value TemporalAccessor>> values = valueBuilder.build();
                return temporalVector(dateTimeInfo, values);
            }

            @Override
            public RValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                if (tagTypes.size() == 1)
                {
                    TagType<DataType> onlyTag = tagTypes.get(0);
                    if (onlyTag.getInner() != null)
                    {
                        // Flatten by ignoring taggedness:
                        return onlyTag.getInner().fromCollapsed((i, prog) -> {
                            @Value Object inner = g.getWithProgress(i, prog).getInner();
                            if (inner == null)
                                throw new InternalException("Null inner value on tag with inner type");
                            return inner;
                        }).applyGet(this);
                    }
                }
                if (tagTypes.size() == 2)
                {
                    @Nullable DataType onlyInner = null;
                    if (tagTypes.get(0).getInner() != null && tagTypes.get(1).getInner() == null)
                        onlyInner = tagTypes.get(0).getInner();
                    else if (tagTypes.get(0).getInner() == null && tagTypes.get(1).getInner() != null)
                        onlyInner = tagTypes.get(1).getInner();
                    if (onlyInner != null)
                    {
                        // Can convert to equivalent of maybe; inner plus missing values as NA:
                        ImmutableList.Builder<Optional<@Value Object>> b = ImmutableList.builderWithExpectedSize(length);
                        for (int i = 0; i < length; i++)
                        {
                            @Value TaggedValue taggedValue = g.get(i);
                            if (taggedValue.getInner() != null)
                                b.add(Optional.<@Value Object>of(taggedValue.getInner()));
                            else
                                b.add(Optional.empty());
                        }
                        ImmutableList<Optional<@Value Object>> inners = b.build();
                        return onlyInner.apply(new DataTypeVisitor<RValue>()
                        {
                            @Override
                            public RValue number(NumberInfo numberInfo) throws InternalException, UserException
                            {
                                return RUtility.doubleVector(inners.stream().mapToDouble(mn -> mn.<Double>map(n -> Utility.toBigDecimal((Number)n).doubleValue()).orElse(Double.NaN)).toArray(), null);
                            }

                            @Override
                            public RValue text() throws InternalException, UserException
                            {
                                return RUtility.stringVector(Utility.<Optional<@Value Object>, Optional<@Value String>>mapListI(inners, v -> v.<@Value String>map(o -> (String) o)), null);
                            }

                            @Override
                            public RValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                            {
                                switch (dateTimeInfo.getType())
                                {
                                    case YEARMONTHDAY:
                                        return dateVector(inners.stream().mapToDouble(md -> md.map(d -> (double)((LocalDate)d).toEpochDay()).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("Date", ImmutableMap.of()));
                                    case YEARMONTH:
                                        return dateVector(inners.stream().mapToDouble(md -> md.map(d -> (double)((YearMonth)d).atDay(1).toEpochDay()).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("Date", ImmutableMap.of()));
                                    case TIMEOFDAY:
                                        return RUtility.doubleVector(inners.stream().mapToDouble(mn -> mn.<Double>map(n -> (double)((LocalTime)n).toNanoOfDay() / 1_000_000_000.0).orElse(Double.NaN)).toArray(), null);
                                    case DATETIMEZONED:
                                        return dateTimeZonedVector(inners.stream().mapToDouble(md -> md.map(d -> {
                                            ZonedDateTime zdt = (ZonedDateTime) d;
                                            return (double) zdt.toEpochSecond() + ((double) zdt.getNano() / 1_000_000_000.0);
                                        }).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("Date", ImmutableMap.of()));
                                    case DATETIME:
                                        return dateTimeZonedVector(inners.stream().mapToDouble(md -> md.map(d -> {
                                            LocalDateTime ldt = (LocalDateTime) d;
                                            double seconds = (double) ldt.toEpochSecond(ZoneOffset.UTC) + (double) ldt.getNano() / 1_000_000_000.0;
                                            return seconds;
                                        }).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("POSIXct", ImmutableMap.of()));
                                }
                                throw new InternalException("Unsupported date-time type: " + dateTimeInfo.getType());
                            }

                            @Override
                            public RValue bool() throws InternalException, UserException
                            {
                                return RUtility.logicalVector(Booleans.toArray(inners.stream().<Boolean>map(mb -> DataTypeUtility.unvalue(((Boolean)mb.orElse(DataTypeUtility.value(false))))).collect(ImmutableList.<Boolean>toImmutableList())), Booleans.toArray(inners.stream().map(b -> !b.isPresent()).collect(ImmutableList.<Boolean>toImmutableList())), null);
                            }

                            @Override
                            public RValue tagged(TypeId innerTypeName, ImmutableList<Either<Unit, DataType>> innerTypeVars, ImmutableList<TagType<DataType>> innerTags) throws InternalException, UserException
                            {
                                if (innerTags.stream().allMatch(tt -> tt.getInner() == null))
                                {
                                    int[] vals = new int[length];
                                    for (int i = 0; i < length; i++)
                                    {
                                        @Value TaggedValue val = g.get(i);
                                        if (val.getTagIndex() == 0)
                                            vals[i] = NA_AS_INTEGER;
                                        else
                                        {
                                            @Value Object valInner = val.getInner();
                                            if (valInner == null)
                                                throw new InternalException("Null inner value of present optional value in row " + i);
                                            vals[i] = Utility.cast(valInner, TaggedValue.class).getTagIndex() + 1;
                                        }
                                    }

                                    // Convert to factors:
                                    return new RValue()
                                    {
                                        @Override
                                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                                        {
                                            return visitor.visitFactorList(vals, Utility.mapListI(innerTags, tt -> tt.getName()));
                                        }
                                    };
                                }
                                
                                throw new UserException("Nested tagged types are not supported");
                            }

                            @Override
                            public RValue record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                            {
                                throw new UserException("Records are not supported in R");
                            }

                            @Override
                            public RValue array(DataType inner) throws InternalException, UserException
                            {
                                throw new UserException("Lists are not supported in R");
                            }
                        });
                    }
                }
                if (tagTypes.stream().allMatch(tt -> tt.getInner() == null))
                {
                    int[] vals = new int[length];
                    for (int i = 0; i < length; i++)
                    {
                        vals[i] = g.get(i).getTagIndex() + 1;
                    }    
                    
                    // Convert to factors:
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(vals, Utility.mapListI(tagTypes, tt -> tt.getName()));
                        }
                    };
                }
                
                throw new UserException("Cannot convert complex tagged type " + typeName.getRaw() + " to R");
            }

            @Override
            public RValue record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
            {
                throw new UserException("Cannot convert records to R");
            }

            @Override
            public RValue array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                if (allowSubLists)
                {
                    ImmutableList.Builder<RValue> listOfLists = ImmutableList.builderWithExpectedSize(length);
                    for (int outer = 0; outer < length; outer++)
                    {
                        @Value ListEx outerValues = g.get(outer);
                        listOfLists.add(convertListToR(inner.fromCollapsed((innerIndex, prog) -> outerValues.get(innerIndex)), outerValues.size(), true));
                    }
                    return RUtility.genericVector(listOfLists.build(), null, false);
                }
                throw new UserException("Cannot convert lists to R");
            }
        });
    }

    private static RValue temporalVector(DateTimeInfo dateTimeInfo, ImmutableList<Optional<@Value TemporalAccessor>> values)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                if (dateTimeInfo.getType() == DateTimeType.DATETIMEZONED && !values.isEmpty())
                {
                    // If no non-NA, won't matter:
                    ZoneId zone = values.stream().flatMap(v -> Utility.streamNullable(v.orElse(null))).findFirst().map(t -> ((ZonedDateTime) t).getZone()).orElse(ZoneId.systemDefault());

                    return visitor.visitTemporalList(dateTimeInfo.getType(), Utility.<Optional<@Value TemporalAccessor>, Optional<@Value TemporalAccessor>>mapListI(values, mv -> mv.<@Value TemporalAccessor>map(v -> DataTypeUtility.valueZonedDateTime(((ZonedDateTime) v).withZoneSameInstant(zone)))), RUtility.makeClassAttributes("POSIXct", ImmutableMap.of("tzone", RUtility.stringVector(DataTypeUtility.value(zone.toString())))));
                }
                else if (dateTimeInfo.getType() == DateTimeType.TIMEOFDAY)
                    return visitor.visitTemporalList(dateTimeInfo.getType(), values, null);
                else
                    return visitor.visitTemporalList(dateTimeInfo.getType(), values, RUtility.makeClassAttributes("Date", ImmutableMap.of()));
            }
        };
    }

    private static @Nullable @Value Integer getIfInteger(@Value Number n) throws InternalException
    {
        return Utility.<@Nullable @Value Integer>withNumberInt(n, l -> {
            if (l.longValue() != l.intValue())
                return null;
            return DataTypeUtility.value(l.intValue());
        }, bd -> {
            try
            {
                return DataTypeUtility.value(bd.intValueExact());
            }
            catch (ArithmeticException e)
            {
                return null;
            }
        });
    }

}
