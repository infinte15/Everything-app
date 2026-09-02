# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Everything App is a personal productivity app (tasks, calendar, study, sports, recipes, finance, habits, projects) with a "smart scheduler" that auto-places tasks/habits/workouts into free calendar slots using a CP-SAT constraint solver. Two independent apps in one repo:

- `Everything-app-backend/everything-app` — Spring Boot 3.2 / Java 17 REST API
- `Everything-app-frontend/everything_app` — Flutter app (Provider for state, go_router for navigation)

There is a root `pom.xml` that aggregates the backend module, but it exists only to build the backend — there is no unified build across both apps. Run backend and frontend commands from their respective directories.

## Commands

### Backend (`Everything-app-backend/everything-app`)

```bash
./mvnw spring-boot:run          # run the API on :8080
./mvnw test                     # run all tests
./mvnw test -Dtest=SmartSchedulerServiceTest          # run a single test class
./mvnw test -Dtest=SmartSchedulerServiceTest#methodName  # run a single test method
./mvnw clean package             # build the jar
```

Requires a local PostgreSQL database `everything_app`. Non-secret config is in `src/main/resources/application.properties` (checked in); the DB password and `jwt.secret` live in `application-secrets.properties` **next to `pom.xml`**, not in `src/main/resources` — a file under `resources/` would end up inside every jar built here. It is gitignored and loaded via `spring.config.import=optional:file:./application-secrets.properties`; copy `application-secrets.properties.example` to create it. `spring.jpa.hibernate.ddl-auto=update`, so schema is auto-migrated from entities; there are no separate migration scripts.

Production runs under the `prod` profile (`application-prod.properties`, `SPRING_PROFILES_ACTIVE=prod`), which takes every secret from environment variables and switches off dev-login, self-registration and CORS. See `DEPLOYMENT.md`.

**Demo-Datenbestand** (`seed/demo/DemoDataSeeder`) — füllt alle Spaces und den Kalender für den `dev_tester`-Nutzer, damit sich die App vorführen lässt. Standardmäßig aus:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.demo-seed.enabled=true"
# neu aufbauen (löscht vorher alle Daten des Nutzers):
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.demo-seed.enabled=true --app.demo-seed.reset=true"
```

Alle Daten hängen an `LocalDate.now()`; am Ende läuft einmal der Smart Scheduler, damit der Kalender gefüllt ist. Anmeldung danach über `POST /api/auth/dev-login`.

### Frontend (`Everything-app-frontend/everything_app`)

```bash
flutter pub get                 # install dependencies
flutter run                     # run on connected device/emulator
flutter test                    # run all tests
flutter test test/widget_test.dart   # run a single test file
flutter analyze                 # static analysis (flutter_lints)
```

The base URL comes from `--dart-define=API_BASE_URL=...` at build time and defaults to `http://localhost:8080/api` (`lib/config/api_config.dart`). For a real Android device the project relies on `adb reverse tcp:8080 tcp:8080`, so the default works there too; for the Android emulator pass `--dart-define=API_BASE_URL=http://10.0.2.2:8080/api`. On web, `kIsWeb` resolves the base URL from `Uri.base.origin` instead — app and API sit on the same origin behind Caddy, so no define and no CORS.

## Backend architecture

Standard layered Spring Boot structure, repeated per domain (task, calendar, study, sports, recipe, finance, habit, project):

`controller` → `service` → `repository` (Spring Data JPA), with `model` (JPA entities), `dto` (API payloads), and `mapper` (hand-written entity↔DTO mappers, not MapStruct) as supporting layers. `exception` holds custom exceptions plus a single `GlobalExceptionHandler` (`@RestControllerAdvice`) that converts them to a common `ErrorResponse` JSON shape.

**Auth**: stateless JWT. `JwtAuthenticationFilter` validates the bearer token on every request; `SecurityConfig` permits only `/api/auth/login`, `/api/finance/bank/callback` and `/error` without auth; `/api/auth/register` and `/api/auth/dev-login` are added to that list only when their respective flags are on (both off in production). Controllers get the authenticated user via a custom `@CurrentUser Long userId` parameter, resolved by `CurrentUserArgumentResolver` (registered in `WebConfig`), which pulls the user ID straight out of the JWT — there's no `Authentication`/`Principal` plumbing in controller signatures.

**Smart Scheduler** (`service/SmartSchedulerService`) is the architectural centerpiece: it uses Google OR-Tools CP-SAT (`com.google.ortools`) to auto-schedule tasks around fixed blocks (sleep, existing calendar events, course schedules), then greedily fills remaining free slots with recurring habits/workouts. It's triggered by an async `ScheduleChangedEvent` (via `@TransactionalEventListener(phase = AFTER_COMMIT)`), so schedule regeneration happens after the triggering transaction commits, not inline with the request. `ScheduleInput`/`ScheduledItem`/`ScheduleResult`/`TimeSlot` are the solver's internal working types (not DTOs).

CSRF is disabled — this is a stateless JWT API, not cookie-based. CORS is driven by `app.cors.allowed-origins` (empty = no cross-origin access at all, which is what production uses). `app.dev-login.enabled` gates both the `/api/auth/dev-login` path *and* whether `DevAuthController` exists as a bean; `app.registration.enabled` does the same for `/api/auth/register`. `LoginRateLimitFilter` caps `/api/auth/login` at 10 attempts per minute per client IP.

## Frontend architecture

Feature-organized under `lib/`: `screens/<feature>/`, `providers/<feature>_provider.dart`, `services/<feature>_service.dart`, `models/<feature>.dart` — one triplet of provider/service/model per backend domain, mirroring the backend's domain split.

- **State**: `provider` package. Every feature has a `ChangeNotifierProvider` registered in `main.dart`'s `MultiProvider`; screens read state via `context.watch`/`context.read`.
- **Networking**: all services go through the shared `ApiService` (`lib/services/api_service.dart`), which centralizes base-URL resolution, auth headers (bearer token from `flutter_secure_storage`), timeouts, and request logging. Feature services (`TaskService`, `CalendarService`, etc.) wrap `ApiService` calls and map JSON to models — don't call `http`/`dio` directly from a provider or screen.
- **Routing**: `go_router` (`lib/config/routes.dart`). A top-level `redirect` guards all routes on `AuthProvider.isLoggedIn`. Main tab screens (`/home`, `/calendar`, `/spaces`, `/create`) live inside a `ShellRoute` with the persistent `MainScaffold`/bottom nav; feature "space" screens (`/study`, `/sports`, `/tasks`, etc.) are top-level routes outside the shell with their own in-screen navigation/back button.
- **Endpoints**: all backend URLs are centralized in `lib/config/api_config.dart` — add new endpoints there rather than inlining URL strings in services. They are `static String get` getters, not `const`, because `baseUrl` is resolved at runtime; new entries must follow that form.

Many UI strings, comments, and error messages are in German — match the existing language when editing nearby code rather than switching to English.
