package com.enrollment.domain.payment.repository;

import com.enrollment.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
