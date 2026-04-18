package com.enrollment.domain.payment.entity;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private Enrollment enrollment;

    @BeforeEach
    void setUp() throws Exception {
        User creator = User.builder().name("instructor").role(UserRole.CREATOR).build();
        setId(creator, 1L);

        User classmate = User.builder().name("student").role(UserRole.CLASSMATE).build();
        setId(classmate, 2L);

        ClassEntity openClass = ClassEntity.builder()
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

        enrollment = Enrollment.builder()
                .classEntity(openClass)
                .user(classmate)
                .status(EnrollmentStatus.PENDING)
                .enrolledAt(LocalDateTime.now())
                .build();
    }

    @Test
    void paid_팩토리_메서드_PAID_상태_생성() {
        Payment payment = Payment.paid(enrollment, 10000);
        assertThat(payment.getEnrollment()).isEqualTo(enrollment);
        assertThat(payment.getAmount()).isEqualTo(10000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void refunded_팩토리_메서드_REFUNDED_상태_생성() {
        Payment payment = Payment.refunded(enrollment, 10000);
        assertThat(payment.getEnrollment()).isEqualTo(enrollment);
        assertThat(payment.getAmount()).isEqualTo(10000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    private void setId(Object obj, Long id) throws Exception {
        Field field = obj.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(obj, id);
    }
}
