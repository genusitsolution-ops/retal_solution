# Rental Solution Android — architecture (v1.2)

**MVVM + Clean Architecture** with Retrofit 2, Kotlin Coroutines, StateFlow / SharedFlow and Hilt.
The screens, texts, roles, API calls and business rules are the same as app 1.1. Only how the code is
organised changed, plus the login/redirect fixes described in FIX-NOTES.md.

```
┌──────────────────────── app (Android) ────────────────────────┐
│ Compose screen ──collects──▶ @HiltViewModel                   │
│   (ui/…Screens.kt)            StateFlow<UiState>  screen state │
│        ▲                      SharedFlow<ScreenEvent> one-time │
│        └── user actions ──▶   calls use cases                  │
│ di/  Hilt modules: NetworkModule, RepositoryModule, AppModule  │
│ platform/  SessionStore (DataStore), AppLanguage               │
└───────────────────────────────┬───────────────────────────────┘
                                │ depends on
┌──────────────────────── domain (pure Kotlin) ─────────────────┐
│ usecase/   LoginUseCase, RecordPaymentUseCase, …  (one class  │
│            per operation; business rules live here)           │
│ repository/ interfaces (AuthRepository, BillingRepository, …) │
│ model/     data models, Session, LoginOutcome                 │
│ error/     ApiException (stable codes)                        │
└───────────────────────────────▲───────────────────────────────┘
                                │ implements
┌──────────────────────── data (pure Kotlin) ───────────────────┐
│ remote/    PrmsApiService (Retrofit), ApiCaller (envelope →    │
│            data / ApiException), HeaderInterceptor,           │
│            SafeRedirectInterceptor, PrmsNetwork (OkHttp+Retrofit)│
│ repository/ *RepositoryImpl                                   │
└───────────────────────────────────────────────────────────────┘
```

The dependency rule: `app → data → domain`. Domain knows nothing about Retrofit or Android, and data
knows nothing about Android. Both are plain JVM modules, so their tests run without an emulator.

## How the requirements map to the code

| Requirement | Where |
|---|---|
| Retrofit 2 | `data/remote/PrmsApiService.kt` (every endpoint of API v1), `PrmsNetwork.retrofit()` with the kotlinx.serialization converter |
| OkHttp | `PrmsNetwork.okHttp()`: timeouts 15 s connect / 30 s read+write; `HeaderInterceptor` (Accept, Accept-Language, User-Agent, Bearer token); `SafeRedirectInterceptor`; BASIC logging in debug builds only (no headers or bodies, so no passwords or tokens in logs) |
| Coroutines | `suspend` Retrofit functions, use cases and repositories; `viewModelScope` in ViewModels; an application scope for work that must outlive a screen (logout, session expiry, saving the language) |
| StateFlow | `Loadable` / `ParamLoadable` (`UiState`: Loading, Data, Error) and `ActionRunner` (`ActionState`: Idle, Running, Done, Failed) in `ui/common/State.kt`; `LoginViewModel.state`; session flows |
| SharedFlow | `BaseViewModel.events: SharedFlow<ScreenEvent>` for navigation and one-off UI effects (Saved, ReceiptReady, InvoiceCreated, ReplySent, PasswordChanged, ChargeAdded). It holds the event (replay 1) until the screen consumes it, so it isn't lost while the app is in the background. Collected with `repeatOnLifecycle(STARTED)` (`EventEffect`) |
| Hilt | `@HiltAndroidApp PrmsApplication`, `@AndroidEntryPoint MainActivity`, a `@HiltViewModel` per screen with `hiltViewModel()`, modules in `app/di/`. Repositories are bound with `@Binds`; use cases and repository implementations use `@Inject` constructors |
| MVVM | `ui/<feature>/*ViewModels.kt`: 38 ViewModels, one per screen. Screens hold only UI state (text fields, dialogs) and never call the API |
| Clean Architecture | `domain` (use cases + repository interfaces), `data` (implementations), `app` (presentation + DI) |

## Error handling and security (unchanged behaviour, now in one place)

- **`ApiCaller`:** network failures (DNS, refused, TLS, timeout) become `NETWORK_ERROR`, which shows
  "No internet connection or the server is not reachable".
  - A redirect that wasn't followed becomes `SERVER_REDIRECT`.
  - HTML or an empty body becomes `BAD_RESPONSE`. Users see "The server is not responding correctly…", and
    debug builds also show the technical detail.
  - `{"status":"error"}` keeps the server's code and its localized message.
- **401 anywhere:** `SessionExpiryHandler` clears the session and the app returns to the login screen.
- **Payments and collections:**
  - `ActionRunner` ignores double taps.
  - The idempotency key is reused on retry and renewed only after success, so the server records a payment
    at most once. It is also kept across a followed redirect.
- **HTTPS only:** cleartext is blocked by `network_security_config.xml`. The redirect policy never downgrades
  to HTTP or leaves the API folder.
