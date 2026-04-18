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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ClassEntity c JOIN FETCH c.createdBy WHERE c.id = :id")
    Optional<ClassEntity> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "createdBy")
    Optional<ClassEntity> findWithCreatorById(Long id);

    @EntityGraph(attributePaths = "createdBy")
    Page<ClassEntity> findByStatus(ClassStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "createdBy")
    Page<ClassEntity> findByCreatedById(Long userId, Pageable pageable);
}
