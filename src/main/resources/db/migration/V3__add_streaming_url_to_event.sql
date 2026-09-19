-- ============================================================
-- V3: Add optional streaming URL to events (FR-EVT-006)
-- ============================================================

ALTER TABLE events
    ADD COLUMN streaming_url VARCHAR(500);
