package com.enrollment.domain.classes.repository;

import com.enrollment.domain.classes.entity.ClassEntity;
import com.enrollment.domain.classes.entity.ClassStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClassRepository extends JpaRepository<ClassEntity, Long> {

    // 강의 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClassEntity c JOIN FETCH c.createdBy WHERE c.id = :id")
    Optional<ClassEntity> findByIdForUpdate(@Param("id") Long id);

    // 강의 소유자 조회
    @EntityGraph(attributePaths = "createdBy")
    Optional<ClassEntity> findWithCreatorById(Long id);

    // 강의 상태별 목록 조회
    @EntityGraph(attributePaths = "createdBy")
    Page<ClassEntity> findByStatus(ClassStatus status, Pageable pageable);

    // 강의 소유자별 목록 조회
    @EntityGraph(attributePaths = "createdBy")
    Page<ClassEntity> findByCreatedById(Long userId, Pageable pageable);
}
