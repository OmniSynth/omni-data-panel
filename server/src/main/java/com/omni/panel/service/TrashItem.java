package com.omni.panel.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 废纸篓条目。
 */
public record TrashItem(String resourceType,
                        @JsonSerialize(using = ToStringSerializer.class) long resourceId,
                        String name, String description, LocalDateTime deletedAt,
                        @JsonSerialize(using = ToStringSerializer.class) long ownerId) {
}
