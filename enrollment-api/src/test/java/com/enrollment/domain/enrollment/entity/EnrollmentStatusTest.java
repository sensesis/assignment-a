package com.enrollment.domain.enrollment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentStatusTest {

    @Test
    void PENDING에서_CONFIRMED로_전이_가능() {
        assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.CONFIRMED)).isTrue();
    }

    @Test
    void PENDING에서_CANCELLED로_전이_가능() {
        assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.CANCELLED)).isTrue();
    }

    @Test
    void CONFIRMED에서_CANCELLED로_전이_가능() {
        assertThat(EnrollmentStatus.CONFIRMED.canTransitionTo(EnrollmentStatus.CANCELLED)).isTrue();
    }

    @Test
    void CONFIRMED에서_PENDING으로_전이_불가() {
        assertThat(EnrollmentStatus.CONFIRMED.canTransitionTo(EnrollmentStatus.PENDING)).isFalse();
    }

    @Test
    void CANCELLED에서_어디로도_전이_불가() {
        assertThat(EnrollmentStatus.CANCELLED.canTransitionTo(EnrollmentStatus.PENDING)).isFalse();
        assertThat(EnrollmentStatus.CANCELLED.canTransitionTo(EnrollmentStatus.CONFIRMED)).isFalse();
    }

    @Test
    void 같은_상태로_전이_불가() {
        assertThat(EnrollmentStatus.PENDING.canTransitionTo(EnrollmentStatus.PENDING)).isFalse();
        assertThat(EnrollmentStatus.CONFIRMED.canTransitionTo(EnrollmentStatus.CONFIRMED)).isFalse();
        assertThat(EnrollmentStatus.CANCELLED.canTransitionTo(EnrollmentStatus.CANCELLED)).isFalse();
    }
}
