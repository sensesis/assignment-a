package com.enrollment.domain.enrollment.service;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.dto.EnrollmentCreateRequest;
import com.enrollment.domain.enrollment.dto.EnrollmentResponse;
import com.enrollment.domain.enrollment.dto.EnrollmentWithUserResponse;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.payment.entity.Payment;
import com.enrollment.domain.payment.entity.PaymentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.payment.repository.PaymentRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.service.WaitlistService;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    EnrollmentRepository enrollmentRepository;
    @Mock
    PaymentRepository paymentRepository;
    @Mock
    ClassRepository classRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    WaitlistService waitlistService;
    @InjectMocks
    EnrollmentService enrollmentService;

    private User creator;
    private User classmate;

    @BeforeEach
    void setUp() throws Exception {
        creator = User.builder().name("instructor").role(UserRole.CREATOR).build();
        setId(creator, 1L);
        classmate = User.builder().name("student").role(UserRole.CLASSMATE).build();
        setId(classmate, 2L);
    }

    private ClassEntity openClass(int capacity, int enrolled) throws Exception {
        ClassEntity entity = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초").description("설명")
                .price(10000).capacity(capacity).enrolledCount(enrolled)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT).build();
        setId(entity, 10L);
        entity.publish();
        return entity;
    }

    private ClassEntity draftClass() throws Exception {
        ClassEntity entity = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초").description("설명")
                .price(10000).capacity(30).enrolledCount(0)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT).build();
        setId(entity, 10L);
        return entity;
    }

    private Enrollment pendingEnrollment(ClassEntity classEntity) throws Exception {
        Enrollment enrollment = Enrollment.builder()
                .classEntity(classEntity)
                .user(classmate)
                .status(EnrollmentStatus.PENDING)
                .enrolledAt(LocalDateTime.now())
                .build();
        setId(enrollment, 100L);
        return enrollment;
    }

    @Nested
    class Enroll {

        @Test
        void 정상_신청() throws Exception {
            ClassEntity openClass = openClass(30, 0);
            EnrollmentCreateRequest request = new EnrollmentCreateRequest(10L);

            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(openClass));
            given(enrollmentRepository.existsByClassIdAndUserIdAndStatusIn(
                    eq(10L), eq(2L), anyCollection())).willReturn(false);
            given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(inv -> {
                Enrollment e = inv.getArgument(0);
                try { setId(e, 100L); } catch (Exception ignored) {}
                return e;
            });

            EnrollmentResponse response = enrollmentService.enroll(2L, request);

            assertThat(response.enrollmentId()).isEqualTo(100L);
            assertThat(response.classId()).isEqualTo(10L);
            assertThat(response.status()).isEqualTo("PENDING");
            assertThat(openClass.getEnrolledCount()).isEqualTo(1);
        }

        @Test
        void 사용자_미존재_시_USER_NOT_FOUND() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enroll(999L, new EnrollmentCreateRequest(10L)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        void 강의_미존재_시_CLASS_NOT_FOUND() {
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentCreateRequest(10L)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CLASS_NOT_FOUND));
        }

        @Test
        void 강의가_OPEN이_아니면_INVALID_STATE_TRANSITION() throws Exception {
            ClassEntity draft = draftClass();
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(draft));

            assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentCreateRequest(10L)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void 이미_신청한_강의면_ALREADY_ENROLLED() throws Exception {
            ClassEntity openClass = openClass(30, 0);
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(openClass));
            given(enrollmentRepository.existsByClassIdAndUserIdAndStatusIn(
                    eq(10L), eq(2L), anyCollection())).willReturn(true);

            assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentCreateRequest(10L)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ALREADY_ENROLLED));
        }

        @Test
        void 정원_초과_시_CAPACITY_EXCEEDED() throws Exception {
            ClassEntity fullClass = openClass(2, 2);
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(fullClass));
            given(enrollmentRepository.existsByClassIdAndUserIdAndStatusIn(
                    eq(10L), eq(2L), anyCollection())).willReturn(false);

            assertThatThrownBy(() -> enrollmentService.enroll(2L, new EnrollmentCreateRequest(10L)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CAPACITY_EXCEEDED));
            verify(enrollmentRepository, never()).save(any(Enrollment.class));
        }
    }

    @Nested
    class Pay {

        @Test
        void 정상_결제() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

            EnrollmentResponse response = enrollmentService.pay(2L, 100L);

            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment saved = paymentCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(saved.getAmount()).isEqualTo(10000);
        }

        @Test
        void 신청_미존재_시_ENROLLMENT_NOT_FOUND() {
            given(enrollmentRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.pay(2L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND));
        }

        @Test
        void 소유자_불일치_시_NOT_ENROLLMENT_OWNER() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.pay(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_ENROLLMENT_OWNER));
        }

        @Test
        void CONFIRMED_상태에서_재결제_시_INVALID_STATE_TRANSITION() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            enrollment.confirm();
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.pay(2L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void 만료된_PENDING_결제_시_HOLD_EXPIRED() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            setField(enrollment, "expiresAt", LocalDateTime.now().minusMinutes(1));
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.pay(2L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.HOLD_EXPIRED));
        }
    }

    @Nested
    class Cancel {

        @Test
        void PENDING_취소_환불없음() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(openClass));

            EnrollmentResponse response = enrollmentService.cancel(2L, 100L);

            assertThat(response.status()).isEqualTo("CANCELLED");
            assertThat(openClass.getEnrolledCount()).isEqualTo(0);
            verify(paymentRepository, never()).save(any(Payment.class));
        }

        @Test
        void CONFIRMED_7일_이내_취소_환불_발생() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            enrollment.confirm();
            setField(enrollment, "confirmedAt", LocalDateTime.now().minusDays(3));
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(openClass));
            given(paymentRepository.save(any(Payment.class))).willAnswer(inv -> inv.getArgument(0));

            EnrollmentResponse response = enrollmentService.cancel(2L, 100L);

            assertThat(response.status()).isEqualTo("CANCELLED");
            assertThat(openClass.getEnrolledCount()).isEqualTo(0);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        void CONFIRMED_7일_초과_시_CANCEL_PERIOD_EXPIRED() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            enrollment.confirm();
            setField(enrollment, "confirmedAt", LocalDateTime.now().minusDays(8));
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(openClass));

            assertThatThrownBy(() -> enrollmentService.cancel(2L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CANCEL_PERIOD_EXPIRED));
            verify(paymentRepository, never()).save(any(Payment.class));
            assertThat(openClass.getEnrolledCount()).isEqualTo(1);
        }

        @Test
        void 소유자_불일치_시_NOT_ENROLLMENT_OWNER() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancel(999L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_ENROLLMENT_OWNER));
        }

        @Test
        void 신청_미존재_시_ENROLLMENT_NOT_FOUND() {
            given(enrollmentRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.cancel(2L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND));
        }

        @Test
        void 이미_취소된_신청이면_ALREADY_CANCELLED() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            enrollment.cancel();
            given(enrollmentRepository.findByIdForUpdate(100L)).willReturn(Optional.of(enrollment));
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(openClass));

            assertThatThrownBy(() -> enrollmentService.cancel(2L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ALREADY_CANCELLED));
        }
    }

    @Nested
    class GetMyEnrollments {

        @Test
        void 내_신청_목록_조회_성공() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            Page<Enrollment> page = new PageImpl<>(List.of(enrollment));
            Pageable pageable = PageRequest.of(0, 20);

            given(userRepository.existsById(2L)).willReturn(true);
            given(enrollmentRepository.findByUserId(2L, pageable)).willReturn(page);

            Page<EnrollmentResponse> result = enrollmentService.getMyEnrollments(2L, pageable);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).classId()).isEqualTo(10L);
        }

        @Test
        void 사용자_미존재_시_USER_NOT_FOUND() {
            given(userRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> enrollmentService.getMyEnrollments(999L, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    class GetClassEnrollments {

        @Test
        void 강사_본인_강의_수강생_목록_조회_성공() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            Enrollment enrollment = pendingEnrollment(openClass);
            Page<Enrollment> page = new PageImpl<>(List.of(enrollment));
            Pageable pageable = PageRequest.of(0, 20);

            given(classRepository.findWithCreatorById(10L)).willReturn(Optional.of(openClass));
            given(enrollmentRepository.findByClassEntityId(10L, pageable)).willReturn(page);

            Page<EnrollmentWithUserResponse> result = enrollmentService.getClassEnrollments(1L, 10L, pageable);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).userName()).isEqualTo("student");
        }

        @Test
        void 강의_미존재_시_CLASS_NOT_FOUND() {
            given(classRepository.findWithCreatorById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.getClassEnrollments(1L, 999L, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CLASS_NOT_FOUND));
        }

        @Test
        void 강사_소유자_아니면_NOT_COURSE_OWNER() throws Exception {
            ClassEntity openClass = openClass(30, 1);
            given(classRepository.findWithCreatorById(10L)).willReturn(Optional.of(openClass));

            assertThatThrownBy(() -> enrollmentService.getClassEnrollments(999L, 10L, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_COURSE_OWNER));
        }
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
