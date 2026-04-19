package com.enrollment.domain.payment.entity;

import com.enrollment.domain.enrollment.entity.Enrollment;
import com.enrollment.global.entity.BaseEntity;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private Enrollment enrollment;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    // 결제 완료
    public static Payment paid(Enrollment enrollment, Integer amount) {
        return Payment.builder()
                .enrollment(enrollment)
                .amount(amount)
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now())
                .build();
    }

    // 결제 취소
    public static Payment refunded(Enrollment enrollment, Integer amount) {
        return Payment.builder()
                .enrollment(enrollment)
                .amount(amount)
                .status(PaymentStatus.REFUNDED)
                .paidAt(LocalDateTime.now())
                .build();
    }
}
