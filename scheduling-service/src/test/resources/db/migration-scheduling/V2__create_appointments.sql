CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    doctor_id UUID,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(1000),
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT appointments_status_check CHECK (status IN ('AGENDADA', 'REALIZADA', 'CANCELADA')),
    CONSTRAINT appointments_patient_fk FOREIGN KEY (patient_id) REFERENCES users (id),
    CONSTRAINT appointments_doctor_fk FOREIGN KEY (doctor_id) REFERENCES users (id)
);

CREATE INDEX ix_appointments_patient_scheduled ON appointments (patient_id, scheduled_at);
