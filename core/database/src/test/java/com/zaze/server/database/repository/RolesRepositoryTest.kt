package com.zaze.server.database.repository

import com.zaze.server.database.model.Roles
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringBootTest
class RolesRepositoryTest {

    @Autowired
    private lateinit var repository: RolesRepository

    fun setUp() {
    }

    @Test
    fun save() {
        val rolesList = listOf(
            Roles.builder().name("希尔").build(),
            Roles.builder().name("克拉拉").build(),
        )
        rolesList.forEach {
            val result = repository.save(it)
            println("saved : $result")
//            assert(repository.existsById(result.id))
        }
//        assert(repository.count() == rolesList.size.toLong())
    }

    @Test
    fun findByTagsLike() {
    }
}