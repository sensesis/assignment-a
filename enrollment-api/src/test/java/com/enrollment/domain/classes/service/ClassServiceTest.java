package com.enrollment.domain.classes.service;

import com.enrollment.domain.classes.dto.ClassCreateRequest;
import com.enrollment.domain.classes.dto.ClassResponse;
import com.enrollment.domain.classes.dto.ClassUpdateRequest;
import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import com.enrollment.domain.classes.repository.ClassRepository;
import com.enrollment.domain.user.entity.User;
import com.enrollment.domain.user.entity.UserRole;
import com.enrollment.domain.user.repository.UserRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

    @Mock
    ClassRepository classRepository;
    @Mock
    UserRepository userRepository;
    @InjectMocks
    ClassService classService;

    private User creator;

    @BeforeEach
    void setUp() throws Exception {
        creator = User.builder().name("instructor").role(UserRole.CREATOR).build();
        setId(creator, 1L);
    }

    private ClassEntity draftEntity() throws Exception {
        ClassEntity entity = ClassEntity.builder()
                .createdBy(creator)
                .title("Java 기초")
                .description("설명")
                .price(10000)
                .capacity(30)
                .enrolledCount(0)
                .startDate(LocalDate.of(2025, 3, 1))
                .endDate(LocalDate.of(2025, 6, 30))
                .status(ClassStatus.DRAFT)
                .build();
        setId(entity, 10L);
        return entity;
    }

    private ClassEntity openEntity() throws Exception {
        ClassEntity entity = draftEntity();
        entity.publish();
        return entity;
    }

    @Nested
    class Create {

        @Test
        void 정상_생성() {
            ClassCreateRequest request = new ClassCreateRequest(
                    "Java 기초", "설명", 10000, 30,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30));

            given(userRepository.findById(1L)).willReturn(Optional.of(creator));
            given(classRepository.save(any(ClassEntity.class))).willAnswer(inv -> {
                ClassEntity saved = inv.getArgument(0);
                try { setId(saved, 10L); } catch (Exception ignored) {}
                return saved;
            });

            ArgumentCaptor<ClassEntity> captor = ArgumentCaptor.forClass(ClassEntity.class);

            ClassResponse response = classService.create(1L, request);

            verify(classRepository).save(captor.capture());
            ClassEntity saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ClassStatus.DRAFT);
            assertThat(saved.getEnrolledCount()).isEqualTo(0);
            assertThat(saved.getCreatedBy()).isEqualTo(creator);

            assertThat(response.classId()).isEqualTo(10L);
            assertThat(response.title()).isEqualTo("Java 기초");
            assertThat(response.price()).isEqualTo(10000);
            assertThat(response.capacity()).isEqualTo(30);
            assertThat(response.enrolledCount()).isEqualTo(0);
            assertThat(response.startDate()).isEqualTo(LocalDate.of(2025, 3, 1));
            assertThat(response.endDate()).isEqualTo(LocalDate.of(2025, 6, 30));
            assertThat(response.status()).isEqualTo("DRAFT");
            assertThat(response.creatorName()).isEqualTo("instructor");
        }

        @Test
        void CLASSMATE가_강의_등록_시_FORBIDDEN_ROLE_예외() throws Exception {
            User classmate = User.builder().name("학생").role(UserRole.CLASSMATE).build();
            setId(classmate, 2L);
            ClassCreateRequest request = new ClassCreateRequest(
                    "Java 기초", "설명", 10000, 30,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30));

            given(userRepository.findById(2L)).willReturn(Optional.of(classmate));

            assertThatThrownBy(() -> classService.create(2L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.FORBIDDEN_ROLE));
        }

        @Test
        void 존재하지_않는_사용자면_USER_NOT_FOUND() {
            ClassCreateRequest request = new ClassCreateRequest(
                    "Java 기초", "설명", 10000, 30,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30));

            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> classService.create(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }

        @Test
        void endDate가_startDate보다_이전이면_INVALID_INPUT() {
            ClassCreateRequest request = new ClassCreateRequest(
                    "Java 기초", "설명", 10000, 30,
                    LocalDate.of(2025, 6, 30), LocalDate.of(2025, 3, 1));

            assertThatThrownBy(() -> classService.create(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }

        @Test
        void endDate가_startDate와_같으면_INVALID_INPUT() {
            ClassCreateRequest request = new ClassCreateRequest(
                    "Java 기초", "설명", 10000, 30,
                    LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1));

            assertThatThrownBy(() -> classService.create(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    @Nested
    class Update {

        @Test
        void DRAFT_상태에서_수정_성공() throws Exception {
            ClassEntity entity = draftEntity();
            ClassUpdateRequest request = new ClassUpdateRequest(
                    "새 제목", null, null, null, null, null);

            given(classRepository.findWithCreatorById(10L)).willReturn(Optional.of(entity));

            ClassResponse response = classService.update(1L, 10L, request);
            assertThat(response.title()).isEqualTo("새 제목");
        }

        @Test
        void 존재하지_않는_강의면_CLASS_NOT_FOUND() {
            given(classRepository.findWithCreatorById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> classService.update(1L, 999L,
                    new ClassUpdateRequest("t", null, null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CLASS_NOT_FOUND));
        }

        @Test
        void 소유자_불일치_시_NOT_COURSE_OWNER() throws Exception {
            ClassEntity entity = draftEntity();
            given(classRepository.findWithCreatorById(10L)).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> classService.update(999L, 10L,
                    new ClassUpdateRequest("t", null, null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_COURSE_OWNER));
        }

        @Test
        void OPEN_상태에서_update_시_INVALID_STATE_TRANSITION() throws Exception {
            ClassEntity entity = openEntity();
            given(classRepository.findWithCreatorById(10L)).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> classService.update(1L, 10L,
                    new ClassUpdateRequest("새 제목", null, null, null, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class Publish {

        @Test
        void 정상_publish() throws Exception {
            ClassEntity entity = draftEntity();
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(entity));

            ClassResponse response = classService.publish(1L, 10L);
            assertThat(response.status()).isEqualTo("OPEN");
        }

        @Test
        void 소유자_불일치_시_실패() throws Exception {
            ClassEntity entity = draftEntity();
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> classService.publish(999L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_COURSE_OWNER));
        }

        @Test
        void OPEN_상태에서_publish_시_INVALID_STATE_TRANSITION() throws Exception {
            ClassEntity entity = openEntity();
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> classService.publish(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class CloseTest {

        @Test
        void 정상_close() throws Exception {
            ClassEntity entity = openEntity();
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(entity));

            ClassResponse response = classService.close(1L, 10L);
            assertThat(response.status()).isEqualTo("CLOSED");
        }

        @Test
        void 존재하지_않는_강의면_CLASS_NOT_FOUND() {
            given(classRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> classService.close(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CLASS_NOT_FOUND));
        }

        @Test
        void 소유자_불일치_시_NOT_COURSE_OWNER() throws Exception {
            ClassEntity entity = openEntity();
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> classService.close(999L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.NOT_COURSE_OWNER));
        }

        @Test
        void DRAFT_상태에서_close_시_INVALID_STATE_TRANSITION() throws Exception {
            ClassEntity entity = draftEntity();
            given(classRepository.findByIdForUpdate(10L)).willReturn(Optional.of(entity));

            assertThatThrownBy(() -> classService.close(1L, 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
        }
    }

    @Nested
    class GetClasses {

        @Test
        void status별_목록_조회() throws Exception {
            ClassEntity entity = openEntity();
            Page<ClassEntity> page = new PageImpl<>(List.of(entity));
            given(classRepository.findByStatus(ClassStatus.OPEN, PageRequest.of(0, 20)))
                    .willReturn(page);

            Page<ClassResponse> result = classService.getClasses(ClassStatus.OPEN, PageRequest.of(0, 20));
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).status()).isEqualTo("OPEN");
        }
    }

    @Nested
    class GetClass {

        @Test
        void 단건_조회_성공() throws Exception {
            ClassEntity entity = draftEntity();
            given(classRepository.findWithCreatorById(10L)).willReturn(Optional.of(entity));

            ClassResponse response = classService.getClass(10L);
            assertThat(response.classId()).isEqualTo(10L);
        }

        @Test
        void 존재하지_않는_강의면_CLASS_NOT_FOUND() {
            given(classRepository.findWithCreatorById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> classService.getClass(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.CLASS_NOT_FOUND));
        }
    }

    @Nested
    class GetMyClasses {

        @Test
        void 내_강의_목록_조회() throws Exception {
            ClassEntity entity = draftEntity();
            Page<ClassEntity> page = new PageImpl<>(List.of(entity));
            Pageable pageable = PageRequest.of(0, 20);
            given(userRepository.existsById(1L)).willReturn(true);
            given(classRepository.findByCreatedById(1L, pageable)).willReturn(page);

            Page<ClassResponse> result = classService.getMyClasses(1L, pageable);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        void 사용자_미존재_시_USER_NOT_FOUND() {
            given(userRepository.existsById(999L)).willReturn(false);

            assertThatThrownBy(() -> classService.getMyClasses(999L, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    private void setId(Object obj, Long id) throws Exception {
        Field field = obj.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(obj, id);
    }
}
