package test.gen.type;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import test.gen.GenValueBase;
import test.gen.type.GenJellyTypeMaker.GenTaggedType;
import test.gen.type.GenJellyTypeMaker.TypeKinds;
import test.gen.type.GenDataTypeMaker.DataTypeMaker;
import test.gen.type.GenJellyTypeMaker.JellyTypeMaker;
import utility.Either;

/**
 * Created by neil on 13/01/2017.
 */
public class GenDataTypeMaker extends GenValueBase<DataTypeMaker>
{
    private final GenJellyTypeMaker genJellyTypeMaker;
    private final boolean mustHaveValues;
    
    public class DataTypeAndValueMaker
    {
        private final TypeManager typeManager;
        private final DataType dataType;

        private DataTypeAndValueMaker(TypeManager typeManager, DataType dataType)
        {
            this.typeManager = typeManager;
            this.dataType = dataType;
        }

        public DataType getDataType()
        {
            return dataType;
        }

        public TypeManager getTypeManager()
        {
            return typeManager;
        }
        
        public @Value Object makeValue() throws InternalException, UserException
        {
            return GenDataTypeMaker.this.makeValue(dataType);
        }
    }

    public class DataTypeMaker
    {
        private final JellyTypeMaker jellyTypeMaker;

        public DataTypeMaker(JellyTypeMaker jellyTypeMaker)
        {
            this.jellyTypeMaker = jellyTypeMaker;
        }
        
        public DataTypeAndValueMaker makeType() throws InternalException, UserException
        {
            DataType dataType;
            do
            {
                dataType = jellyTypeMaker.makeType().makeDataType(ImmutableMap.of(), jellyTypeMaker.typeManager);
            }
            while (mustHaveValues && !hasValues(dataType));


            return new DataTypeAndValueMaker(jellyTypeMaker.typeManager, dataType);
        }

        public TypeManager getTypeManager()
        {
            return jellyTypeMaker.typeManager;
        }
    }
    
    public GenDataTypeMaker()
    {
        // All kinds:
        this(false);
    }

    public GenDataTypeMaker(boolean mustHaveValues)
    {
        // All kinds:
        this(ImmutableSet.copyOf(TypeKinds.values()), mustHaveValues);
    }

    public GenDataTypeMaker(ImmutableSet<TypeKinds> typeKinds, boolean mustHaveValues)
    {
        this(new GenJellyTypeMaker(typeKinds, ImmutableSet.of(), mustHaveValues), mustHaveValues);
    }
    
    protected GenDataTypeMaker(GenJellyTypeMaker genJellyTypeMaker, boolean mustHaveValues)
    {
        super(DataTypeMaker.class);
        this.genJellyTypeMaker = genJellyTypeMaker;
        this.mustHaveValues = mustHaveValues;
    }
    
    @Override
    public DataTypeMaker generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        this.r = r;
        this.gs = generationStatus;
        JellyTypeMaker jellyTypeMaker = genJellyTypeMaker.generate(r, generationStatus);
        return new DataTypeMaker(jellyTypeMaker);
    }

    private static boolean hasValues(DataType dataType) throws InternalException
    {
        return dataType.apply(new DataTypeVisitorEx<Boolean, InternalException>()
        {
            @Override
            public Boolean number(NumberInfo numberInfo) throws InternalException
            {
                return true;
            }

            @Override
            public Boolean text() throws InternalException
            {
                return true;
            }

            @Override
            public Boolean date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return true;
            }

            @Override
            public Boolean bool() throws InternalException
            {
                return true;
            }

            @Override
            public Boolean tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException
            {
                return !tags.isEmpty();
            }

            @Override
            public Boolean tuple(ImmutableList<DataType> inner) throws InternalException
            {
                for (DataType type : inner)
                {
                    if (!hasValues(type))
                    {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public Boolean array(DataType inner) throws InternalException
            {
                return hasValues(inner);
            }
        });
    }

    public static class GenTaggedType extends GenDataTypeMaker
    {
        public GenTaggedType()
        {
            super(new GenJellyTypeMaker.GenTaggedType(), true);
        }        
    }
}