- **Session token:** stored in app-private DataStore, which is excluded from backups.
- **No secrets in the source:** release signing comes from `keystore.properties` or CI secrets.

## Screen inventory / regression checklist

Each row lists the ViewModel and the endpoint it calls. All 38 ViewModels, the DI modules and `State.kt` were
type-checked locally. The Compose screens are compiled by Gradle in CI.

| Role | Screen | ViewModel | API |
|---|---|---|---|
| All | Login (EN/हिं/मरा) | LoginViewModel | POST auth/login.php (+ logout.php for Super Admin) |
| All | Session gate | AppViewModel | DataStore |
| All | Notifications, mark all read | NotificationsViewModel | GET/POST common/notifications.php |
| All | Receipt + WhatsApp/share | ReceiptViewModel | GET common/receipt.php |
| All | Settings: language, contact details, password, logout | SettingsViewModel | GET/PUT auth/me.php · PUT tenant/employee profile.php · POST auth/logout.php |
| Owner | Home dashboard | OwnerHomeViewModel | GET owner/dashboard.php |
| Owner | Tenants list / detail / delete | OwnerTenantsViewModel, OwnerTenantDetailViewModel | GET/DELETE owner/tenants.php |
| Owner | Add / edit tenant (+ tenant login) | TenantFormViewModel | GET/POST/PUT owner/tenants.php |
| Owner | Bills list, "collect" picker | OwnerInvoicesViewModel | GET owner/invoices.php |
| Owner | Bill detail, cancel bill | InvoiceDetailViewModel | GET + POST(action=cancel) owner/invoices.php |
| Owner | Record payment | RecordPaymentViewModel | POST owner/invoices.php (Idempotency-Key), GET common/meta.php |
| Owner | Payments received | OwnerPaymentsViewModel | GET owner/payments.php |
| Owner | Rooms list / add / edit / delete | PropertiesViewModel, AddPropertyViewModel, EditPropertyViewModel | owner/properties.php |
| Owner | Charges: list, add, (de)activate | ChargesViewModel | owner/charges.php |
| Owner | Allocations: list, collect deposit, vacate | AllocationsViewModel | owner/allocations.php |
| Owner | Allocate room | NewAllocationViewModel | GET properties/tenants/charges, POST owner/allocations.php |
| Owner | Generate bill (meter readings) | GenerateInvoiceViewModel | GET(action=context) + POST(action=generate) owner/invoices.php |
| Owner | Collection agents list / add / edit | EmployeesViewModel, EmployeeFormViewModel | owner/employees.php |
| Owner | Agent detail: assign rooms, (de)activate, delete | EmployeeDetailViewModel | owner/employees.php (+ action=assign) |
| Owner | Complaints list / conversation / status | OwnerQueriesViewModel, QueryDetailViewModel | owner/queries.php |
| Owner | Reports: collections, revenue, pending | ReportsViewModel | owner/reports.php |
| Owner | Refer & earn | ReferralViewModel | owner/referrals.php |
| Owner | Subscription + renewal request | SubscriptionViewModel | owner/subscription.php |
| Agent | Home dashboard | AgentHomeViewModel | employee/dashboard.php |
| Agent | My tenants | AgentTenantsViewModel | employee/tenants.php |
| Agent | My collections | AgentCollectionsViewModel | employee/collections.php |
| Agent | Collect rent (oldest-first or a chosen bill) | AgentCollectViewModel | POST employee/collections.php (Idempotency-Key) |
| Tenant | Home dashboard, call owner | TenantHomeViewModel | tenant/dashboard.php |
| Tenant | My bills / bill detail | TenantInvoicesViewModel, TenantInvoiceDetailViewModel | tenant/invoices.php |
| Tenant | Payment history | TenantPaymentsViewModel | tenant/payments.php |
| Tenant | Complaints list / conversation / raise | TenantQueriesViewModel, QueryDetailViewModel, RaiseQueryViewModel | tenant/queries.php |

Super Admin is unchanged: it is told to use the web panel, and its token is revoked right after login.

## Tests

- `domain/src/test/DomainTest.kt` covers:
  - money formatting and validators;
  - login rules (owner, agent and tenant are signed in; admin is sent to the web panel and its token revoked;
    wrong password);
  - logout always clears the session;
  - complaint reply rules.
- `data/src/test/ApiIntegrationTest.kt` runs the real Retrofit + OkHttp stack against MockWebServer:
  - login JSON and headers;
  - Bearer token and Idempotency-Key;
  - error classification (401, 422, 500 with HTML, empty body, network down);
  - query strings, with nulls left out;
  - lenient JSON decoding;
  - the HTTP 301 fix (".php"-strip and www redirects are followed with the same method and body; the
    idempotency key survives a redirect; the web login page, other hosts, plain HTTP, other ports, 303 and
    redirect loops are refused).
