package com.enrollment.domain.user;

import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
import com.enrollment.global.config.JpaAuditingConfig;
import com.enrollment.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(JpaAuditingConfig.class)
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    TestEntityManager em;
    @Autowired
    UserRepository userRepository;

    @Test
    void save_시_id_자동_생성() {
        User saved = userRepository.saveAndFlush(
            User.builder().name("kim").role(UserRole.CLASSMATE).build()
        );
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findById_왕복() {
        Long id = userRepository.saveAndFlush(
            User.builder().name("kim").role(UserRole.CLASSMATE).build()
        ).getId();
        em.clear();
        User found = userRepository.findById(id).orElseThrow();
        assertThat(found.getName()).isEqualTo("kim");
        assertThat(found.getRole()).isEqualTo(UserRole.CLASSMATE);
    }

    @Test
    void Auditing_자동_주입() {
        User saved = userRepository.saveAndFlush(
            User.builder().name("kim").role(UserRole.CREATOR).build()
        );
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void name_null_시_제약_위반() {
        User invalid = User.builder().role(UserRole.CLASSMATE).build();
        assertThatThrownBy(() -> userRepository.saveAndFlush(invalid))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
