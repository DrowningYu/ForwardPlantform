package com.bytd.forward.runtime.shared;

import java.util.List;

/**
 * 协议级 topic 过滤匹配。
 *
 * <p>过滤器可以是精确 topic，也可以是 MQTT 通配过滤器（{@code +} 单层、{@code #} 多层）。
 * Kafka/HTTP 的 topic 不含 '/' 层级时通配符退化为精确匹配，不影响行为。</p>
 */
public final class TopicMatcher {

    private TopicMatcher() {
    }

    /**
     * @param filters 协议配置的过滤器列表；null 或空 = 匹配全部
     * @param topic   消息实际 topic
     */
    public static boolean matchesAny(List<String> filters, String topic) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (topic == null) {
            return false;
        }
        for (String filter : filters) {
            if (matches(filter, topic)) {
                return true;
            }
        }
        return false;
    }

    /** 单个过滤器匹配，支持 MQTT + / # 通配。 */
    public static boolean matches(String filter, String topic) {
        if (filter == null || filter.isBlank()) {
            return false;
        }
        filter = filter.trim();
        if (filter.equals(topic)) {
            return true;
        }
        String[] filterParts = filter.split("/", -1);
        String[] topicParts = topic.split("/", -1);

        int i = 0;
        for (; i < filterParts.length; i++) {
            String fp = filterParts[i];
            if ("#".equals(fp)) {
                // # 必须是最后一段，匹配剩余所有层级（含零层）
                return i == filterParts.length - 1;
            }
            if (i >= topicParts.length) {
                return false;
            }
            if (!"+".equals(fp) && !fp.equals(topicParts[i])) {
                return false;
            }
        }
        return i == topicParts.length;
    }

    /** 拆分 | 或 , 分隔的过滤器串；null/空返回空列表（= 全部）。 */
    public static List<String> splitFilters(String filters) {
        if (filters == null || filters.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(filters.split("[|,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
