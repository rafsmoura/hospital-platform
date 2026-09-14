ALTER TABLE notification_logs
    ADD CONSTRAINT uk_notification_logs_message_id UNIQUE (message_id);

CREATE TABLE notification_processed_messages (
    message_id UUID PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE notification_processing_failures (
    id UUID PRIMARY KEY,
    message_id UUID,
    failure_reason VARCHAR(1000) NOT NULL,
    payload VARCHAR(4000),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_notification_failure_message_id
    ON notification_processing_failures (message_id);
