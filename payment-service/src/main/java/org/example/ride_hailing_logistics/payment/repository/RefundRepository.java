package org.example.ride_hailing_logistics.payment.repository;

import org.example.ride_hailing_logistics.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentIdOrderByCreatedAt(UUID paymentId);
}
