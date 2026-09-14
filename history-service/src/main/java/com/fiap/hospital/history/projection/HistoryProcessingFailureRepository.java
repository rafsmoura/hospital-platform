package com.fiap.hospital.history.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HistoryProcessingFailureRepository extends JpaRepository<HistoryProcessingFailure, UUID> {
}
