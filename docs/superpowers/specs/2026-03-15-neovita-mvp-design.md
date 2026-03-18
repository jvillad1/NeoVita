# NeoVita MVP — Design Spec
**Date:** 2026-03-15
**Status:** Approved
**Platforms:** Android · iOS · Web

---

## 1. Visión General

NeoVita es una app de coaching integral de longevidad con IA, dirigida a adultos mayores de 45 años en contexto colombiano. El MVP valida el flujo completo B2C (usuario individual) y el panel B2B (empleador). La app usa Claude API como coach personalizado de longevidad.

**Modelo de negocio:** B2C (usuarios directos) + B2B (empresas que ofrecen NeoVita como beneficio de bienestar a sus empleados).

---

## 2. Plataformas y Stack

| Decisión | Elección | Razón |
|----------|----------|-------|
| Framework | Kotlin Multiplatform (KMP) | Un solo codebase para Android, iOS y Web |
| UI | Compose Multiplatform | Una sola UI compartida en las 3 plataformas |
| Navegación | Voyager | Soporte nativo KMP, rutas URL en Web |
| Backend | Ktor (módulo `:server`) | Proxy seguro para Claude API + API REST |
| BD | PostgreSQL + Exposed ORM | Persistencia de usuarios, evaluaciones y planes |
| Caché local | SQLDelight | Offline-first, compartido entre plataformas |
| Auth | Google OAuth 2.0 | Sin fricción para el usuario |
| Red (cliente) | Ktor Client | Compartido en `:shared`, soporta SSE streaming |
| DI | Koin | Multiplatform, simple para MVP |
| Serialización | Kotlinx Serialization | Estándar KMP |
| Imágenes | Coil 3 | Soporte Compose Multiplatform |

---

## 3. Arquitectura de Módulos (Monorepo Gradle)

```
neovita/
├── server/          # Ktor backend
├── shared/          # Lógica KMP compartida
│   ├── domain/      # Modelos, casos de uso, interfaces de repositorios
│   ├── data/        # Repositorios, SQLDelight, mappers
│   └── network/     # Ktor Client, ApiService, DTOs
└── composeApp/      # UI Compose Multiplatform
    ├── androidMain/
    ├── iosMain/
    ├── wasmJsMain/  # Web
    └── commonMain/  # Screens, ViewModels, Navigation
```

### 3.1 Módulo `:server`
- Framework: Ktor con Netty engine
- Expone REST API + SSE streaming para chat
- Proxy seguro hacia Anthropic Claude API (la API key nunca sale del servidor)
- Verifica tokens Google OAuth y emite JWT propio
- Persistencia con Exposed ORM sobre PostgreSQL

### 3.2 Módulo `:shared`
Código Kotlin puro compartido entre Android, iOS y Web.

**Capa domain:**
- Modelos: `User`, `Assessment`, `LongevityPlan`, `PillarScores`, `ChatMessage`
- Casos de uso: `GeneratePlanUseCase`, `SendChatMessageUseCase`, `CalculateScoresUseCase`, `GetCurrentUserUseCase`
- Interfaces de repositorios: `UserRepository`, `AssessmentRepository`, `PlanRepository`, `ChatRepository`

**Capa data:**
- Implementaciones de repositorios
- SQLDelight para caché local (plan activo, última evaluación, historial de chat)
- Mappers entre DTOs de red y modelos de dominio

**Capa network:**
- `ApiService` con Ktor Client
- Soporte SSE para streaming del chat y generación de plan
- Manejo centralizado de errores de red

### 3.3 Módulo `:composeApp`
- Screens en `commonMain`: LoginScreen, OnboardingScreen, AssessmentScreen, ResultsScreen, DashboardScreen, PlanScreen, ChatScreen, B2BScreen, ProfileScreen
- ViewModels en `commonMain` (kotlin Coroutines + StateFlow)
- Google Sign-In implementado de forma nativa por plataforma (expect/actual)
- Rutas Web mapeadas a URLs del browser vía Voyager

---

## 4. Pantallas del MVP

| # | Pantalla | Descripción | Roles |
|---|----------|-------------|-------|
| 1 | LoginScreen | Google Sign-In | Todos |
| 2 | OnboardingScreen | Nombre y edad (solo primer uso) | B2C, B2B |
| 3 | AssessmentScreen | 5 preguntas: frecuencia ejercicio, tipo ejercicio, horas sueño, calidad sueño (1-10), objetivo principal | B2C, B2B |
| 4 | ResultsScreen | Índice de longevidad general + scores por pilar con barras de progreso | B2C, B2B |
| 5 | DashboardScreen | Home: índice de longevidad, tareas del día, accesos rápidos por pilar | B2C, B2B |
| 6 | PlanScreen | Plan personalizado generado por Claude: recomendaciones de nutrición, sueño y ejercicio | B2C, B2B |
| 7 | ChatScreen | Chat libre con coach NeoVita (Claude). Streaming SSE, chips de sugerencias rápidas | B2C, B2B |
| 8 | B2BScreen | Métricas del equipo: índice promedio, lista de empleados con scores. Solo rol `EMPLOYER` | B2B |
| 9 | ProfileScreen | Datos de usuario, historial de evaluaciones, re-assessment, logout | Todos |

---

## 5. Flujo de Navegación

