package com.enrollment.domain.classes.entity;

import com.enrollment.domain.user.entity.User;
import com.enrollment.global.entity.BaseEntity;
import com.enrollment.global.error.exception.BusinessException;
import com.enrollment.global.error.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "classes")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClassEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "class_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    @Builder.Default
    private Integer enrolledCount = 0;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClassStatus status;

    @Version
    private Long version;

    public void publish() {
        validateTransition(ClassStatus.OPEN);
        validateFieldsForPublish();
        this.status = ClassStatus.OPEN;
    }

    public void close() {
        validateTransition(ClassStatus.CLOSED);
        this.status = ClassStatus.CLOSED;
    }

    public void update(String title, String description, Integer price,
                       Integer capacity, LocalDate startDate, LocalDate endDate) {
        if (this.status != ClassStatus.DRAFT) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (price != null) {
            if (price < 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
            this.price = price;
        }
        if (capacity != null) {
            if (capacity <= 0) throw new BusinessException(ErrorCode.INVALID_INPUT);
            this.capacity = capacity;
        }
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (this.startDate != null && this.endDate != null) {
            if (!this.endDate.isAfter(this.startDate)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
        }
    }

    public void validateOwner(Long userId) {
        if (this.createdBy == null || !this.createdBy.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_COURSE_OWNER);
        }
    }

    private void validateTransition(ClassStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private void validateFieldsForPublish() {
        if (price == null || price < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (capacity == null || capacity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (!endDate.isAfter(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
