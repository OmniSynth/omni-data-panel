-- 业务表表级 COMMENT（不改写历史迁移，避免 Flyway checksum 冲突；不含 QRTZ_*）

ALTER TABLE sys_user COMMENT = '系统用户';
ALTER TABLE sys_role COMMENT = '角色';
ALTER TABLE sys_permission COMMENT = '功能权限目录';
ALTER TABLE sys_user_role COMMENT = '用户与角色关联';
ALTER TABLE sys_role_permission COMMENT = '角色与功能权限关联';
ALTER TABLE sys_login_audit COMMENT = '登录审计';
ALTER TABLE sys_user_token COMMENT = '用户激活/凭据令牌';
ALTER TABLE sys_user_totp_backup_code COMMENT = '用户 TOTP 备用码';

ALTER TABLE bi_data_source COMMENT = '外部数据源连接';
ALTER TABLE bi_meta_schema COMMENT = '数据源元数据：库/命名空间缓存';
ALTER TABLE bi_meta_table COMMENT = '数据源元数据：表缓存';
ALTER TABLE bi_meta_column COMMENT = '数据源元数据：列缓存';

ALTER TABLE bi_dataset COMMENT = '语义模型（数据集）';
ALTER TABLE bi_dataset_field COMMENT = '语义模型字段';
ALTER TABLE bi_dataset_audit COMMENT = '模型变更审计';
ALTER TABLE bi_metric COMMENT = '业务指标';

ALTER TABLE bi_chart COMMENT = '图表/问题';
ALTER TABLE bi_dashboard COMMENT = '仪表盘';
ALTER TABLE bi_dashboard_card COMMENT = '仪表盘卡片布局与绑定';

ALTER TABLE bi_collection COMMENT = '内容集合（文件夹）';
ALTER TABLE bi_recent_item COMMENT = '用户最近打开资源';
ALTER TABLE bi_public_link COMMENT = '公开分享链接';
ALTER TABLE bi_setting COMMENT = '站点级系统设置';

ALTER TABLE bi_role_resource_permission COMMENT = '角色资源 ACL（集合/仪表盘/图表等）';
ALTER TABLE bi_field_permission COMMENT = '字段级权限策略';
ALTER TABLE bi_row_rule COMMENT = '行级过滤规则';
ALTER TABLE bi_role_table_deny COMMENT = '数据源对象 ACL：按角色拒绝表';
ALTER TABLE bi_role_column_deny COMMENT = '数据源对象 ACL：按角色拒绝列';

ALTER TABLE bi_query_audit COMMENT = '查询审计';
ALTER TABLE bi_export_audit COMMENT = '导出审计';
ALTER TABLE bi_export_task COMMENT = '异步导出任务';
ALTER TABLE bi_schedule COMMENT = '通用 Quartz 调度任务配置';
ALTER TABLE bi_subscription COMMENT = '仪表盘邮件订阅';
