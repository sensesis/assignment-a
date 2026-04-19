package com.enrollment.domain.waitlist.repository;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.domain.waitlist.entity.Waitlist;
import com.enrollment.domain.waitlist.entity.WaitlistStatus;
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

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(JpaAuditingConfig.class)
class WaitlistRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TestEntityManager em;
    @Autowired
    WaitlistRepository waitlistRepository;
    @Autowired
    ClassRepository classRepository;
    @Autowired
    UserRepository userRepository;

    private User creator;
    private User classmate;
    private User otherClassmate;
    private ClassEntity fullClass;

    @BeforeEach
    void setUp() {
        creator = userRepository.saveAndFlush(
                User.builder().name("instructor").role(UserRole.CREATOR).build()
        );
        classmate = userRepository.saveAndFlush(
                User.builder().name("student").role(UserRole.CLASSMATE).build()
        );
        otherClassmate = userRepository.saveAndFlush(
                User.builder().name("other").role(UserRole.CLASSMATE).build()
        );
        fullClass = classRepository.saveAndFlush(
                ClassEntity.builder()
                        .createdBy(creator)
                        .title("Java 기초").description("설명")
                        .price(10000).capacity(1).enrolledCount(1)
                        .startDate(LocalDate.of(2025, 3, 1))
                        .endDate(LocalDate.of(2025, 6, 30))
                        .status(ClassStatus.OPEN)
                        .build()
        );
    }

    @Test
    void save_시_id_자동_생성_및_auditing() {
        Waitlist saved = waitlistRepository.saveAndFlush(newWaiting(classmate));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(WaitlistStatus.WAITING);
    }

    @Test
    void 부분_유니크_인덱스_WAITING_중복_방지() {
        waitlistRepository.saveAndFlush(newWaiting(classmate));
        em.clear();

        Waitlist duplicate = newWaiting(classmate);
        assertThatThrownBy(() -> waitlistRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 부분_유니크_인덱스_CANCELLED_후_재등록_허용() {
        Waitlist first = waitlistRepository.saveAndFlush(newWaiting(classmate));
        first.cancel();
        waitlistRepository.saveAndFlush(first);
        em.clear();

        Waitlist secondAttempt = newWaiting(classmate);
        Waitlist saved = waitlistRepository.saveAndFlush(secondAttempt);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void chk_waitlist_promotion_PROMOTED인데_promoted_at_NULL이면_실패() throws Exception {
        // JPA 라이프사이클을 우회하기 위해 리플렉션으로 필드 직접 설정 후 flush
        Waitlist waitlist = newWaiting(classmate);
        setField(waitlist, "status", WaitlistStatus.PROMOTED);
        setField(waitlist, "promotedEnrollmentId", null);
        setField(waitlist, "promotedAt", null);

        assertThatThrownBy(() -> waitlistRepository.saveAndFlush(waitlist))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void chk_waitlist_cancelled_CANCELLED인데_cancelled_at_NULL이면_실패() throws Exception {
        Waitlist waitlist = newWaiting(classmate);
        setField(waitlist, "status", WaitlistStatus.CANCELLED);
        setField(waitlist, "cancelledAt", null);

        assertThatThrownBy(() -> waitlistRepository.saveAndFlush(waitlist))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findFirstWaitingForUpdate_FIFO_순서로_선두_조회() throws Exception {
        // 타이브레이커 확인 위해 createdAt을 동일하게 강제 설정
        Waitlist first = waitlistRepository.saveAndFlush(newWaiting(classmate));
        Waitlist second = waitlistRepository.saveAndFlush(newWaiting(otherClassmate));

        LocalDateTime sameInstant = LocalDateTime.now();
        setField(first, "createdAt", sameInstant);
        setField(second, "createdAt", sameInstant);
        waitlistRepository.saveAndFlush(first);
        waitlistRepository.saveAndFlush(second);
        em.clear();

        Optional<Waitlist> found = waitlistRepository.findFirstWaitingForUpdate(fullClass.getId());
        assertThat(found).isPresent();
        // id 타이브레이커로 더 작은 id(first)가 선두
        assertThat(found.get().getId()).isEqualTo(first.getId());
        // JOIN FETCH로 user 즉시 로딩
        assertThat(found.get().getUser().getName()).isEqualTo("student");
    }

    @Test
    void findFirstWaitingForUpdate_createdAt_ASC_정렬() throws Exception {
        Waitlist earlier = waitlistRepository.saveAndFlush(newWaiting(classmate));
        Waitlist later = waitlistRepository.saveAndFlush(newWaiting(otherClassmate));

        LocalDateTime base = LocalDateTime.now();
        setField(earlier, "createdAt", base.minusMinutes(5));
        setField(later, "createdAt", base);
        waitlistRepository.saveAndFlush(earlier);
        waitlistRepository.saveAndFlush(later);
        em.clear();

        Optional<Waitlist> found = waitlistRepository.findFirstWaitingForUpdate(fullClass.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(earlier.getId());
    }

    @Test
    void existsByClassIdAndUserIdAndStatus_WAITING_존재하면_true() {
        waitlistRepository.saveAndFlush(newWaiting(classmate));
        em.clear();

        boolean exists = waitlistRepository.existsByClassIdAndUserIdAndStatus(
                fullClass.getId(), classmate.getId(), WaitlistStatus.WAITING);
        assertThat(exists).isTrue();
    }

    @Test
    void existsByClassIdAndUserIdAndStatus_미존재시_false() {
        boolean exists = waitlistRepository.existsByClassIdAndUserIdAndStatus(
                fullClass.getId(), classmate.getId(), WaitlistStatus.WAITING);
        assertThat(exists).isFalse();
    }

    @Test
    void findByClassIdAndUserIdAndStatus_조회_성공() {
        waitlistRepository.saveAndFlush(newWaiting(classmate));
        em.clear();

        Optional<Waitlist> found = waitlistRepository.findByClassIdAndUserIdAndStatus(
                fullClass.getId(), classmate.getId(), WaitlistStatus.WAITING);
        assertThat(found).isPresent();
    }

    private Waitlist newWaiting(User user) {
        return Waitlist.builder()
                .classEntity(fullClass)
                .user(user)
                .status(WaitlistStatus.WAITING)
                .build();
    }

    private void setField(Object obj, String name, Object value) throws Exception {
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
