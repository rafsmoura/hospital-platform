CREATE TABLE notification_logs (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    appointment_id UUID NOT NULL,
    patient_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(30) NOT NULL,
    failure_reason VARCHAR(1000),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT notification_event_type_check CHECK (event_type IN ('CONSULTA_CRIADA', 'CONSULTA_EDITADA')),
    CONSTRAINT notification_status_check CHECK (status IN ('SENT', 'SKIPPED_CANCELLED', 'FAILED'))
);

CREATE INDEX ix_notification_message_id ON notification_logs (message_id);
