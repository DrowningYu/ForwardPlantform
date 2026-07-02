package com.bytd.forward.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 脚本版本，支持保存历史与回滚。
 */
@Getter
@Setter
@Entity
@Table(name = "script_version")
public class ScriptVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "protocol_id", nullable = false)
    private Long protocolId;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String language = "groovy";

    @Column(nullable = false, columnDefinition = "text")
    private String code;

    /** OK / ERROR / UNKNOWN */
    @Column(name = "compile_status", nullable = false)
    private String compileStatus = "UNKNOWN";

    @Column(name = "compile_error", columnDefinition = "text")
    private String compileError;

    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
