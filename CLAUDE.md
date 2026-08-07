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

Requires a local PostgreSQL database `everything_app` (see `src/main/resources/application.properties` for credentials/port — currently hardcoded there, not via env vars). `spring.jpa.hibernate.ddl-auto=update`, so schema is auto-migrated from entities; there are no separate migration scripts.

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

The app talks to the backend at `http://localhost:8080/api` (`lib/config/api_config.dart`). For a real Android device, the project relies on `adb reverse tcp:8080 tcp:8080` rather than switching the base URL. Android emulator/iOS simulator need the base URL swapped manually (`10.0.2.2` / `localhost` per the comments in `api_config.dart`).

## Backend architecture

Standard layered Spring Boot structure, repeated per domain (task, calendar, study, sports, recipe, finance, habit, project):

`controller` → `service` → `repository` (Spring Data JPA), with `model` (JPA entities), `dto` (API payloads), and `mapper` (hand-written entity↔DTO mappers, not MapStruct) as supporting layers. `exception` holds custom exceptions plus a single `GlobalExceptionHandler` (`@RestControllerAdvice`) that converts them to a common `ErrorResponse` JSON shape.

**Auth**: stateless JWT. `JwtAuthenticationFilter` validates the bearer token on every request; `SecurityConfig` permits only `/api/auth/register`, `/api/auth/login`, `/api/auth/dev-login`, and `/error` without auth. Controllers get the authenticated user via a custom `@CurrentUser Long userId` parameter, resolved by `CurrentUserArgumentResolver` (registered in `WebConfig`), which pulls the user ID straight out of the JWT — there's no `Authentication`/`Principal` plumbing in controller signatures.

**Smart Scheduler** (`service/SmartSchedulerService`) is the architectural centerpiece: it uses Google OR-Tools CP-SAT (`com.google.ortools`) to auto-schedule tasks around fixed blocks (sleep, existing calendar events, course schedules), then greedily fills remaining free slots with recurring habits/workouts. It's triggered by an async `ScheduleChangedEvent` (via `@TransactionalEventListener(phase = AFTER_COMMIT)`), so schedule regeneration happens after the triggering transaction commits, not inline with the request. `ScheduleInput`/`ScheduledItem`/`ScheduleResult`/`TimeSlot` are the solver's internal working types (not DTOs).

CORS is wide open (`allowedOrigins("*")`) and CSRF is disabled — this is a stateless JWT API, not cookie-based.

## Frontend architecture

Feature-organized under `lib/`: `screens/<feature>/`, `providers/<feature>_provider.dart`, `services/<feature>_service.dart`, `models/<feature>.dart` — one triplet of provider/service/model per backend domain, mirroring the backend's domain split.

- **State**: `provider` package. Every feature has a `ChangeNotifierProvider` registered in `main.dart`'s `MultiProvider`; screens read state via `context.watch`/`context.read`.
- **Networking**: all services go through the shared `ApiService` (`lib/services/api_service.dart`), which centralizes base-URL resolution, auth headers (bearer token from `flutter_secure_storage`), timeouts, and request logging. Feature services (`TaskService`, `CalendarService`, etc.) wrap `ApiService` calls and map JSON to models — don't call `http`/`dio` directly from a provider or screen.
- **Routing**: `go_router` (`lib/config/routes.dart`). A top-level `redirect` guards all routes on `AuthProvider.isLoggedIn`. Main tab screens (`/home`, `/calendar`, `/spaces`, `/create`) live inside a `ShellRoute` with the persistent `MainScaffold`/bottom nav; feature "space" screens (`/study`, `/sports`, `/tasks`, etc.) are top-level routes outside the shell with their own in-screen navigation/back button.
- **Endpoints**: all backend URLs are centralized as string constants/builders in `lib/config/api_config.dart` — add new endpoints there rather than inlining URL strings in services.

Many UI strings, comments, and error messages are in German — match the existing language when editing nearby code rather than switching to English.
