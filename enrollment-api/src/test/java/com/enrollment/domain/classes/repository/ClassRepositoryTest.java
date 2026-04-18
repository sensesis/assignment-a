package com.enrollment.domain.classes.repository;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(JpaAuditingConfig.class)
class ClassRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TestEntityManager em;
    @Autowired
    ClassRepository classRepository;
    @Autowired
    UserRepository userRepository;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = userRepository.saveAndFlush(
                User.builder().name("instructor").role(UserRole.CREATOR).build()
        );
    }

    private ClassEntity buildClass(ClassStatus status) {
        return ClassEntity.builder()
                .createdBy(creator)
                .title("Test Class")
                .description("desc")
                .price(10000)
                .capacity(30)
                .enrolledCount(0)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(status)
                .build();
    }

    @Test
    void save_시_id_자동_생성_및_auditing() {
        ClassEntity saved = classRepository.saveAndFlush(buildClass(ClassStatus.DRAFT));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_왕복() {
        Long id = classRepository.saveAndFlush(buildClass(ClassStatus.DRAFT)).getId();
        em.clear();
        ClassEntity found = classRepository.findById(id).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("Test Class");
        assertThat(found.getStatus()).isEqualTo(ClassStatus.DRAFT);
    }

    @Test
    void findByIdForUpdate_비관적_잠금_조회() {
        Long id = classRepository.saveAndFlush(buildClass(ClassStatus.OPEN)).getId();
        em.clear();
        Optional<ClassEntity> found = classRepository.findByIdForUpdate(id);
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(ClassStatus.OPEN);
        // JOIN FETCH must load createdBy eagerly — no LazyInitializationException
        assertThat(found.get().getCreatedBy().getName()).isEqualTo("instructor");
    }

    @Test
    void findWithCreatorById_createdBy_즉시_로딩_확인() {
        Long id = classRepository.saveAndFlush(buildClass(ClassStatus.DRAFT)).getId();
        em.clear();
        Optional<ClassEntity> found = classRepository.findWithCreatorById(id);
        assertThat(found).isPresent();
        // EntityGraph must load createdBy eagerly — no LazyInitializationException
        assertThat(found.get().getCreatedBy().getName()).isEqualTo("instructor");
    }

    @Test
    void findWithCreatorById_미존재시_empty() {
        Optional<ClassEntity> found = classRepository.findWithCreatorById(9999L);
        assertThat(found).isEmpty();
    }

    @Test
    void findByStatus_필터링() {
        classRepository.saveAndFlush(buildClass(ClassStatus.DRAFT));
        classRepository.saveAndFlush(buildClass(ClassStatus.OPEN));
        classRepository.saveAndFlush(buildClass(ClassStatus.OPEN));
        em.clear();

        Page<ClassEntity> openClasses = classRepository.findByStatus(ClassStatus.OPEN, PageRequest.of(0, 10));
        assertThat(openClasses.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByStatus_createdBy_즉시_로딩_확인() {
        classRepository.saveAndFlush(buildClass(ClassStatus.OPEN));
        em.clear();

        Page<ClassEntity> result = classRepository.findByStatus(ClassStatus.OPEN, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        // LazyInitializationException 없이 createdBy.getName() 접근 가능해야 함
        assertThat(result.getContent().get(0).getCreatedBy().getName()).isEqualTo("instructor");
    }

    @Test
    void findByCreatedById_createdBy_즉시_로딩_확인() {
        classRepository.saveAndFlush(buildClass(ClassStatus.DRAFT));
        em.clear();

        Page<ClassEntity> result = classRepository.findByCreatedById(creator.getId(), PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getCreatedBy().getName()).isEqualTo("instructor");
    }

    @Test
    void findByCreatedById_내_강의_조회() {
        User other = userRepository.saveAndFlush(
                User.builder().name("other").role(UserRole.CREATOR).build()
        );
        classRepository.saveAndFlush(buildClass(ClassStatus.DRAFT));
        classRepository.saveAndFlush(ClassEntity.builder()
                .createdBy(other).title("Other Class").description("desc")
                .price(5000).capacity(20).enrolledCount(0)
                .startDate(LocalDate.of(2025, 3, 1)).endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT).build());
        em.clear();

        Page<ClassEntity> myClasses = classRepository.findByCreatedById(creator.getId(), PageRequest.of(0, 10));
        assertThat(myClasses.getTotalElements()).isEqualTo(1);
        assertThat(myClasses.getContent().get(0).getTitle()).isEqualTo("Test Class");
    }
}
