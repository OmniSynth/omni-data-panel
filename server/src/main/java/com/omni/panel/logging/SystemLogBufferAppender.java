package com.omni.panel.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;

/**
 * 将近期日志写入 {@link SystemLogBuffer}，供管理端查看。
 */
public class SystemLogBufferAppender extends AppenderBase<ILoggingEvent> {
    private static final int MESSAGE_LIMIT = 2000;
    private static final int STACK_LIMIT = 8000;
    private static final String APP_LOGGER_PREFIX = "com.omni.panel";

    @Override
    protected void append(ILoggingEvent event) {
        if (event == null) {
            return;
        }
        Level level = event.getLevel();
        if (level == null || !level.isGreaterOrEqual(Level.INFO)) {
            return;
        }
        if (Level.INFO.equals(level)) {
            String loggerName = event.getLoggerName();
            if (loggerName == null || !loggerName.startsWith(APP_LOGGER_PREFIX)) {
                return;
            }
        }
        try {
            SystemLogBuffer.get().append(new SystemLogBuffer.Entry(
                    level.toString(),
                    event.getLoggerName(),
                    truncate(event.getFormattedMessage(), MESSAGE_LIMIT),
                    truncate(stackTrace(event.getThrowableProxy()), STACK_LIMIT),
                    event.getThreadName(),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(event.getTimeStamp()), ZoneId.systemDefault())
            ));
        } catch (RuntimeException ignored) {
            // 日志采集失败不得影响业务
        }
    }

    private static String stackTrace(IThrowableProxy proxy) {
        if (!(proxy instanceof ThrowableProxy throwableProxy)) {
            return null;
        }
        Throwable throwable = throwableProxy.getThrowable();
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return null;
        }
        if (value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit);
    }
}
