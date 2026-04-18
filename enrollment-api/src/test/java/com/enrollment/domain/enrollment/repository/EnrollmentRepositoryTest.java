package com.enrollment.domain.enrollment.repository;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.global.config.JpaAuditingConfig;
import com.enrollment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(JpaAuditingConfig.class)
class EnrollmentRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TestEntityManager em;
    @Autowired
    EnrollmentRepository enrollmentRepository;
    @Autowired
    ClassRepository classRepository;
    @Autowired
    UserRepository userRepository;

    private User creator;
    private User classmate;
    private ClassEntity openClass;

    @BeforeEach
    void setUp() {
        creator = userRepository.saveAndFlush(
                User.builder().name("instructor").role(UserRole.CREATOR).build()
        );
        classmate = userRepository.saveAndFlush(
                User.builder().name("student").role(UserRole.CLASSMATE).build()
        );
        openClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("Java 기초").description("설명")
                        .price(10000).capacity(30).enrolledCount(0)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build()
        );
    }

    @Test
    void save_시_id_자동_생성_및_auditing() {
        Enrollment saved = enrollmentRepository.saveAndFlush(
                newEnrollment(openClass, classmate));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEnrolledAt()).isNotNull();
    }

    @Test
    void findByIdForUpdate_JOIN_FETCH로_classEntity와_user_즉시_로딩() {
        Long id = enrollmentRepository.saveAndFlush(
                newEnrollment(openClass, classmate)).getId();
        em.clear();

        Optional<Enrollment> found = enrollmentRepository.findByIdForUpdate(id);
        assertThat(found).isPresent();
        // LazyInitializationException 없이 접근 가능해야 함
        assertThat(found.get().getClassEntity().getTitle()).isEqualTo("Java 기초");
        assertThat(found.get().getUser().getName()).isEqualTo("student");
    }

    @Test
    void findByUserId_페이지네이션_성공() {
        enrollmentRepository.saveAndFlush(newEnrollment(openClass, classmate));
        em.clear();

        Page<Enrollment> result = enrollmentRepository.findByUserId(
                classmate.getId(), PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        // EntityGraph must load classEntity eagerly
        assertThat(result.getContent().get(0).getClassEntity().getTitle()).isEqualTo("Java 기초");
    }

    @Test
    void findByClassEntityId_페이지네이션_성공() {
        enrollmentRepository.saveAndFlush(newEnrollment(openClass, classmate));
        em.clear();

        Page<Enrollment> result = enrollmentRepository.findByClassEntityId(
                openClass.getId(), PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        // EntityGraph must load user eagerly
        assertThat(result.getContent().get(0).getUser().getName()).isEqualTo("student");
    }

    @Test
    void existsByClassIdAndUserIdAndStatusIn_중복_체크() {
        enrollmentRepository.saveAndFlush(newEnrollment(openClass, classmate));
        em.clear();

        boolean exists = enrollmentRepository.existsByClassIdAndUserIdAndStatusIn(
                openClass.getId(), classmate.getId(),
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED));
        assertThat(exists).isTrue();
    }

    @Test
    void existsByClassIdAndUserIdAndStatusIn_미존재시_false() {
        boolean exists = enrollmentRepository.existsByClassIdAndUserIdAndStatusIn(
                openClass.getId(), classmate.getId(),
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED));
        assertThat(exists).isFalse();
    }

    @Test
    void 부분_유니크_인덱스_활성_상태_중복_방지() {
        enrollmentRepository.saveAndFlush(newEnrollment(openClass, classmate));
        em.clear();

        Enrollment duplicate = newEnrollment(openClass, classmate);
        assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 부분_유니크_인덱스_CANCELLED_후_재신청_허용() {
        Enrollment first = enrollmentRepository.saveAndFlush(
                newEnrollment(openClass, classmate));
        first.cancel();
        enrollmentRepository.saveAndFlush(first);
        em.clear();

        Enrollment secondAttempt = newEnrollment(openClass, classmate);
        Enrollment saved = enrollmentRepository.saveAndFlush(secondAttempt);
        assertThat(saved.getId()).isNotNull();
    }

    private Enrollment newEnrollment(ClassEntity classEntity, User user) {
        return Enrollment.builder()
                .classEntity(classEntity)
                .user(user)
                .status(EnrollmentStatus.PENDING)
                .enrolledAt(LocalDateTime.now())
                .build();
    }
}
