package com.enrollment.domain.waitlist.entity;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WaitlistTest {

    private User classmate;
    private ClassEntity openClass;

    @BeforeEach
    void setUp() throws Exception {
        User creator = User.builder().name("instructor").role(UserRole.CREATOR).build();
        setId(creator, 1L);

        classmate = User.builder().name("student").role(UserRole.CLASSMATE).build();
        setId(classmate, 2L);

        openClass = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초")
                .description("설명")
                .price(10000)
                .capacity(1)
                .enrolledCount(1)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT)
                .build();
        setId(openClass, 10L);
        openClass.publish();
    }

    @Nested
    class Create {

        @Test
        void create_시_WAITING_상태와_null_필드() {
            Waitlist waitlist = newWaitlist();
            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.WAITING);
            assertThat(waitlist.getPromotedAt()).isNull();
            assertThat(waitlist.getPromotedEnrollmentId()).isNull();
            assertThat(waitlist.getCancelledAt()).isNull();
            assertThat(waitlist.getClassEntity()).isEqualTo(openClass);
            assertThat(waitlist.getUser()).isEqualTo(classmate);
        }
    }

    @Nested
    class Promote {

        @Test
        void WAITING에서_promote_성공() {
            Waitlist waitlist = newWaitlist();
            waitlist.promote(555L);
            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
            assertThat(waitlist.getPromotedEnrollmentId()).isEqualTo(555L);
            assertThat(waitlist.getPromotedAt()).isNotNull();
        }

        @Test
        void PROMOTED에서_promote_시_INVALID_STATE_TRANSITION() {
            Waitlist waitlist = newWaitlist();
            waitlist.promote(555L);
            assertThatThrownBy(() -> waitlist.promote(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void CANCELLED에서_promote_시_INVALID_STATE_TRANSITION() {
            Waitlist waitlist = newWaitlist();
            waitlist.cancel();
            assertThatThrownBy(() -> waitlist.promote(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Cancel {

        @Test
        void WAITING에서_cancel_성공() {
            Waitlist waitlist = newWaitlist();
            waitlist.cancel();
            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
            assertThat(waitlist.getCancelledAt()).isNotNull();
        }

        @Test
        void PROMOTED에서_cancel_시_INVALID_STATE_TRANSITION() {
            Waitlist waitlist = newWaitlist();
            waitlist.promote(555L);
            assertThatThrownBy(waitlist::cancel)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void CANCELLED에서_cancel_시_ALREADY_CANCELLED() {
            Waitlist waitlist = newWaitlist();
            waitlist.cancel();
            assertThatThrownBy(waitlist::cancel)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ALREADY_CANCELLED));
        }
    }

    @Nested
    class Skip {

        @Test
        void skip_시_예외_없이_CANCELLED로_전환() {
            Waitlist waitlist = newWaitlist();
            waitlist.skip();
            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
            assertThat(waitlist.getCancelledAt()).isNotNull();
        }
    }

    @Nested
    class ValidateOwner {

        @Test
        void 소유자_일치_시_예외_없음() {
            Waitlist waitlist = newWaitlist();
            waitlist.validateOwner(2L);
        }

        @Test
        void 소유자_불일치_시_NOT_WAITLIST_OWNER_예외() {
            Waitlist waitlist = newWaitlist();
            assertThatThrownBy(() -> waitlist.validateOwner(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_WAITLIST_OWNER));
        }
    }

    private Waitlist newWaitlist() {
        return Waitlist.builder()
                .classEntity(openClass)
                .user(classmate)
                .status(WaitlistStatus.WAITING)
                .build();
    }

    private void setId(Object obj, Long id) throws Exception {
        Field field = obj.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(obj, id);
    }
}
