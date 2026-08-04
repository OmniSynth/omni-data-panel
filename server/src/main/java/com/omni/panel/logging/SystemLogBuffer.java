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

    private SystemLogBuffer() {
    }

    public static SystemLogBuffer get() {
        return INSTANCE;
    }

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

    private static boolean matches(Entry entry, String keyword) {
        String q = keyword.toLowerCase(Locale.ROOT);
        return contains(entry.loggerName(), q)
                || contains(entry.message(), q)
                || contains(entry.threadName(), q)
                || contains(entry.stackTrace(), q);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

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
