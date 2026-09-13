package com.zaze.server.feature.auth.repository

import com.zaze.server.database.repository.BaseRepository
import com.zaze.server.feature.auth.pojo.User

interface UserRepository : BaseRepository<User, Long> {
    fun findByUsername(username: String): User?
}
