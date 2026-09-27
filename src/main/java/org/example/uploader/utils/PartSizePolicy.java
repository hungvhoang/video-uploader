package org.example.uploader.utils;

public final class PartSizePolicy {

    private static final long MIB = 1024L * 1024L;
    private static final long MINIMUM_PART_SIZE = 10 * MIB;
    private static final long MAXIMUM_PART_SIZE = 5L * 1024L * MIB;
    private static final long MAXIMUM_OBJECT_SIZE = 5L * 1024L * 1024L * MIB;
    private static final int MAXIMUM_PART_COUNT = 10_000;

    private PartSizePolicy() {
    }

    public static PartPlan calculate(long objectSize) {
        if (objectSize <= 0 || objectSize > MAXIMUM_OBJECT_SIZE) {
            throw new IllegalArgumentException("Object size must be between 1 byte and 5 TiB");
        }

        long requiredPartSize = ceilingDivide(objectSize, MAXIMUM_PART_COUNT);
        long roundedPartSize = ceilingDivide(requiredPartSize, MIB) * MIB;
        long partSize = Math.max(MINIMUM_PART_SIZE, roundedPartSize);
        if (partSize > MAXIMUM_PART_SIZE) {
            throw new IllegalArgumentException("Required part size exceeds 5 GiB");
        }

        int partCount = Math.toIntExact(ceilingDivide(objectSize, partSize));
        return new PartPlan(partSize, partCount);
    }

    private static long ceilingDivide(long dividend, long divisor) {
        return (dividend - 1) / divisor + 1;
    }

    public record PartPlan(long partSize, int partCount) {
    }
}