```
App Launch
    ↓
¿JWT válido?
    ├─ No → LoginScreen (Google Sign-In) → ¿Primera vez?
    │            ├─ Sí → OnboardingScreen → AssessmentScreen → ResultsScreen → App Principal
    │            └─ No → App Principal
    └─ Sí → App Principal

App Principal (Bottom Navigation)
    ├─ Dashboard
    ├─ Mi Plan
    ├─ Chat IA
    ├─ Empresa (solo EMPLOYER)
    └─ Perfil → Re-AssessmentScreen (opcional)
```

**Rutas Web (Voyager → URL del browser):**
`/login`, `/onboarding`, `/assessment`, `/results`, `/dashboard`, `/plan`, `/chat`, `/b2b`, `/profile`

---

## 6. Backend API

### Endpoints

```
POST   /auth/google          → Verifica Google ID token, devuelve JWT propio
GET    /users/me             → Perfil del usuario autenticado
PATCH  /users/me             → Actualiza nombre, edad u otros campos de perfil (usado en OnboardingScreen)

POST   /assessments          → Guarda evaluación completa
GET    /assessments/latest   → Obtiene la evaluación más reciente del usuario

GET    /plans/current        → Plan de longevidad activo
POST   /plans/generate       → Genera nuevo plan vía Claude API (SSE streaming)

POST   /chat                 → Envía mensaje al coach, responde en SSE streaming

GET    /b2b/team             → Métricas del equipo (requiere rol EMPLOYER)
```

### Formato de error estándar
```json
{ "code": "AUTH_INVALID_TOKEN", "message": "El token de Google es inválido o ha expirado" }
```

---

## 7. Modelos de Dominio

```kotlin
data class User(
    val id: String,
    val name: String,
    val email: String,
    val age: Int,
    val role: UserRole,          // USER | EMPLOYER
    val companyId: String?
)

data class Assessment(
    val id: String,
    val userId: String,
    val createdAt: Instant,
    val exerciseFrequency: String,
    val exerciseType: String,
    val sleepHours: String,      // Valores: "4-6", "6-8", "8+" (selección del usuario)
    val sleepQuality: Int,       // Rango: 1-10
    val mainGoal: String
)

data class LongevityPlan(
    val id: String,
    val userId: String,
    val generatedAt: Instant,
    val nutrition: List<String>,
    val sleep: List<String>,
    val exercise: List<String>,
    val scores: PillarScores
)

data class PillarScores(
    val overall: Int,            // 0-100
    val exercise: Int,
    val sleep: Int,
    val nutrition: Int
)

data class ChatMessage(
    val id: String,
    val role: MessageRole,       // USER | ASSISTANT
    val content: String,
    val timestamp: Instant
)
```

---

## 8. Manejo de Errores

| Escenario | Comportamiento |
|-----------|----------------|
| Sin conexión a red | Mostrar datos en caché (SQLDelight) con banner "Modo offline" |
| Error de red (5xx) | Banner no intrusivo + botón retry con exponential backoff |
| Auth expirada (401) | Redirect a LoginScreen, limpia JWT local |
| Streaming cortado antes de enviar | Mensaje se mantiene en el input + banner "Sin conexión" |
| Streaming cortado durante respuesta | Muestra el texto recibido + botón "Continuar" o "Nuevo mensaje" |
| Claude API timeout | Error amigable: "El coach no está disponible, intenta en unos minutos" |
| Google token inválido | 401 con mensaje claro en LoginScreen |

---

## 9. Testing

| Capa | Qué testear | Herramienta | Prioridad MVP |
|------|-------------|-------------|---------------|
| `:shared` domain | Cálculo de scores (`CalculateScoresUseCase`), mappers | kotlin.test | Alta |
| `:shared` network | Serialización/deserialización de DTOs | kotlin.test + MockEngine | Media |
| `:server` | Endpoints REST, flujo de auth, generación de plan | Ktor Test Engine | Alta |
| `:composeApp` | Navegación entre screens, estados de carga | Compose UI Test | Media |

El MVP prioriza tests de lógica de negocio (cálculo de scores) y endpoints críticos (auth y generación de plan). Tests de UI son opcionales para la primera iteración.

---

## 10. Restricciones y Decisiones

- **minSdk Android:** 26 (Android 8.0) — cubre >95% de dispositivos activos
- **iOS mínimo:** iOS 16
- **Web target:** Compose for Web (WASM/JS) — requiere browser moderno con soporte WASM
- **Claude API key:** Almacenada únicamente en variables de entorno del servidor, nunca en el cliente
- **B2B:** La vista de empresa solo se muestra si `user.role == EMPLOYER`. El rol se asigna manualmente en la BD para el MVP (no hay flujo de registro de empresa en este MVP). Post-MVP: agregar panel admin o flujo de invitación para asignar el rol EMPLOYER
- **Idioma:** La app es en español (contexto colombiano). Los prompts a Claude incluyen instrucción de responder en español con referencias culturales colombianas
- **Offline:** El plan activo y la última evaluación se cachean en SQLDelight. El chat requiere conexión

---

## 11. Fuera del Alcance (MVP)

- Registro de empresa / onboarding B2B (el rol EMPLOYER se asigna manualmente)
- Notificaciones push
- Wearables / integración con Apple Health o Google Fit
- Pagos / suscripciones
- Soporte multiidioma
- Dashboard de analytics avanzado para empresa
