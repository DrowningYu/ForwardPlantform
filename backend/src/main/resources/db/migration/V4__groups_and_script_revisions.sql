CREATE TABLE protocol_group (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL UNIQUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE protocol
    ADD COLUMN group_id BIGINT REFERENCES protocol_group(id) ON DELETE SET NULL,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0,
    ADD COLUMN next_script_version INT NOT NULL DEFAULT 2;
UPDATE protocol p SET next_script_version = COALESCE(
    (SELECT max(v.version) + 1 FROM script_version v WHERE v.protocol_id = p.id), 1);
CREATE INDEX idx_protocol_group_sort ON protocol(group_id, sort_order, id);

ALTER TABLE script_version
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN updated_at TIMESTAMPTZ;
UPDATE script_version SET updated_at = created_at;
ALTER TABLE script_version ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE script_version ALTER COLUMN updated_at SET DEFAULT now();
