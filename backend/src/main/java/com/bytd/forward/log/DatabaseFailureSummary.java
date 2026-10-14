package com.bytd.forward.log;

import java.sql.SQLException;

/** 不调用 getMessage()，避免批量 SQL、报文、连接凭据进入故障日志。 */
public final class DatabaseFailureSummary {
    private DatabaseFailureSummary() {}
    public static String of(Exception error) {
        Throwable cause = error;
        for (int i = 0; cause != null && i < 12; i++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                return error.getClass().getSimpleName() + " SQLState="
                        + (state != null && state.matches("[A-Z0-9]{5}") ? state : "unknown");
            }
        }
        return error.getClass().getSimpleName();
    }
}
