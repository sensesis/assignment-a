package com.enrollment.domain.waitlist.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistStatusTest {

    @Test
    void WAITING에서_PROMOTED로_전이_가능() {
        assertThat(WaitlistStatus.WAITING.canTransitionTo(WaitlistStatus.PROMOTED)).isTrue();
    }

    @Test
    void WAITING에서_CANCELLED로_전이_가능() {
        assertThat(WaitlistStatus.WAITING.canTransitionTo(WaitlistStatus.CANCELLED)).isTrue();
    }

    @Test
    void PROMOTED에서_어디로도_전이_불가() {
        assertThat(WaitlistStatus.PROMOTED.canTransitionTo(WaitlistStatus.WAITING)).isFalse();
        assertThat(WaitlistStatus.PROMOTED.canTransitionTo(WaitlistStatus.CANCELLED)).isFalse();
    }

    @Test
    void CANCELLED에서_어디로도_전이_불가() {
        assertThat(WaitlistStatus.CANCELLED.canTransitionTo(WaitlistStatus.WAITING)).isFalse();
        assertThat(WaitlistStatus.CANCELLED.canTransitionTo(WaitlistStatus.PROMOTED)).isFalse();
    }

    @Test
    void 같은_상태로_전이_불가() {
        assertThat(WaitlistStatus.WAITING.canTransitionTo(WaitlistStatus.WAITING)).isFalse();
        assertThat(WaitlistStatus.PROMOTED.canTransitionTo(WaitlistStatus.PROMOTED)).isFalse();
        assertThat(WaitlistStatus.CANCELLED.canTransitionTo(WaitlistStatus.CANCELLED)).isFalse();
    }
}
