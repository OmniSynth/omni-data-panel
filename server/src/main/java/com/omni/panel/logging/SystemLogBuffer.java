package com.omni.panel.logging;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 进程内系统日志环形缓冲，供管理端查看近期运行/报错日志；重启后清空。
 */
public final class SystemLogBuffer {
    public static final int CAPACITY = 2000;
    private static final SystemLogBuffer INSTANCE = new SystemLogBuffer();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ArrayList<Entry> entries = new ArrayList<>(CAPACITY);

    /**
     * 单例构造，禁止外部实例化。
     */
    private SystemLogBuffer() {
    }

    /**
     * 返回进程内唯一缓冲实例。
     *
     * @return 系统日志缓冲单例
     */
    public static SystemLogBuffer get() {
        return INSTANCE;
    }

    /**
     * 追加一条日志；超出容量时丢弃最旧条目。
     *
     * @param entry 日志条目，null 时忽略
     */
    public void append(Entry entry) {
        if (entry == null) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (entries.size() >= CAPACITY) {
                entries.remove(0);
            }
            entries.add(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清空缓冲内全部日志条目。
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            entries.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 按级别与关键字过滤后倒序分页（最新在前）。
     *
     * @param keyword 搜索关键字，可为空
     * @param level   日志级别过滤，可为空
     * @param page    页码（从 1 起）
     * @param size    每页条数
     * @return 分页结果
     */
    public Page page(String keyword, String level, int page, int size) {
        String normalizedKeyword = blankToNull(keyword);
        String normalizedLevel = blankToNull(level);
        if (normalizedLevel != null) {
            normalizedLevel = normalizedLevel.toUpperCase(Locale.ROOT);
        }
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), CAPACITY);

        lock.readLock().lock();
        try {
            List<Entry> matched = new ArrayList<>();
            for (int i = entries.size() - 1; i >= 0; i--) {
                Entry entry = entries.get(i);
                if (normalizedLevel != null && !normalizedLevel.equals(entry.level())) {
                    continue;
                }
                if (normalizedKeyword != null && !matches(entry, normalizedKeyword)) {
                    continue;
                }
                matched.add(entry);
            }
            long total = matched.size();
            int from = Math.min((safePage - 1) * safeSize, matched.size());
            int to = Math.min(from + safeSize, matched.size());
            return new Page(List.copyOf(matched.subList(from, to)), total, safePage, safeSize, CAPACITY, entries.size());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 判断日志条目是否匹配关键字（忽略大小写）。
     *
     * @param entry   日志条目
     * @param keyword 搜索关键字
     * @return 任一字段包含关键字时返回 true
     */
    private static boolean matches(Entry entry, String keyword) {
        String q = keyword.toLowerCase(Locale.ROOT);
        return contains(entry.loggerName(), q)
                || contains(entry.message(), q)
                || contains(entry.threadName(), q)
                || contains(entry.stackTrace(), q)
                || contains(entry.requestId(), q);
    }

    /**
     * 判断字符串是否包含关键字（忽略大小写）。
     *
     * @param value   待搜索文本
     * @param keyword 关键字
     * @return 包含时返回 true；value 为 null 时返回 false
     */
    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /**
     * 将空白字符串规范为 null。
     *
     * @param value 原始字符串
     * @return 非空白 trim 后的值，否则 null
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 系统日志条目。
     */
    public record Entry(
            String level,
            String loggerName,
            String message,
            String stackTrace,
            String threadName,
            String requestId,
            LocalDateTime createdAt
    ) {
    }

    /**
     * 内存分页结果，附带缓冲容量信息。
     */
    public record Page(
            List<Entry> items,
            long total,
            int page,
            int size,
            int capacity,
            int buffered
    ) {
    }
}
