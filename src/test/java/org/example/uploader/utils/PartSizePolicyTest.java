package org.example.uploader.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PartSizePolicyTest {

    private static final long MIB = 1024L * 1024L;
    private static final long GIB = 1024L * MIB;
    private static final long TIB = 1024L * GIB;

    @Test
    void usesMinimumPartSizeForSmallFile() {
        var plan = PartSizePolicy.calculate(1);

        assertThat(plan.partSize()).isEqualTo(10 * MIB);
        assertThat(plan.partCount()).isEqualTo(1);
    }

    @Test
    void usesTwoMinimumSizedPartsJustOverMinimum() {
        var plan = PartSizePolicy.calculate(10 * MIB + 1);

        assertThat(plan.partSize()).isEqualTo(10 * MIB);
        assertThat(plan.partCount()).isEqualTo(2);
    }

    @Test
    void acceptsExactTenThousandPartBoundary() {
        var plan = PartSizePolicy.calculate(100_000L * MIB);

        assertThat(plan.partSize()).isEqualTo(10 * MIB);
        assertThat(plan.partCount()).isEqualTo(10_000);
    }

    @Test
    void roundsRequiredPartSizeUpToWholeMibibyte() {
        long objectSize = 100_000L * MIB + 1;

        var plan = PartSizePolicy.calculate(objectSize);

        assertThat(plan.partSize()).isEqualTo(11 * MIB);
        assertThat(plan.partCount()).isEqualTo(9_091);
    }

    @Test
    void acceptsMaximumS3ObjectSizeWithinPartLimits() {
        var plan = PartSizePolicy.calculate(5 * TIB);

        assertThat(plan.partSize()).isEqualTo(525 * MIB);
        assertThat(plan.partCount()).isEqualTo(9_987);
        assertThat(plan.partCount()).isLessThanOrEqualTo(10_000);
        assertThat(plan.partSize()).isLessThanOrEqualTo(5 * GIB);
    }

    @Test
    void rejectsNonPositiveObjectSizes() {
        assertThatIllegalArgumentException().isThrownBy(() -> PartSizePolicy.calculate(0));
        assertThatIllegalArgumentException().isThrownBy(() -> PartSizePolicy.calculate(-1));
    }

    @Test
    void rejectsObjectsAboveS3Maximum() {
        assertThatIllegalArgumentException().isThrownBy(() -> PartSizePolicy.calculate(5 * TIB + 1));
    }
}
