package com.fiap.hospital.history.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HistoryProcessedMessageRepository extends JpaRepository<HistoryProcessedMessage, UUID> {
}
