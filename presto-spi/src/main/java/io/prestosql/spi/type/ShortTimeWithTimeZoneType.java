/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.spi.type;

import io.airlift.slice.Slice;
import io.airlift.slice.XxHash64;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.block.BlockBuilder;
import io.prestosql.spi.block.BlockBuilderStatus;
import io.prestosql.spi.block.LongArrayBlockBuilder;
import io.prestosql.spi.block.PageBuilderStatus;
import io.prestosql.spi.connector.ConnectorSession;
import io.prestosql.spi.function.ScalarOperator;

import static io.prestosql.spi.function.OperatorType.EQUAL;
import static io.prestosql.spi.function.OperatorType.HASH_CODE;
import static io.prestosql.spi.function.OperatorType.XX_HASH_64;
import static io.prestosql.spi.type.DateTimeEncoding.unpackOffsetMinutes;
import static io.prestosql.spi.type.DateTimeEncoding.unpackTimeNanos;
import static io.prestosql.spi.type.TimeWithTimezoneTypes.normalizeNanos;
import static io.prestosql.spi.type.TimeWithTimezoneTypes.normalizePackedTime;
import static io.prestosql.spi.type.Timestamps.PICOSECONDS_PER_NANOSECOND;
import static io.prestosql.spi.type.TypeOperatorDeclaration.extractOperatorDeclaration;
import static java.lang.String.format;
import static java.lang.invoke.MethodHandles.lookup;

/**
 * Encodes time with time zone up to p = 9.
 */
class ShortTimeWithTimeZoneType
        extends TimeWithTimeZoneType
{
    private static final TypeOperatorDeclaration TYPE_OPERATOR_DECLARATION = extractOperatorDeclaration(ShortTimeWithTimeZoneType.class, lookup(), long.class);

    public ShortTimeWithTimeZoneType(int precision)
    {
        super(precision, long.class);

        if (precision < 0 || precision > MAX_SHORT_PRECISION) {
            throw new IllegalArgumentException(format("Precision must be in the range [0, %s]", MAX_SHORT_PRECISION));
        }
    }

    @Override
    public TypeOperatorDeclaration getTypeOperatorDeclaration(TypeOperators typeOperators)
    {
        return TYPE_OPERATOR_DECLARATION;
    }

    @Override
    public final int getFixedSize()
    {
        return Long.BYTES;
    }

    @Override
    public final long getLong(Block block, int position)
    {
        return block.getLong(position, 0);
    }

    @Override
    public final Slice getSlice(Block block, int position)
    {
        return block.getSlice(position, 0, getFixedSize());
    }

    @Override
    public final void writeLong(BlockBuilder blockBuilder, long value)
    {
        blockBuilder.writeLong(value).closeEntry();
    }

    @Override
    public final void appendTo(Block block, int position, BlockBuilder blockBuilder)
    {
        if (block.isNull(position)) {
            blockBuilder.appendNull();
        }
        else {
            blockBuilder.writeLong(block.getLong(position, 0)).closeEntry();
        }
    }

    @Override
    public int compareTo(Block leftBlock, int leftPosition, Block rightBlock, int rightPosition)
    {
        long left = leftBlock.getLong(leftPosition, 0);
        long right = rightBlock.getLong(rightPosition, 0);

        return Long.compare(normalizeNanos(unpackTimeNanos(left), unpackOffsetMinutes(left)), normalizeNanos(unpackTimeNanos(right), unpackOffsetMinutes(right)));
    }

    @Override
    public final BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries, int expectedBytesPerEntry)
    {
        int maxBlockSizeInBytes;
        if (blockBuilderStatus == null) {
            maxBlockSizeInBytes = PageBuilderStatus.DEFAULT_MAX_PAGE_SIZE_IN_BYTES;
        }
        else {
            maxBlockSizeInBytes = blockBuilderStatus.getMaxPageSizeInBytes();
        }
        return new LongArrayBlockBuilder(
                blockBuilderStatus,
                Math.min(expectedEntries, maxBlockSizeInBytes / Long.BYTES));
    }

    @Override
    public final BlockBuilder createBlockBuilder(BlockBuilderStatus blockBuilderStatus, int expectedEntries)
    {
        return createBlockBuilder(blockBuilderStatus, expectedEntries, Long.BYTES);
    }

    @Override
    public final BlockBuilder createFixedSizeBlockBuilder(int positionCount)
    {
        return new LongArrayBlockBuilder(null, positionCount);
    }

    @Override
    public Object getObjectValue(ConnectorSession session, Block block, int position)
    {
        if (block.isNull(position)) {
            return null;
        }

        long value = block.getLong(position, 0);
        return SqlTimeWithTimeZone.newInstance(getPrecision(), unpackTimeNanos(value) * PICOSECONDS_PER_NANOSECOND, unpackOffsetMinutes(value));
    }

    @ScalarOperator(EQUAL)
    private static boolean equalOperator(long leftPackedTime, long rightPackedTime)
    {
        return normalizePackedTime(leftPackedTime) == normalizePackedTime(rightPackedTime);
    }

    @ScalarOperator(HASH_CODE)
    private static long hashCodeOperator(long packedTime)
    {
        return AbstractLongType.hash(normalizePackedTime(packedTime));
    }

    @ScalarOperator(XX_HASH_64)
    private static long xxHash64Operator(long packedTime)
    {
        return XxHash64.hash(normalizePackedTime(packedTime));
    }
}
