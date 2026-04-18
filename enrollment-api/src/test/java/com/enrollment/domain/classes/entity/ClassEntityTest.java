package com.enrollment.domain.classes.entity;

import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClassEntityTest {

    private User owner;

    @BeforeEach
    void setUp() throws Exception {
        owner = User.builder().name("instructor").role(UserRole.CREATOR).build();
        setId(owner, 1L);
    }

    private ClassEntity validDraft() {
        return ClassEntity.builder()
                .createdBy(owner)
                .title("Java 기초")
                .description("자바 기초 강의")
                .price(10000)
                .capacity(30)
                .enrolledCount(0)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT)
                .build();
    }

    private ClassEntity openClass() {
        ClassEntity entity = validDraft();
        entity.publish();
        return entity;
    }

    @Nested
    class Publish {

        @Test
        void DRAFT_상태에서_publish_성공() {
            ClassEntity entity = validDraft();
            entity.publish();
            assertThat(entity.getStatus()).isEqualTo(ClassStatus.OPEN);
        }

        @Test
        void OPEN_상태에서_publish_실패() {
            ClassEntity entity = openClass();
            assertThatThrownBy(entity::publish)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void endDate가_startDate_이전이면_publish_실패() throws Exception {
            // Build entity with inverted dates via reflection to bypass update() validation
            ClassEntity entity = ClassEntity.builder()
                    .createdBy(owner)
                    .title("Java 기초")
                    .description("자바 기초 강의")
                    .price(10000)
                    .capacity(30)
                    .enrolledCount(0)
                    .startDate(LocalDate.of(2025, 6, 30))
                    .endDate(LocalDate.of(2025, 3, 1))
                    .status(ClassStatus.DRAFT)
                    .build();
            assertThatThrownBy(entity::publish)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }

        @Test
        void startDate_equals_endDate_publish_실패() throws Exception {
            // Build entity with equal dates via builder to bypass update() validation
            ClassEntity entity = ClassEntity.builder()
                    .createdBy(owner)
                    .title("Java 기초")
                    .description("자바 기초 강의")
                    .price(10000)
                    .capacity(30)
                    .enrolledCount(0)
                    .startDate(LocalDate.of(2025, 6, 30))
                    .endDate(LocalDate.of(2025, 6, 30))
                    .status(ClassStatus.DRAFT)
                    .build();
            assertThatThrownBy(entity::publish)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    @Nested
    class Close {

        @Test
        void OPEN_상태에서_close_성공() {
            ClassEntity entity = openClass();
            entity.close();
            assertThat(entity.getStatus()).isEqualTo(ClassStatus.CLOSED);
        }

        @Test
        void DRAFT_상태에서_close_실패() {
            ClassEntity entity = validDraft();
            assertThatThrownBy(entity::close)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Update {

        @Test
        void DRAFT_상태에서_전체_필드_수정_성공() {
            ClassEntity entity = validDraft();
            entity.update("새 제목", "새 설명", 20000, 50,
                    LocalDate.of(2025, 4, 1), LocalDate.of(2025, 7, 31));
            assertThat(entity.getTitle()).isEqualTo("새 제목");
            assertThat(entity.getDescription()).isEqualTo("새 설명");
            assertThat(entity.getPrice()).isEqualTo(20000);
            assertThat(entity.getCapacity()).isEqualTo(50);
            assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2025, 4, 1));
            assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2025, 7, 31));
        }

        @Test
        void null_필드는_기존값_유지() {
            ClassEntity entity = validDraft();
            entity.update(null, null, null, null, null, null);
            assertThat(entity.getTitle()).isEqualTo("Java 기초");
            assertThat(entity.getPrice()).isEqualTo(10000);
        }

        @Test
        void OPEN_상태에서_update_실패() {
            ClassEntity entity = openClass();
            assertThatThrownBy(() -> entity.update("새 제목", null, null, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }

        @Test
        void update_시_음수_price_INVALID_INPUT_예외() {
            ClassEntity entity = validDraft();
            assertThatThrownBy(() -> entity.update(null, null, -1, null, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }

        @Test
        void update_시_0이하_capacity_INVALID_INPUT_예외() {
            ClassEntity entity = validDraft();
            assertThatThrownBy(() -> entity.update(null, null, null, 0, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }

        @Test
        void update_시_시작일_종료일_이후면_INVALID_INPUT_예외() {
            ClassEntity entity = validDraft();
            assertThatThrownBy(() -> entity.update(null, null, null, null,
                    LocalDate.of(2025, 7, 1), LocalDate.of(2025, 3, 1)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }

        @Test
        void update_시_startDate만_갱신해_endDate_이전이_되면_INVALID_INPUT_예외() {
            // entity has endDate=2025-06-30; update startDate=2025-07-01 → startDate > endDate
            ClassEntity entity = validDraft(); // endDate = 2025-06-30
            assertThatThrownBy(() -> entity.update(null, null, null, null,
                    LocalDate.of(2025, 7, 1), null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }

        @Test
        void price_0_update_성공() {
            ClassEntity entity = validDraft();
            entity.update(null, null, 0, null, null, null);
            assertThat(entity.getPrice()).isEqualTo(0);
        }

        @Test
        void capacity_1_update_성공() {
            ClassEntity entity = validDraft();
            entity.update(null, null, null, 1, null, null);
            assertThat(entity.getCapacity()).isEqualTo(1);
        }
    }

    @Nested
    class ValidateOwner {

        @Test
        void 소유자_일치_시_예외_없음() {
            ClassEntity entity = validDraft();
            entity.validateOwner(1L);
        }

        @Test
        void 소유자_불일치_시_NOT_COURSE_OWNER_예외() {
            ClassEntity entity = validDraft();
            assertThatThrownBy(() -> entity.validateOwner(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_COURSE_OWNER));
        }

        @Test
        void createdBy_null_시_NOT_COURSE_OWNER() throws Exception {
            ClassEntity entity = validDraft();
            // Use reflection to set createdBy to null (bypassing builder)
            Field createdByField = ClassEntity.class.getDeclaredField("createdBy");
            createdByField.setAccessible(true);
            createdByField.set(entity, null);

            assertThatThrownBy(() -> entity.validateOwner(1L))
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
}
