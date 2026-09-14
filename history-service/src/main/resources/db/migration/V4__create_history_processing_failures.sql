CREATE TABLE history_processing_failures (
    id UUID PRIMARY KEY,
    message_id UUID,
    failure_reason VARCHAR(1000) NOT NULL,
    payload TEXT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
