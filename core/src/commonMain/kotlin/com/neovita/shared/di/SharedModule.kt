package com.neovita.shared.di

import com.neovita.shared.config.RemoteConfigRepository
import com.neovita.shared.data.cache.LocalCache
import com.neovita.shared.data.repository.AssessmentRepositoryImpl
import com.neovita.shared.data.repository.ChatRepositoryImpl
import com.neovita.shared.data.repository.ContentRepositoryImpl
import com.neovita.shared.data.repository.PlanRepositoryImpl
import com.neovita.shared.data.repository.UserRepositoryImpl
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.domain.repository.ChatRepository
import com.neovita.shared.domain.repository.ContentRepository
import com.neovita.shared.domain.repository.PlanRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.domain.usecase.CalculateScoresUseCase
import com.neovita.shared.network.ApiService
import com.neovita.shared.session.SessionManager
import io.ktor.client.*
import io.ktor.client.plugins.HttpCallValidator
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module

fun sharedModule(baseUrl: String, cache: LocalCache?) = module {
    if (cache != null) single<LocalCache> { cache }
    single {
        HttpClient {
            // ignoreUnknownKeys: installed apps must keep working when the server (which deploys
            // far more often) adds response fields — core of the install-once strategy and of
            // the SDUI schema's forward-compat.
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            // Non-2xx responses throw (so safeCall can classify 401/404 by the status
            // in the exception message, and silent successes like DELETE surface errors).
            expectSuccess = true
            // Auth as a cross-cutting concern (Movi pattern): attach the stored
            // token to every request, and centralize 401 handling here.
            defaultRequest {
                SessionManager.token?.let { headers.append(HttpHeaders.Authorization, "Bearer $it") }
            }
            install(HttpCallValidator) {
                validateResponse { response ->
                    when {
                        response.status == HttpStatusCode.Unauthorized -> SessionManager.onUnauthorized()
                        response.status.value in 200..299 -> SessionManager.onAuthSuccess()
                    }
                }
                // Network errors are swallowed by the caller's safeCall; do not log out here.
            }
        }
    }
    single { ApiService(baseUrl, get()) }
    single { RemoteConfigRepository(get()) }
    single { ServerOrigin(baseUrl.trimEnd('/').removeSuffix("/api")) }
    single<AssessmentRepository> { AssessmentRepositoryImpl(get(), getOrNull()) }
    single<PlanRepository> { PlanRepositoryImpl(get(), getOrNull()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
    single<ContentRepository> { ContentRepositoryImpl(get()) }
    factory { CalculateScoresUseCase() }
}
