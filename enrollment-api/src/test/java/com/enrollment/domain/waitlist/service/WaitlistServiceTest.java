package com.enrollment.domain.waitlist.service;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.dto.WaitlistResponse;
import com.enrollment.domain.waitlist.entity.Waitlist;
import com.enrollment.domain.waitlist.entity.WaitlistStatus;
import com.enrollment.domain.waitlist.repository.WaitlistRepository;
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

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    WaitlistRepository waitlistRepository;
    @Mock
    EnrollmentRepository enrollmentRepository;
    @Mock
    ClassRepository classRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    WaitlistService waitlistService;

    private User creator;
    private User classmate;

    @BeforeEach
    void setUp() throws Exception {
        creator = User.builder().name("instructor").role(UserRole.CREATOR).build();
        setId(creator, 1L);
        classmate = User.builder().name("student").role(UserRole.CLASSMATE).build();
        setId(classmate, 2L);
    }

    private ClassEntity fullOpenClass(int capacity) throws Exception {
        ClassEntity entity = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초").description("설명")
                .price(10000).capacity(capacity).enrolledCount(capacity)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT).build();
        setId(entity, 10L);
        entity.publish();
        return entity;
    }

    private ClassEntity openClassWithVacancy() throws Exception {
        ClassEntity entity = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초").description("설명")
                .price(10000).capacity(30).enrolledCount(5)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT).build();
        setId(entity, 10L);
        entity.publish();
        return entity;
    }

    private ClassEntity closedClass() throws Exception {
        ClassEntity entity = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초").description("설명")
                .price(10000).capacity(1).enrolledCount(1)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT).build();
        setId(entity, 10L);
        entity.publish();
        entity.close();
        return entity;
    }

    @Nested
    class Register {

        @Test
        void 정상_등록() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findById(10L)).willReturn(Optional.of(fullClass));
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 2L)).willReturn(false);
            given(waitlistRepository.existsByClassIdAndUserIdAndStatus(
                    10L, 2L, WaitlistStatus.WAITING)).willReturn(false);

            given(waitlistRepository.save(any(Waitlist.class))).willAnswer(inv -> {
                Waitlist w = inv.getArgument(0);
                setId(w, 500L);
                return w;
            });

            WaitlistResponse response = waitlistService.register(2L, 10L);

            assertThat(response.waitlistId()).isEqualTo(500L);
            assertThat(response.classId()).isEqualTo(10L);
            assertThat(response.classTitle()).isEqualTo("Java 기초");
            assertThat(response.status()).isEqualTo("WAITING");
        }

        @Test
        void 사용자_미존재_시_USER_NOT_FOUND() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.register(999L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        void 강의_미존재_시_CLASS_NOT_FOUND() {
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findById(10L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.register(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CLASS_NOT_FOUND));
        }

        @Test
        void 강의_OPEN_아니면_WAITLIST_NOT_ALLOWED() throws Exception {
            ClassEntity closed = closedClass();
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findById(10L)).willReturn(Optional.of(closed));

            assertThatThrownBy(() -> waitlistService.register(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.WAITLIST_NOT_ALLOWED));
        }

        @Test
        void 정원_남으면_WAITLIST_NOT_ALLOWED() throws Exception {
            ClassEntity withVacancy = openClassWithVacancy();
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findById(10L)).willReturn(Optional.of(withVacancy));

            assertThatThrownBy(() -> waitlistService.register(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.WAITLIST_NOT_ALLOWED));
        }

        @Test
        void 이미_활성_수강_신청이면_ALREADY_ENROLLED() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findById(10L)).willReturn(Optional.of(fullClass));
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 2L)).willReturn(true);

            assertThatThrownBy(() -> waitlistService.register(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ALREADY_ENROLLED));
        }

        @Test
        void 이미_대기중이면_ALREADY_IN_WAITLIST() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));
            given(classRepository.findById(10L)).willReturn(Optional.of(fullClass));
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 2L)).willReturn(false);
            given(waitlistRepository.existsByClassIdAndUserIdAndStatus(
                    10L, 2L, WaitlistStatus.WAITING)).willReturn(true);

            assertThatThrownBy(() -> waitlistService.register(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.ALREADY_IN_WAITLIST));
        }
    }

    @Nested
    class CancelWaitlist {

        @Test
        void 정상_철회() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            Waitlist waitlist = Waitlist.builder()
                    .classEntity(fullClass).user(classmate)
                    .status(WaitlistStatus.WAITING).build();
            setId(waitlist, 500L);

            given(waitlistRepository.findByClassIdAndUserIdAndStatus(
                    10L, 2L, WaitlistStatus.WAITING)).willReturn(Optional.of(waitlist));

            waitlistService.cancelWaitlist(2L, 10L);

            assertThat(waitlist.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
            assertThat(waitlist.getCancelledAt()).isNotNull();
        }

        @Test
        void 대기열_미존재_시_WAITLIST_NOT_FOUND() {
            given(waitlistRepository.findByClassIdAndUserIdAndStatus(
                    10L, 2L, WaitlistStatus.WAITING)).willReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.cancelWaitlist(2L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.WAITLIST_NOT_FOUND));
        }

        @Test
        void 소유자_불일치_시_NOT_WAITLIST_OWNER() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            Waitlist waitlist = Waitlist.builder()
                    .classEntity(fullClass).user(classmate)
                    .status(WaitlistStatus.WAITING).build();
            setId(waitlist, 500L);

            given(waitlistRepository.findByClassIdAndUserIdAndStatus(
                    10L, 999L, WaitlistStatus.WAITING)).willReturn(Optional.of(waitlist));

            assertThatThrownBy(() -> waitlistService.cancelWaitlist(999L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_WAITLIST_OWNER));
        }
    }

    @Nested
    class PromoteNext {

        @Test
        void 대기열_비어있으면_no_op() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            // cancel 이후 상태로 만들기 위해 정원 1 감소
            setField(fullClass, "enrolledCount", 0);

            given(waitlistRepository.findFirstWaitingForUpdate(10L)).willReturn(Optional.empty());

            waitlistService.promoteNext(fullClass);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
            assertThat(fullClass.getEnrolledCount()).isEqualTo(0);
        }

        @Test
        void 선두_승격_시_PENDING_신청_생성_및_enrolledCount_증가() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            setField(fullClass, "enrolledCount", 0);

            User waitingUser = User.builder().name("waiter").role(UserRole.CLASSMATE).build();
            setId(waitingUser, 3L);
            Waitlist head = Waitlist.builder()
                    .classEntity(fullClass).user(waitingUser)
                    .status(WaitlistStatus.WAITING).build();
            setId(head, 500L);

            given(waitlistRepository.findFirstWaitingForUpdate(10L)).willReturn(Optional.of(head));
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 3L)).willReturn(false);
            AtomicLong idGen = new AtomicLong(777);
            given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(inv -> {
                Enrollment e = inv.getArgument(0);
                setId(e, idGen.getAndIncrement());
                return e;
            });

            waitlistService.promoteNext(fullClass);

            ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
            verify(enrollmentRepository).save(captor.capture());
            Enrollment saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(saved.getUser().getId()).isEqualTo(3L);
            assertThat(saved.getEnrolledAt()).isNotNull();

            assertThat(head.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
            assertThat(head.getPromotedEnrollmentId()).isEqualTo(777L);
            assertThat(fullClass.getEnrolledCount()).isEqualTo(1);
        }

        @Test
        void 선두_1명_skip_후_다음_승격() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            setField(fullClass, "enrolledCount", 0);

            User skipUser = User.builder().name("skip").role(UserRole.CLASSMATE).build();
            setId(skipUser, 3L);
            User secondUser = User.builder().name("second").role(UserRole.CLASSMATE).build();
            setId(secondUser, 4L);

            Waitlist skipHead = Waitlist.builder()
                    .classEntity(fullClass).user(skipUser)
                    .status(WaitlistStatus.WAITING).build();
            setId(skipHead, 500L);
            Waitlist secondHead = Waitlist.builder()
                    .classEntity(fullClass).user(secondUser)
                    .status(WaitlistStatus.WAITING).build();
            setId(secondHead, 501L);

            given(waitlistRepository.findFirstWaitingForUpdate(10L))
                    .willReturn(Optional.of(skipHead))
                    .willReturn(Optional.of(secondHead));
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 3L)).willReturn(true);
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 4L)).willReturn(false);
            given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(inv -> {
                Enrollment e = inv.getArgument(0);
                setId(e, 888L);
                return e;
            });

            waitlistService.promoteNext(fullClass);

            assertThat(skipHead.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
            assertThat(secondHead.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
            assertThat(fullClass.getEnrolledCount()).isEqualTo(1);
        }

        @Test
        void 선두_2명_연속_skip_후_3번째_승격() throws Exception {
            ClassEntity fullClass = fullOpenClass(1);
            setField(fullClass, "enrolledCount", 0);

            User a = User.builder().name("a").role(UserRole.CLASSMATE).build();
            setId(a, 3L);
            User b = User.builder().name("b").role(UserRole.CLASSMATE).build();
            setId(b, 4L);
            User c = User.builder().name("c").role(UserRole.CLASSMATE).build();
            setId(c, 5L);

            Waitlist wa = Waitlist.builder().classEntity(fullClass).user(a).status(WaitlistStatus.WAITING).build();
            setId(wa, 500L);
            Waitlist wb = Waitlist.builder().classEntity(fullClass).user(b).status(WaitlistStatus.WAITING).build();
            setId(wb, 501L);
            Waitlist wc = Waitlist.builder().classEntity(fullClass).user(c).status(WaitlistStatus.WAITING).build();
            setId(wc, 502L);

            given(waitlistRepository.findFirstWaitingForUpdate(10L))
                    .willReturn(Optional.of(wa))
                    .willReturn(Optional.of(wb))
                    .willReturn(Optional.of(wc));
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 3L)).willReturn(true);
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 4L)).willReturn(true);
            given(enrollmentRepository.existsActiveByClassIdAndUserId(10L, 5L)).willReturn(false);
            given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(inv -> {
                Enrollment e = inv.getArgument(0);
                setId(e, 999L);
                return e;
            });

            waitlistService.promoteNext(fullClass);

            assertThat(wa.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
            assertThat(wb.getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
            assertThat(wc.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
            assertThat(fullClass.getEnrolledCount()).isEqualTo(1);
        }
    }

    private void setId(Object obj, Long id) throws Exception {
        setField(obj, "id", id);
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
