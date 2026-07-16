package com.bytd.forward.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 转发协议：绑定一个数据源、一个输出目标与当前脚本版本。
 */
@Getter
@Setter
@Entity
@Table(name = "protocol")
public class ProtocolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /** STOPPED / STARTING / RUNNING / ERROR */
    @Column(nullable = false)
    private String status = "STOPPED";

    @Column(name = "status_message")
    private String statusMessage;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "source_id")
    private Long sourceId;

    /** 协议订阅的数据源 topic 过滤，| 分隔；NULL/空 = 接收该数据源全部 topic。 */
    @Column(name = "source_topics")
    private String sourceTopics;

    @Column(name = "output_target_id")
    private Long outputTargetId;

    @Column(name = "current_version_id")
    private Long currentVersionId;

    @Column(name = "ring_buffer_size", nullable = false)
    private int ringBufferSize = 16384;

    @Column(name = "worker_threads", nullable = false)
    private int workerThreads = 4;

    @Column(name = "log_retention_days", nullable = false)
    private int logRetentionDays = 7;

    @Column(name = "sample_rate", nullable = false)
    private double sampleRate = 1.0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
