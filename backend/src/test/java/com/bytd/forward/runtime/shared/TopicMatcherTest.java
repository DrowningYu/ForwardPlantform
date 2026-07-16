package com.bytd.forward.runtime.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(TopicMatcher.matches("a/b/c", "a/b/c"));
        assertFalse(TopicMatcher.matches("a/b/c", "a/b"));
        assertFalse(TopicMatcher.matches("a/b", "a/b/c"));
        // Kafka topic 无层级
        assertTrue(TopicMatcher.matches("bridge_health_monitoring", "bridge_health_monitoring"));
        assertFalse(TopicMatcher.matches("bridge_health_monitoring", "bridge_health_monitoring_sd"));
    }

    @Test
    void plusWildcard() {
        assertTrue(TopicMatcher.matches("prod/iot/jz/+/metric/v1", "prod/iot/jz/ivs/metric/v1"));
        assertTrue(TopicMatcher.matches("prod/iot/jz/+/metric/v1", "prod/iot/jz/meteorology/metric/v1"));
        assertFalse(TopicMatcher.matches("prod/iot/jz/+/metric/v1", "prod/iot/jz/ivs/extra/metric/v1"));
        assertTrue(TopicMatcher.matches("+/b", "a/b"));
        assertFalse(TopicMatcher.matches("+", "a/b"));
    }

    @Test
    void hashWildcard() {
        assertTrue(TopicMatcher.matches("test/bdl/#", "test/bdl/dev1"));
        assertTrue(TopicMatcher.matches("test/bdl/#", "test/bdl/dev1/sub"));
        assertTrue(TopicMatcher.matches("test/bdl/#", "test/bdl"));
        assertFalse(TopicMatcher.matches("test/bdl/#", "test/ydh/dev1"));
        assertTrue(TopicMatcher.matches("#", "anything/at/all"));
    }

    @Test
    void emptyFiltersMatchAll() {
        assertTrue(TopicMatcher.matchesAny(null, "a/b"));
        assertTrue(TopicMatcher.matchesAny(List.of(), "a/b"));
    }

    @Test
    void multipleFilters() {
        List<String> filters = List.of("test/bdl/#", "test/other");
        assertTrue(TopicMatcher.matchesAny(filters, "test/bdl/x"));
        assertTrue(TopicMatcher.matchesAny(filters, "test/other"));
        assertFalse(TopicMatcher.matchesAny(filters, "test/ydh/x"));
    }

    @Test
    void splitFilters() {
        assertEquals(List.of("a/b", "c/d"), TopicMatcher.splitFilters("a/b|c/d"));
        assertEquals(List.of("a", "b"), TopicMatcher.splitFilters("a, b"));
        assertTrue(TopicMatcher.splitFilters(null).isEmpty());
        assertTrue(TopicMatcher.splitFilters("  ").isEmpty());
    }
}
