package com.neovita.server.db.repositories

import com.neovita.server.db.tables.UsersTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class UserEntity(
    val id: String, val email: String, val name: String,
    val age: Int, val role: String, val companyId: String?
)

class UserRepository {
    fun findByEmail(email: String): UserEntity? = transaction {
        UsersTable.selectAll().where { UsersTable.email eq email }
            .singleOrNull()?.toEntity()
    }

    fun upsert(email: String, name: String): UserEntity = transaction {
        val existing = findByEmail(email)
        if (existing != null) return@transaction existing
        val id = UUID.randomUUID().toString()
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[UsersTable.name] = name
        }
        findByEmail(email)!!
    }

    fun findById(id: String): UserEntity? = transaction {
        UsersTable.selectAll().where { UsersTable.id eq id }.singleOrNull()?.toEntity()
    }

    fun findByCompany(companyId: String): List<UserEntity> = transaction {
        UsersTable.selectAll().where { UsersTable.companyId eq companyId }.map { it.toEntity() }
    }

    fun update(id: String, name: String? = null, age: Int? = null): UserEntity? = transaction {
        UsersTable.update({ UsersTable.id eq id }) {
            name?.let { n -> it[UsersTable.name] = n }
            age?.let { a -> it[UsersTable.age] = a }
        }
        findById(id)
    }

    private fun ResultRow.toEntity() = UserEntity(
        id = this[UsersTable.id], email = this[UsersTable.email],
        name = this[UsersTable.name], age = this[UsersTable.age],
        role = this[UsersTable.role], companyId = this[UsersTable.companyId]
    )
}
