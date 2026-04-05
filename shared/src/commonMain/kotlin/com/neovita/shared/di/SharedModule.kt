package com.neovita.shared.di

import com.neovita.shared.data.repository.AssessmentRepositoryImpl
import com.neovita.shared.data.repository.ChatRepositoryImpl
import com.neovita.shared.data.repository.PlanRepositoryImpl
import com.neovita.shared.data.repository.UserRepositoryImpl
import com.neovita.shared.domain.repository.AssessmentRepository
import com.neovita.shared.domain.repository.ChatRepository
import com.neovita.shared.domain.repository.PlanRepository
import com.neovita.shared.domain.repository.UserRepository
import com.neovita.shared.domain.usecase.CalculateScoresUseCase
import com.neovita.shared.db.NeoVitaDatabase
import com.neovita.shared.network.ApiService
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.koin.dsl.module

fun sharedModule(baseUrl: String, database: NeoVitaDatabase?) = module {
    if (database != null) single { database }
    single {
        HttpClient {
            install(ContentNegotiation) { json() }
        }
    }
    single { ApiService(baseUrl, get()) }
    single<AssessmentRepository> { AssessmentRepositoryImpl(get(), get()) }
    single<PlanRepository> { PlanRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
    factory { CalculateScoresUseCase() }
}
