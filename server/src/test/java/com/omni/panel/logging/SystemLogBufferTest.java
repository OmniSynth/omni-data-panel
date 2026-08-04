package com.omni.panel.logging;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemLogBufferTest {
    @BeforeEach
    void 清空() {
        SystemLogBuffer.get().clear();
    }

    @Test
    void 按级别过滤并倒序分页() {
        SystemLogBuffer buffer = SystemLogBuffer.get();
        buffer.append(entry("INFO", "hello-1"));
        buffer.append(entry("ERROR", "boom"));
        buffer.append(entry("INFO", "hello-2"));

        SystemLogBuffer.Page page = buffer.page(null, "INFO", 1, 10);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(SystemLogBuffer.Entry::message)
                .containsExactly("hello-2", "hello-1");
    }

    @Test
    void 超出容量时丢弃最旧条目() {
        SystemLogBuffer buffer = SystemLogBuffer.get();
        for (int i = 0; i < SystemLogBuffer.CAPACITY + 3; i++) {
            buffer.append(entry("WARN", "msg-" + i));
        }
        SystemLogBuffer.Page page = buffer.page(null, null, 1, 1);
        assertThat(page.buffered()).isEqualTo(SystemLogBuffer.CAPACITY);
        assertThat(page.items().get(0).message()).isEqualTo("msg-" + (SystemLogBuffer.CAPACITY + 2));
    }

    private static SystemLogBuffer.Entry entry(String level, String message) {
        return new SystemLogBuffer.Entry(level, "com.omni.panel.test", message, null, "main", LocalDateTime.now());
    }
}
