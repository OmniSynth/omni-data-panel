package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.omni.panel.entity.TotpBackupCodeEntity;

/**
 * TOTP 备用码表访问。
 */
@Mapper
public interface TotpBackupCodeMapper extends BaseMapper<TotpBackupCodeEntity> {
}
