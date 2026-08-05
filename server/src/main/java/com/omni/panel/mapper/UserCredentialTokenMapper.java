package com.omni.panel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.omni.panel.entity.UserCredentialTokenEntity;

/**
 * 用户凭据令牌表访问。
 */
@Mapper
public interface UserCredentialTokenMapper extends BaseMapper<UserCredentialTokenEntity> {
}
