package com.neovita.server.db.repositories

import com.neovita.server.db.tables.DeviceTokensTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

class DeviceTokenRepository {

    /**
     * Registra el token, o lo reasigna si ya existía (un dispositivo puede cambiar de dueño
     * al cerrar y abrir sesión): manda el último registro.
     *
     * Una sola sentencia atómica (`ON CONFLICT DO UPDATE`) en vez de UPDATE-y-si-no-INSERT.
     * Con dos sentencias, dos registros simultáneos del mismo token pasan ambos por el UPDATE
     * en vacío y ambos insertan: el segundo choca contra la clave primaria. El resultado
     * acababa siendo correcto porque Exposed reintenta la transacción y el UPDATE del
     * reintento sí encuentra la fila — pero al precio de una excepción y un viaje de más.
     */
    fun upsert(token: String, userId: String, platform: String) = transaction {
        val now = System.currentTimeMillis()
        DeviceTokensTable.upsert(DeviceTokensTable.token) {
            it[DeviceTokensTable.token] = token
            it[DeviceTokensTable.userId] = userId
            it[DeviceTokensTable.platform] = platform
            it[updatedAt] = now
        }
    }

    fun tokensForUser(userId: String): List<String> = transaction {
        DeviceTokensTable.selectAll().where { DeviceTokensTable.userId eq userId }
            .map { it[DeviceTokensTable.token] }
    }

    fun allTokens(): List<String> = transaction {
        DeviceTokensTable.selectAll().map { it[DeviceTokensTable.token] }
    }

    /** Borra los tokens que FCM ya no reconoce. Devuelve cuántos existían. */
    fun delete(tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        return transaction {
            DeviceTokensTable.deleteWhere { DeviceTokensTable.token inList tokens }
        }
    }
}
