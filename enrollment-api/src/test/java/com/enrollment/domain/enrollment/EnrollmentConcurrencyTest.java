package com.enrollment.domain.enrollment;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.dto.EnrollmentCreateRequest;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.enrollment.repository.EnrollmentRepository;
import com.enrollment.domain.enrollment.service.EnrollmentService;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnrollmentConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    EnrollmentService enrollmentService;
    @Autowired
    ClassRepository classRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    EnrollmentRepository enrollmentRepository;

    private Long classId;
    private List<Long> classmateIds;

    @BeforeEach
    void setUp() {
        User creator = userRepository.saveAndFlush(
                User.builder().name("instructor").role(UserRole.CREATOR).build());

        // 10명의 학생이 동시에 같은 강의에 신청 — 중복 방지 인덱스 우회를 위해 유저는 서로 다름
        classmateIds = IntStream.range(0, 10)
                .mapToObj(i -> userRepository.saveAndFlush(
                        User.builder().name("학생" + i).role(UserRole.CLASSMATE).build()).getId())
                .toList();

        ClassEntity openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("마감_임박_강의").description("정원 1석")
                        .price(10000).capacity(1).enrolledCount(0)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build());

        classId = openClass.getId();
    }

    @Test
    void 정원_1명_강의에_10명이_동시_신청_시_정확히_1명만_성공() throws Exception {
        int threadCount = classmateIds.size();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);

        for (Long classmateId : classmateIds) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    enrollmentService.enroll(classmateId, new EnrollmentCreateRequest(classId));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // BusinessException(CAPACITY_EXCEEDED) + 락/트랜잭션 예외 모두 실패로 집계
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        ClassEntity finalClass = classRepository.findById(classId).orElseThrow();
        assertThat(finalClass.getEnrolledCount()).isEqualTo(1);

        long activeEnrollments = enrollmentRepository.findByClassEntityId(classId, PageRequest.of(0, 20))
                .stream()
                .filter(e -> e.getStatus() != EnrollmentStatus.CANCELLED)
                .count();
        assertThat(activeEnrollments).isEqualTo(1);
    }
}
