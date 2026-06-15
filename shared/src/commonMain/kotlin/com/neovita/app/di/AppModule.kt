package com.neovita.app.di

import com.neovita.app.auth.GoogleSignInClient
import com.neovita.app.screens.admin.ContentAdminViewModel
import com.neovita.app.screens.assessment.AssessmentViewModel
import com.neovita.app.screens.chat.ChatViewModel
import com.neovita.app.screens.dashboard.DashboardViewModel
import com.neovita.app.screens.login.LoginViewModel
import com.neovita.app.screens.onboarding.OnboardingViewModel
import com.neovita.app.screens.plan.PlanViewModel
import com.neovita.app.screens.profile.ProfileViewModel
import com.neovita.app.screens.results.ResultsViewModel
import org.koin.dsl.module

val appModule = module {
    factory { GoogleSignInClient() }
    factory { LoginViewModel(get(), get()) }
    factory { DashboardViewModel(get(), get(), get()) }
    factory { ChatViewModel(get()) }
    factory { PlanViewModel(get()) }
    factory { AssessmentViewModel(get()) }
    factory { OnboardingViewModel(get()) }
    factory { ResultsViewModel(get()) }
    factory { ProfileViewModel(get(), getOrNull()) }
    factory { ContentAdminViewModel(get()) }
}
