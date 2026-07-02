-- 删除 script_version 时自动将 protocol.current_version_id 置空，避免外键冲突
ALTER TABLE protocol DROP CONSTRAINT IF EXISTS fk_protocol_current_version;

ALTER TABLE protocol
    ADD CONSTRAINT fk_protocol_current_version
    FOREIGN KEY (current_version_id) REFERENCES script_version(id) ON DELETE SET NULL;
