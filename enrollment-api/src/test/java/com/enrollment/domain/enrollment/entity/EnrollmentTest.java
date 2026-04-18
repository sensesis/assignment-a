package com.enrollment.domain.enrollment.entity;

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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

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
                .capacity(30)
                .enrolledCount(0)
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
        void create_시_PENDING_상태와_enrolledAt_설정() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(enrollment.getEnrolledAt()).isNotNull();
            assertThat(enrollment.getConfirmedAt()).isNull();
            assertThat(enrollment.getCancelledAt()).isNull();
            assertThat(enrollment.getClassEntity()).isEqualTo(openClass);
            assertThat(enrollment.getUser()).isEqualTo(classmate);
        }
    }

    @Nested
    class Confirm {

        @Test
        void PENDING에서_confirm_성공() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.confirm();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(enrollment.getConfirmedAt()).isNotNull();
        }

        @Test
        void CONFIRMED에서_confirm_시_INVALID_STATE_TRANSITION() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.confirm();
            assertThatThrownBy(enrollment::confirm)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void CANCELLED에서_confirm_시_INVALID_STATE_TRANSITION() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.cancel();
            assertThatThrownBy(enrollment::confirm)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Cancel {

        @Test
        void PENDING에서_cancel_성공() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.cancel();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollment.getCancelledAt()).isNotNull();
        }

        @Test
        void CONFIRMED_7일_이내_cancel_성공() throws Exception {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.confirm();
            // confirmedAt을 6일 전으로 설정
            setField(enrollment, "confirmedAt", LocalDateTime.now().minusDays(6));

            enrollment.cancel();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollment.getCancelledAt()).isNotNull();
        }

        @Test
        void CONFIRMED_7일_초과_cancel_시_CANCEL_PERIOD_EXPIRED() throws Exception {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.confirm();
            // confirmedAt을 8일 전으로 설정
            setField(enrollment, "confirmedAt", LocalDateTime.now().minusDays(8));

            assertThatThrownBy(enrollment::cancel)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_PERIOD_EXPIRED));
        }

        @Test
        void CONFIRMED_정확히_7일째_cancel_허용() throws Exception {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.confirm();
            // 7일 경계 바로 안쪽 → plusDays(7).isBefore(now) == false → 허용
            // (테스트 실행 중 now()가 아주 미세하게 진행되므로 5초 버퍼 부여)
            setField(enrollment, "confirmedAt", LocalDateTime.now().minusDays(7).plusSeconds(5));

            enrollment.cancel();
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        void CONFIRMED_7일_1초_후_cancel_실패() throws Exception {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.confirm();
            // 7일 + 1초 경과 → CANCEL_PERIOD_EXPIRED
            setField(enrollment, "confirmedAt", LocalDateTime.now().minusDays(7).minusSeconds(1));

            assertThatThrownBy(enrollment::cancel)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_PERIOD_EXPIRED));
        }

        @Test
        void CANCELLED에서_cancel_시_ALREADY_CANCELLED() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.cancel();
            assertThatThrownBy(enrollment::cancel)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ALREADY_CANCELLED));
        }
    }

    @Nested
    class ValidateOwner {

        @Test
        void 소유자_일치_시_예외_없음() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            enrollment.validateOwner(2L);
        }

        @Test
        void 소유자_불일치_시_NOT_ENROLLMENT_OWNER_예외() {
            Enrollment enrollment = newEnrollment(openClass, classmate);
            assertThatThrownBy(() -> enrollment.validateOwner(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_ENROLLMENT_OWNER));
        }
    }

    private Enrollment newEnrollment(ClassEntity classEntity, User user) {
        return Enrollment.builder()
                .classEntity(classEntity)
                .user(user)
                .status(EnrollmentStatus.PENDING)
                .enrolledAt(LocalDateTime.now())
                .build();
    }

    private void setId(Object obj, Long id) throws Exception {
        Field field = obj.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(obj, id);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
