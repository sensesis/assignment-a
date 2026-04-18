package com.enrollment.domain.enrollment.repository;

import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.domain.enrollment.entity.EnrollmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    // 수강 신청 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.classEntity JOIN FETCH e.user WHERE e.id = :id")
    Optional<Enrollment> findByIdForUpdate(@Param("id") Long id);

    // 수강 신청 락 없는 조회 (classEntity, user 즉시 로딩)
    @EntityGraph(attributePaths = {"classEntity", "user"})
    Optional<Enrollment> findWithClassAndUserById(Long id);

    // 사용자별 수강 신청 목록 조회
    @EntityGraph(attributePaths = {"classEntity", "user"})
    Page<Enrollment> findByUserId(Long userId, Pageable pageable);

    // 강의별 수강 신청 목록 조회
    @EntityGraph(attributePaths = {"classEntity", "user"})
    Page<Enrollment> findByClassEntityId(Long classId, Pageable pageable);

    // 수강 신청 존재 여부 검증
    @Query("SELECT (COUNT(e) > 0) FROM Enrollment e "
            + "WHERE e.classEntity.id = :classId AND e.user.id = :userId AND e.status IN :statuses")
    boolean existsByClassIdAndUserIdAndStatusIn(
            @Param("classId") Long classId,
            @Param("userId") Long userId,
            @Param("statuses") Collection<EnrollmentStatus> statuses);
}
