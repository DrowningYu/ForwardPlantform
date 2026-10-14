package com.bytd.forward.web;

import com.bytd.forward.service.ProtocolBindingBlockedException;
import com.bytd.forward.service.ProtocolBindingWarningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleStaleVersion(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "版本已被其他操作修改，请重新载入"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "数据已被修改、名称重复或关联对象不存在，请刷新后重试"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() == null ? "参数错误" : e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage() == null ? "状态错误" : e.getMessage()));
    }

    @ExceptionHandler(ProtocolBindingBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBindingBlocked(ProtocolBindingBlockedException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        body.put("code", "BINDING_BLOCKED");
        body.put("blockers", e.getResult().blockers());
        body.put("warnings", e.getResult().warnings());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ProtocolBindingWarningException.class)
    public ResponseEntity<Map<String, Object>> handleBindingWarning(ProtocolBindingWarningException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getMessage());
        body.put("code", "BINDING_WARNING");
        body.put("blockers", e.getResult().blockers());
        body.put("warnings", e.getResult().warnings());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("请求处理异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
}
