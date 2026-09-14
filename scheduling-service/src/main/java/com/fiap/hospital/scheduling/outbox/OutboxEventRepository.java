package com.fiap.hospital.scheduling.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("select event from OutboxEvent event "
            + "where event.status in (com.fiap.hospital.scheduling.outbox.OutboxStatus.PENDING, "
            + "com.fiap.hospital.scheduling.outbox.OutboxStatus.FAILED) "
            + "and event.nextAttemptAt <= :now order by event.createdAt")
    List<OutboxEvent> findReady(@Param("now") Instant now);
}
