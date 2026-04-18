package com.enrollment.domain.classes.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassStatusTest {

    @Test
    void DRAFT에서_OPEN으로_전이_가능() {
        assertThat(ClassStatus.DRAFT.canTransitionTo(ClassStatus.OPEN)).isTrue();
    }

    @Test
    void OPEN에서_CLOSED로_전이_가능() {
        assertThat(ClassStatus.OPEN.canTransitionTo(ClassStatus.CLOSED)).isTrue();
    }

    @Test
    void DRAFT에서_CLOSED로_전이_불가() {
        assertThat(ClassStatus.DRAFT.canTransitionTo(ClassStatus.CLOSED)).isFalse();
    }

    @Test
    void OPEN에서_DRAFT로_전이_불가() {
        assertThat(ClassStatus.OPEN.canTransitionTo(ClassStatus.DRAFT)).isFalse();
    }

    @Test
    void CLOSED에서_어디로도_전이_불가() {
        assertThat(ClassStatus.CLOSED.canTransitionTo(ClassStatus.DRAFT)).isFalse();
        assertThat(ClassStatus.CLOSED.canTransitionTo(ClassStatus.OPEN)).isFalse();
    }

    @Test
    void 같은_상태로_전이_불가() {
        assertThat(ClassStatus.DRAFT.canTransitionTo(ClassStatus.DRAFT)).isFalse();
        assertThat(ClassStatus.OPEN.canTransitionTo(ClassStatus.OPEN)).isFalse();
        assertThat(ClassStatus.CLOSED.canTransitionTo(ClassStatus.CLOSED)).isFalse();
    }
}
