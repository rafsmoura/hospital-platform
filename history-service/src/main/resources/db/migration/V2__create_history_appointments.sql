CREATE TABLE history_appointments (
    appointment_id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    doctor_id UUID,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_event_message_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT history_status_check CHECK (status IN ('AGENDADA', 'REALIZADA', 'CANCELADA'))
);

CREATE UNIQUE INDEX ux_history_last_event_message_id ON history_appointments (last_event_message_id);
CREATE INDEX ix_history_patient_scheduled_at ON history_appointments (patient_id, scheduled_at);
