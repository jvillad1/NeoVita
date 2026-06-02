package com.neovita.shared.network.error

sealed class NetworkError : Exception() {
    data object Unauthorized : NetworkError()
    data object NotFound : NetworkError()
    data class ServerError(val code: String, val msg: String) : NetworkError()
    data class Unknown(override val cause: Throwable?) : NetworkError()
}
