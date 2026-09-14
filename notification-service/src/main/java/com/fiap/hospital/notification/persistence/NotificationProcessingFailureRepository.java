package com.fiap.hospital.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationProcessingFailureRepository extends JpaRepository<NotificationProcessingFailure, UUID> {
}
