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

    /**
     * 将符合条件的 Logback 事件写入内存缓冲。
     *
     * @param event Logback 日志事件
     */
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

    /**
     * 提取异常堆栈文本。
     *
     * @param proxy Logback 异常代理
     * @return 堆栈字符串；无异常时返回 null
     */
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

    /**
     * 截断超长字符串以限制缓冲占用。
     *
     * @param value 原始字符串
     * @param limit 最大长度
     * @return 截断后的字符串
     */
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
