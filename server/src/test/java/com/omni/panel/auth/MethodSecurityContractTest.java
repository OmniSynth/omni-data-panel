package com.omni.panel.auth;

import com.omni.panel.datasource.DataSourceController;
import com.omni.panel.export.ExportController;
import com.omni.panel.query.QueryController;
import com.omni.panel.role.RoleController;
import com.omni.panel.schedule.ScheduleController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.assertj.core.api.Assertions.assertThat;

class MethodSecurityContractTest {
    @Test
    void 方法鉴权已启用且关键端点声明系统权限() throws Exception {
        assertThat(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class)).isTrue();
        assertThat(QueryController.class.getAnnotation(PreAuthorize.class).value()).contains("query:execute");
        assertThat(ScheduleController.class.getAnnotation(PreAuthorize.class).value()).contains("schedule:manage");
        assertThat(DataSourceController.class.getMethod("create", DataSourceController.CreateRequest.class)
            .getAnnotation(PreAuthorize.class).value()).contains("ADMIN");
        assertThat(RoleController.class.getAnnotation(PreAuthorize.class).value()).contains("ADMIN");
        assertThat(ExportController.class.getMethod("submit", ExportController.ExportRequest.class)
            .getAnnotation(PreAuthorize.class).value()).contains("export:execute");
    }
}
