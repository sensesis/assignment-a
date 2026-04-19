package com.enrollment.domain.waitlist;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.enrollment.service.EnrollmentService;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.entity.Waitlist;
import com.enrollment.domain.waitlist.entity.WaitlistStatus;
import com.enrollment.domain.waitlist.repository.WaitlistRepository;
import com.enrollment.domain.waitlist.service.WaitlistService;
import com.enrollment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WaitlistConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentService enrollmentService;
    @Autowired
    WaitlistService waitlistService;
    @Autowired
    ClassRepository classRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    EnrollmentRepository enrollmentRepository;
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
    void 정원_1_CONFIRMED_1명_대기_3명_상태에서_1건_취소_시_정확히_1명_승격() throws Exception {
        // 정원 1, CONFIRMED 1명 + WAITING 3명
        User confirmedUser = userRepository.saveAndFlush(
                User.builder().name("confirmed").role(UserRole.CLASSMATE).build());
        List<Long> waitingUserIds = IntStream.range(0, 3)
                .mapToObj(i -> userRepository.saveAndFlush(
                        User.builder().name("waiting" + i).role(UserRole.CLASSMATE).build()).getId())
                .toList();

        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("마감_강의").description("정원 1")
                        .price(10000).capacity(1).enrolledCount(1)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());
        Long classId = openClass.getId();

        // CONFIRMED 수강 신청 선삽입
        Enrollment confirmed = transactionTemplate.execute(status ->
                enrollmentRepository.saveAndFlush(
                        Enrollment.builder()
                                .classEntity(classRepository.findById(classId).orElseThrow())
                                .user(userRepository.findById(confirmedUser.getId()).orElseThrow())
                                .status(EnrollmentStatus.CONFIRMED)
                                .enrolledAt(LocalDateTime.now())
                                .confirmedAt(LocalDateTime.now())
                                .build()));

        // WAITING 3명 선삽입
        for (Long waitingId : waitingUserIds) {
            transactionTemplate.executeWithoutResult(s ->
                    waitlistRepository.saveAndFlush(
                            Waitlist.builder()
                                    .classEntity(classRepository.findById(classId).orElseThrow())
                                    .user(userRepository.findById(waitingId).orElseThrow())
                                    .status(WaitlistStatus.WAITING)
                                    .build()));
        }

        // CONFIRMED 취소 실행
        enrollmentService.cancel(confirmedUser.getId(), confirmed.getId());

        // 검증
        ClassEntity finalClass = classRepository.findById(classId).orElseThrow();
        assertThat(finalClass.getEnrolledCount()).isEqualTo(1);

        long promotedCount = waitlistRepository.findAll().stream()
                .filter(w -> w.getClassEntity().getId().equals(classId)
                        && w.getStatus() == WaitlistStatus.PROMOTED)
                .count();
        assertThat(promotedCount).isEqualTo(1);

        long pendingEnrollments = enrollmentRepository.findAll().stream()
                .filter(e -> e.getClassEntity().getId().equals(classId)
                        && e.getStatus() == EnrollmentStatus.PENDING)
                .count();
        assertThat(pendingEnrollments).isEqualTo(1);
    }

    @Test
    void 정원_10_CONFIRMED_10명_대기_10명_상태에서_2건_동시_취소_시_2명_승격_net_zero() throws Exception {
        // 정원 10, CONFIRMED 10 + WAITING 10
        List<Long> confirmedUserIds = IntStream.range(0, 10)
                .mapToObj(i -> userRepository.saveAndFlush(
                        User.builder().name("confirmed" + i).role(UserRole.CLASSMATE).build()).getId())
                .toList();
        List<Long> waitingUserIds = IntStream.range(0, 10)
                .mapToObj(i -> userRepository.saveAndFlush(
                        User.builder().name("waiting" + i).role(UserRole.CLASSMATE).build()).getId())
                .toList();

        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("인기_강의").description("정원 10")
                        .price(10000).capacity(10).enrolledCount(10)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());
        Long classId = openClass.getId();

        // CONFIRMED 10명 선삽입
        List<Long> confirmedEnrollmentIds = confirmedUserIds.stream()
                .map(uid -> transactionTemplate.execute(s ->
                        enrollmentRepository.saveAndFlush(
                                Enrollment.builder()
                                        .classEntity(classRepository.findById(classId).orElseThrow())
                                        .user(userRepository.findById(uid).orElseThrow())
                                        .status(EnrollmentStatus.CONFIRMED)
                                        .enrolledAt(LocalDateTime.now())
                                        .confirmedAt(LocalDateTime.now())
                                        .build()).getId()))
                .toList();

        // WAITING 10명 선삽입
        for (Long waitingId : waitingUserIds) {
            transactionTemplate.executeWithoutResult(s ->
                    waitlistRepository.saveAndFlush(
                            Waitlist.builder()
                                    .classEntity(classRepository.findById(classId).orElseThrow())
                                    .user(userRepository.findById(waitingId).orElseThrow())
                                    .status(WaitlistStatus.WAITING)
                                    .build()));
        }

        // 동시 취소 2건
        List<Long> cancelTargets = confirmedEnrollmentIds.subList(0, 2);
        List<Long> cancelUserIds = confirmedUserIds.subList(0, 2);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.cancel(cancelUserIds.get(idx), cancelTargets.get(idx));
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(2);

        // net-zero: enrolledCount 유지 (decrement 2 + increment 2 for promotion)
        ClassEntity finalClass = classRepository.findById(classId).orElseThrow();
        assertThat(finalClass.getEnrolledCount()).isEqualTo(10);

        long promotedCount = waitlistRepository.findAll().stream()
                .filter(w -> w.getClassEntity().getId().equals(classId)
                        && w.getStatus() == WaitlistStatus.PROMOTED)
                .count();
        assertThat(promotedCount).isEqualTo(2);
    }

    @Test
    void 다른_유저_5명_동시_register_시_모두_성공() throws Exception {
        // 정원 찬 강의 준비
        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("인기_강의").description("정원 1")
                        .price(10000).capacity(1).enrolledCount(1)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());
        Long classId = openClass.getId();

        List<Long> classmateIds = IntStream.range(0, 5)
                .mapToObj(i -> userRepository.saveAndFlush(
                        User.builder().name("student" + i).role(UserRole.CLASSMATE).build()).getId())
                .toList();

        int threadCount = classmateIds.size();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (Long classmateId : classmateIds) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    waitlistService.register(classmateId, classId);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);

        long waitingCount = waitlistRepository.findAll().stream()
                .filter(w -> w.getClassEntity().getId().equals(classId)
                        && w.getStatus() == WaitlistStatus.WAITING)
                .count();
        assertThat(waitingCount).isEqualTo(5);
    }
}
