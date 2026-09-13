package com.zaze.server.feature.auth.model

import com.zaze.server.feature.auth.pojo.User
import com.zaze.server.feature.auth.vo.UserVo

fun User.asVo(): UserVo = UserVo(
    id = this.id,
    username = this.username,
    displayName = this.displayName,
    role = this.role.name,
    enabled = this.enabled
)
