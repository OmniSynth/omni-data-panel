package com.omni.panel.service;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 废纸篓条目。
 *
 * @param resourceType 资源类型
 * @param resourceId   资源标识
 * @param name         资源名称
 * @param description  资源描述
 * @param deletedAt    软删除时间
 * @param ownerId      资源所有者标识
 */
public record TrashItem(String resourceType,
                        @JsonSerialize(using = ToStringSerializer.class) long resourceId,
                        String name, String description, LocalDateTime deletedAt,
                        @JsonSerialize(using = ToStringSerializer.class) long ownerId) {
}
