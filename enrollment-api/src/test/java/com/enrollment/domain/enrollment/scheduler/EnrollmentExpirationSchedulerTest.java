package com.enrollment.domain.enrollment.scheduler;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.entity.Waitlist;
import com.enrollment.domain.waitlist.entity.WaitlistStatus;
import com.enrollment.domain.waitlist.repository.WaitlistRepository;
import com.enrollment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnrollmentExpirationSchedulerTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentExpirationScheduler scheduler;
    @Autowired
    EnrollmentRepository enrollmentRepository;
    @Autowired
    ClassRepository classRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WaitlistRepository waitlistRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = userRepository.saveAndFlush(
                User.builder().name("instructor").role(UserRole.CREATOR).build());
    }

    @Test
    void 만료된_PENDING_실행_시_CANCELLED_및_enrolledCount_감소() {
        User classmate = userRepository.saveAndFlush(
                User.builder().name("student").role(UserRole.CLASSMATE).build());

        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("Java 기초").description("설명")
                        .price(10000).capacity(10).enrolledCount(1)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());

        // 만료된 PENDING 생성
        Enrollment saved = transactionTemplate.execute(s ->
                enrollmentRepository.saveAndFlush(
                        Enrollment.builder()
                                .classEntity(classRepository.findById(openClass.getId()).orElseThrow())
                                .user(userRepository.findById(classmate.getId()).orElseThrow())
                                .status(EnrollmentStatus.PENDING)
                                .enrolledAt(LocalDateTime.now().minusMinutes(31))
                                .expiresAt(LocalDateTime.now().minusMinutes(1))
                                .build()));

        // 만료 스캔 수동 실행
        scheduler.expirePendings();

        Enrollment result = enrollmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(result.getCancelledAt()).isNotNull();

        ClassEntity updatedClass = classRepository.findById(openClass.getId()).orElseThrow();
        assertThat(updatedClass.getEnrolledCount()).isEqualTo(0);
    }

    @Test
    void 만료_이후_대기자_있으면_체인_승격() {
        User pendingUser = userRepository.saveAndFlush(
                User.builder().name("pending").role(UserRole.CLASSMATE).build());
        User waitingUser = userRepository.saveAndFlush(
                User.builder().name("waiting").role(UserRole.CLASSMATE).build());

        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("인기_강의").description("정원 1")
                        .price(10000).capacity(1).enrolledCount(1)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());

        // 만료된 PENDING + WAITING 1명 사전 세팅
        Enrollment expired = transactionTemplate.execute(s ->
                enrollmentRepository.saveAndFlush(
                        Enrollment.builder()
                                .classEntity(classRepository.findById(openClass.getId()).orElseThrow())
                                .user(userRepository.findById(pendingUser.getId()).orElseThrow())
                                .status(EnrollmentStatus.PENDING)
                                .enrolledAt(LocalDateTime.now().minusMinutes(31))
                                .expiresAt(LocalDateTime.now().minusMinutes(1))
                                .build()));

        transactionTemplate.executeWithoutResult(s ->
                waitlistRepository.saveAndFlush(
                        Waitlist.builder()
                                .classEntity(classRepository.findById(openClass.getId()).orElseThrow())
                                .user(userRepository.findById(waitingUser.getId()).orElseThrow())
                                .status(WaitlistStatus.WAITING)
                                .build()));

        // 만료 스캔 실행
        scheduler.expirePendings();

        // 기존 PENDING → CANCELLED
        Enrollment expiredResult = enrollmentRepository.findById(expired.getId()).orElseThrow();
        assertThat(expiredResult.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);

        // 대기자 승격 확인
        List<Waitlist> waitlists = waitlistRepository.findAll().stream()
                .filter(w -> w.getClassEntity().getId().equals(openClass.getId()))
                .toList();
        assertThat(waitlists).hasSize(1);
        assertThat(waitlists.get(0).getStatus()).isEqualTo(WaitlistStatus.PROMOTED);

        // 새 PENDING + expiresAt 세팅 확인
        List<Enrollment> pendings = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassEntity().getId().equals(openClass.getId())
                        && e.getStatus() == EnrollmentStatus.PENDING)
                .toList();
        assertThat(pendings).hasSize(1);
        assertThat(pendings.get(0).getUser().getId()).isEqualTo(waitingUser.getId());
        assertThat(pendings.get(0).getExpiresAt()).isNotNull();
        assertThat(pendings.get(0).getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(29));

        // 좌석 유지 (만료 -1, 승격 +1 = net 0)
        ClassEntity updatedClass = classRepository.findById(openClass.getId()).orElseThrow();
        assertThat(updatedClass.getEnrolledCount()).isEqualTo(1);
    }

    @Test
    void 대기자_없으면_좌석만_해제() {
        User classmate = userRepository.saveAndFlush(
                User.builder().name("only").role(UserRole.CLASSMATE).build());

        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("여유_강의").description("정원 10")
                        .price(10000).capacity(10).enrolledCount(1)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());

        transactionTemplate.executeWithoutResult(s ->
                enrollmentRepository.saveAndFlush(
                        Enrollment.builder()
                                .classEntity(classRepository.findById(openClass.getId()).orElseThrow())
                                .user(userRepository.findById(classmate.getId()).orElseThrow())
                                .status(EnrollmentStatus.PENDING)
                                .enrolledAt(LocalDateTime.now().minusMinutes(31))
                                .expiresAt(LocalDateTime.now().minusMinutes(1))
                                .build()));

        scheduler.expirePendings();

        ClassEntity updatedClass = classRepository.findById(openClass.getId()).orElseThrow();
        assertThat(updatedClass.getEnrolledCount()).isEqualTo(0);
    }
}
