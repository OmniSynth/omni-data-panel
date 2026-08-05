package com.omni.panel.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.omni.panel.common.ApiResponse;
import com.omni.panel.mapper.DataSourceObjectAclMapper;
import com.omni.panel.service.DataSourceObjectAclService;

/**
 * 管理员维护数据源表/列角色拒绝规则。
 */
@RestController
@RequestMapping("/api/data-sources/{sourceId}/object-acl")
@PreAuthorize("hasRole('ADMIN')")
public class DataSourceObjectAclController {
    private final DataSourceObjectAclService service;

    public DataSourceObjectAclController(DataSourceObjectAclService service) {
        this.service = service;
    }

    /**
     * 查询角色在数据源上的表/列拒绝配置。
     *
     * @param sourceId 数据源标识
     * @param roleId   角色标识
     * @return ACL 视图
     */
    @GetMapping
    public ApiResponse<DataSourceObjectAclService.AclView> get(@PathVariable long sourceId,
                                                               @RequestParam long roleId) {
        return ApiResponse.ok(service.getForRole(sourceId, roleId));
    }

    /**
     * 整体替换角色在数据源上的表/列拒绝配置。
     *
     * @param sourceId 数据源标识
     * @param request  角色与拒绝列表
     * @return 空成功响应
     */
    @PutMapping
    public ApiResponse<Void> replace(@PathVariable long sourceId, @Valid @RequestBody ReplaceRequest request) {
        service.replace(sourceId, request.roleId(), request.deniedTables(), request.deniedColumns());
        return ApiResponse.ok();
    }

    /**
     * 替换请求。
     *
     * @param roleId        角色标识
     * @param deniedTables  拒绝的表；空表示清除表拒绝
     * @param deniedColumns 拒绝的列；空表示清除列拒绝
     */
    public record ReplaceRequest(@NotNull Long roleId,
                                 List<DataSourceObjectAclMapper.TableRef> deniedTables,
                                 List<DataSourceObjectAclMapper.ColumnRef> deniedColumns) {
    }
}
