package com.omni.panel.iam;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import com.omni.panel.config.SecurityConfig;
import com.omni.panel.controller.DataSourceController;
import com.omni.panel.controller.ExportController;
import com.omni.panel.controller.QueryController;
import com.omni.panel.controller.RoleController;
import com.omni.panel.controller.ScheduleController;

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
