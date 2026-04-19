package com.enrollment.domain.waitlist.repository;

import com.enrollment.domain.waitlist.entity.Waitlist;
import com.enrollment.domain.waitlist.entity.WaitlistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    // 대기열 선두 비관적 락 조회 (FIFO: createdAt ASC, id ASC 타이브레이커). Pageable.ofSize(1) 로 LIMIT 1
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Waitlist w JOIN FETCH w.user "
            + "WHERE w.classEntity.id = :classId AND w.status = com.enrollment.domain.waitlist.entity.WaitlistStatus.WAITING "
            + "ORDER BY w.createdAt ASC, w.id ASC")
    List<Waitlist> findWaitingForUpdate(@Param("classId") Long classId, Pageable pageable);

    // 대기열 선두 단건 조회 편의 메서드
    default Optional<Waitlist> findFirstWaitingForUpdate(Long classId) {
        return findWaitingForUpdate(classId, Pageable.ofSize(1)).stream().findFirst();
    }

    // 대기열 존재 여부 검증
    @Query("SELECT (COUNT(w) > 0) FROM Waitlist w "
            + "WHERE w.classEntity.id = :classId AND w.user.id = :userId AND w.status = :status")
    boolean existsByClassIdAndUserIdAndStatus(@Param("classId") Long classId,
                                              @Param("userId") Long userId,
                                              @Param("status") WaitlistStatus status);

    // 대기열 단건 조회
    @Query("SELECT w FROM Waitlist w "
            + "WHERE w.classEntity.id = :classId AND w.user.id = :userId AND w.status = :status")
    Optional<Waitlist> findByClassIdAndUserIdAndStatus(@Param("classId") Long classId,
                                                      @Param("userId") Long userId,
                                                      @Param("status") WaitlistStatus status);
}
