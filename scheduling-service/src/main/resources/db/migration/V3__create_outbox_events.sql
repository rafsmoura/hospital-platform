CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    payload TEXT NOT NULL,
    routing_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT outbox_event_type_check CHECK (event_type IN ('CONSULTA_CRIADA', 'CONSULTA_EDITADA')),
    CONSTRAINT outbox_status_check CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE UNIQUE INDEX ux_outbox_message_id ON outbox_events (message_id);
CREATE INDEX ix_outbox_pending ON outbox_events (status, next_attempt_at);
