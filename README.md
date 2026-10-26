# dddonion - Reactive DDD + Onion + Hexagonal example (Spring Boot 4, WebFlux, R2DBC, Liquibase)

Dieses Projekt demonstriert Domain-Driven Design (DDD), Onion Architecture und Hexagonal Architecture (Ports & Adapters) in einem reaktiven Spring-Stack.

## Stack
- Web: Spring WebFlux
- API-Contract: OpenAPI 3 (design-first); der WebFlux-Controller implementiert die aus der YAML generierte Schnittstelle
- Persistenz: R2DBC mit MariaDB
- Migrationen: Liquibase (formatted SQL)
- Ops: Actuator Readiness/Liveness, ArchUnit
- Monitoring: separates Modul `dddonion-monitor` mit asynchronem Domain-Event-Logging
- Tests: JUnit 6/Jupiter, Reactor Test, RestAssured, Testcontainers (MariaDB)

## Module
```text
dddonion/
|- dddonion-domain
|  |- domain/common/DomainEvent, Money, ConcurrentAggregateUpdateException
|  |- domain/customer/model/Customer, CustomerId, CustomerEmail
|  |- domain/customer/command/RegisterCustomerCommand, ChangeCreditLimitCommand
|  |- domain/customer/repository/CustomerRepository (Port)
|  |- domain/order/model/Order, OrderId, OrderStatus
|  |- domain/order/command/PlaceOrderCommand
|  |- domain/order/event/OrderPlaced
|  \- domain/order/repository/OrderRepository (Port)
|- dddonion-architecture
|  \- architecture/UseCase, ApplicationComponent, TransactionalUseCase
|- dddonion-application
|  |- application/event/DomainEventPublisherPort
|  |- application/context/UseCaseMetadata, ActorType, UseCaseContextKeys
|  |- application/customer/usecase/RegisterCustomerUseCase, GetCustomerUseCase, ChangeCreditLimitUseCase
|  |- application/customer/service/RegisterCustomerService, GetCustomerService, ChangeCreditLimitService
|  |- application/order/usecase/PlaceOrderUseCase, GetOrderUseCase
|  \- application/order/service/PlaceOrderService, GetOrderService
|- dddonion-adapter-webflux
|  \- infrastructure/web/OrderController, CustomerController, UseCaseMetadataFactory
|- dddonion-adapter-r2dbc
|  \- infrastructure/persistence/r2dbc/... (Order- und Customer-Adapter, je mit Repo + Mapper + DTO)
|- dddonion-monitor
|  \- monitor/DomainEventLoggingListener
|- dddonion-boot
|  |- bootstrap/DddOnionApplication, BeansConfig (nur Adapter), ApplicationWiringConfig, AsyncConfig
|  |- bootstrap/observability/UseCaseObservability, AuditLogObservationHandler
|  |- bootstrap/usecase/UseCasePipelinePostProcessor, UseCasePipelineConfig
|  \- bootstrap/transaction/UseCaseTransaction
\- dddonion-archunit
   \- Architekturregeln (Layer, Abhängigkeiten, Adapter-Isolation, Use-Case-Konventionen, Aggregate-Grenzen, transaktionale Writes)
```

## Architekturzuordnung
- Domain: enthält Aggregate, Value Objects, Domain Events und Domain-Ports; keine Abhängigkeit auf Spring/Infrastruktur. Einzige Zusatzabhängigkeit ist `reactor-core`: Repository-Ports liefern `Mono` (lazy, ein leerer `Mono` bedeutet „nicht gefunden“), damit der Reactor-Context bis in die Adapter durchgereicht wird.
- Architecture: Spring-freie Annotationen, die Use Cases beschreiben und für das Wiring markieren; einzige erlaubte Zusatzabhängigkeit der Application-Schicht.
- Application: orchestriert Use Cases, ruft Domain und Ports auf.
- Adapter (Infrastruktur): implementieren Ports und binden Frameworks/IO (HTTP, DB).
- Boot: Composition Root: Adapter-Beans in `BeansConfig`; Application Services per Component-Scan auf `@ApplicationComponent`; jeder Use Case wird automatisch beobachtet, `@TransactionalUseCase`-Services zusätzlich mit Retry und Transaktion (`UseCaseTransaction`); `AsyncConfig` aktiviert `@EnableAsync` und stellt den `applicationTaskExecutor` (virtuelle Threads, MDC-Weitergabe) für alle asynchronen Listener.
- Monitor: asynchrone Beobachtung von Domain Events via Spring Events; enthält nur den Listener, der Executor kommt aus Boot.

## Mehrere Aggregates
- **Referenz nur per ID:** `Order` hält `CustomerId` statt `Customer`, so bleiben Aggregates lose gekoppelt. ArchUnit: `aggregates_reference_each_other_only_by_id`.
- **Mehrere Aggregates pro Transaktion:** `PlaceOrder` ändert Customer und Order atomar: Der Kredit wird reserviert und die Order angelegt, beides in einer Transaktion (siehe "Transaktionen").
- **Eingehende Adapter nur über Use Cases:** Klassen in `infrastructure.web` greifen nicht auf Repositories zu. ArchUnit: `inbound_adapters_do_not_access_repositories`.
- **Reaktion über Events:** Nur der Monitor reagiert auf Domain Events (`DomainEventLoggingListener`); fachliche Folgen laufen nicht über Events.
- **Geteilter Code:** Retry und Transaktion liegen in der Pipeline statt in einem geteilten Baustein; für anderen geteilten Code gibt es weder Basisklasse noch Use-Case-zu-Use-Case-Aufruf, damit Use Cases unabhängig bleiben. ArchUnit: `use_cases_do_not_call_use_cases` und `aggregate_use_cases_are_independent`.
- **Schreiben nur transaktional:** Jeder Zugriff aus `application` auf einen Repository-Port, der nicht mit `find` oder `exists` beginnt (auch per Methodenreferenz), setzt `@TransactionalUseCase` an der Service-Klasse voraus. ArchUnit: `repository_writes_only_in_transactional_use_cases`.
- **Events nur transaktional:** Jeder Aufruf von `DomainEventPublisherPort.publish` aus `application` (auch per Methodenreferenz) setzt ebenfalls `@TransactionalUseCase` voraus, sonst scheitert er schon im Build statt erst zur Laufzeit. ArchUnit: `domain_events_published_only_in_transactional_use_cases`.
- **Fachlogik am Aggregate:** Die Kreditprüfung samt Reservierung ist `Customer.reserveCredit` und kein Domain Service. Ein Domain Service ist erst gerechtfertigt, wenn eine Regel den Zustand mehrerer Aggregates braucht.

Zielbild:
- Ein Bounded Context pro Microservice, mehrere Aggregates je Service.
- Fachcode wird zwischen Services nicht geteilt.
- Technische Bausteine (`dddonion-architecture`, Pipeline, ArchUnit-Regeln) sind Kandidaten für eine spätere Plattform-Bibliothek.

## REST-API (OpenAPI, design-first)
- Vertrag (Single Source of Truth): `dddonion-adapter-webflux/src/main/resources/openapi/api.yaml`.
- Aus dem Vertrag generiert das `openapi-generator-maven-plugin` (Phase `generate-sources`) eine reaktive Interface- und DTO-Schnittstelle nach `net.rsworld.example.dddonion.infrastructure.web.{api,dto}`.
- `OrderController implements OrdersApi` und `CustomerController implements CustomersApi` – die HTTP-Signatur (Pfad, JSON-Bodies, Statuscodes, Bean-Validation) stammt damit aus der YAML, nicht aus handgeschriebenen Annotationen.
- Fehler werden als RFC 7807 `application/problem+json` beantwortet (`spring.webflux.problemdetails.enabled=true` plus `ApiExceptionHandler` für die Domain-Validierung).
- Endpunkte:
  - `POST /orders`, `Content-Type: application/json`, Body `{ "customerId": "...", "total": 12.34 }`
    - Erfolg: `201 Created`, Header `Location: /orders/{orderId}`, Body `{ "orderId": "..." }`
    - Ungültige Eingabe (z. B. keine gültige `customerId`): `400`, `application/problem+json`
    - Unbekannter Customer oder Kreditlimit überschritten: `422`, `application/problem+json`
    - Gleichzeitige Änderung, Wiederholungsversuche erschöpft: `409`, `application/problem+json`
  - `GET /orders/{orderId}`
    - Erfolg: `200 OK`, Body `{ "orderId", "customerId", "total", "status" }`
    - Unbekannte Order: `404`, `application/problem+json`
  - `POST /customers`, `Content-Type: application/json`, Body `{ "email": "...", "creditLimit": 500.00 }`
    - Erfolg: `201 Created`, Header `Location: /customers/{customerId}`, Body `{ "customerId": "..." }`
    - Ungültige Eingabe: `400`, `application/problem+json`
    - E-Mail bereits registriert: `409`, `application/problem+json`
  - `GET /customers/{customerId}`
    - Erfolg: `200 OK`, Body `{ "customerId", "email", "creditLimit", "openAmount" }`
    - Ungültige Id: `400`, `application/problem+json`
    - Unbekannter Customer: `404`, `application/problem+json`
  - `PUT /customers/{customerId}/credit-limit`, Body `{ "creditLimit": 750.00 }`
    - Erfolg: `204 No Content`
    - Ungültige Id oder Eingabe: `400`, `application/problem+json`
    - Unbekannter Customer: `404`, `application/problem+json`
    - Gleichzeitige Änderung, Wiederholungsversuche erschöpft: `409`, `application/problem+json`
- Vertrag & UI zur Laufzeit:
  - `GET /openapi.yaml` – liefert den authored Vertrag unverändert aus.
  - `GET /swagger-ui.html` – Swagger UI (statische Webjar-Assets), lädt `/openapi.yaml`; kein Code-Scanning.

## Datenfluss: Order anlegen
1. HTTP `POST /orders` mit JSON-Body `{ "customerId": ..., "total": ... }` trifft auf `OrderController`.
2. Controller baut aus den Headern `UseCaseMetadata` und ruft `PlaceOrderUseCase`. Das Bean ist ein Proxy, den `UseCasePipelinePostProcessor` um `PlaceOrderService` gelegt hat.
3. `PlaceOrderService` lädt den `Customer` über `CustomerRepository` und ruft `reserveCredit(total)`. Übersteigt der offene Betrag das Limit, bricht der Use Case ab.
4. `PlaceOrderService` erstellt ein `Order`-Aggregate und führt `order.place()` aus.
5. Customer und Order werden in einer Transaktion gespeichert, zuerst der Customer (`CustomerRepository`), dann die Order (`OrderRepository`, jeweils R2DBC-Adapter). Bei einem Versionskonflikt wiederholt die Pipeline den ganzen Use Case (siehe "Transaktionen").
6. Entstandene Domain Events übergibt der Service an den Application-Port `DomainEventPublisherPort`. Sie werden nur gepuffert und erst nach dem Commit der Transaktion tatsächlich publiziert; bei einem Fehler oder Rollback wird nichts publiziert.
7. `DomainEventLoggingListener` verarbeitet die publizierten Events asynchron.

## Use-Case-Audit (Logs & Metriken)
Jeder Use Case erhält explizit `UseCaseMetadata` (`actorType`, `channel`, `correlationId`) aus dem Application-Layer; der Domain-Command bleibt frei davon.
- **Wer**: Header `X-Actor-Type` (`PRIVATE_CUSTOMER`, `BUSINESS_CUSTOMER`, `EXTERNAL_ERP`); fehlt er, wird `UNKNOWN` erfasst. Selbstdeklariert, also nur für Auswertungen, nicht für Autorisierung.
- **Kanal**: `channel` ist `rest` (HTTP-Adapter).
- **Korrelation**: Header `X-Correlation-Id` (optional, sonst generiert, wird in der Antwort zurückgegeben).
- **Metrik**: Timer `usecase.execution` (Prometheus `usecase_execution_seconds_*`) mit Labels `usecase`, `usecase_kind` (`command`/`query`), `actor_type`, `channel`, `outcome` (`SUCCESS`/`EMPTY`/`ERROR`/`CANCELLED`; `EMPTY` = kein Ergebnis bei Query/wertliefernden Use Cases, ein `Mono<Void>`-Use-Case gilt bei Abschluss als `SUCCESS`), `error`; unter `/actuator/prometheus`. Die Correlation-ID ist bewusst kein Label (Kardinalität).
- **Name**: Das Label `usecase` kommt aus `@UseCase.name` (z. B. `order.place`, Konvention `<aggregate>.<verb>`, kleingeschrieben, Segmente per Punkt getrennt, Bindestriche erlaubt, z. B. `customer.change-credit-limit`). Es ist Teil des Metrik-Vertrags und bleibt beim Umbenennen des Interfaces stabil; `usecase_kind` stammt aus `@UseCase.kind`.
- **Audit-Log**: genau eine Zeile pro Ausführung auf Logger `audit`, inkl. Dauer; mit Profil `json-logs` als ECS-JSON mit eigenen Feldern (für Loki).
- **MDC**: `usecase`, `actorType`, `correlationId` stehen in jeder Logzeile innerhalb des Use Cases, auch im asynchronen `DomainEventLoggingListener`.
- Das Wrapping passiert automatisch (`UseCasePipelinePostProcessor`). Beim Start wird erzwungen:
  - `Mono`-Rückgabe und `UseCaseMetadata`-Parameter
  - gültiger, eindeutiger Name
  - genau ein `@UseCase`-Interface pro Bean

```bash
# $CUSTOMER_ID stammt aus dem Block "Customer anlegen" unter "Starten"
curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' -H 'X-Actor-Type: BUSINESS_CUSTOMER' \
  -d "{\"customerId\": \"$CUSTOMER_ID\", \"total\": 12.34}"
curl -s http://localhost:8080/actuator/prometheus | grep usecase_execution
```

### Use Case hinzufügen
1. Interface in `application.<aggregate>.usecase` mit `@UseCase`.
2. Paketinterne Implementierung in `application.<aggregate>.service` mit `@ApplicationComponent`.
3. COMMANDs, die schreiben oder Events publizieren: zusätzlich `@TransactionalUseCase` an die Service-Klasse (siehe "Transaktionen"). Reine Queries brauchen sie nicht.
4. Kein Wiring nötig, Audit, Metrik, Retry und Transaktion greifen automatisch.

```java
@UseCase(name = "order.cancel", kind = UseCase.Kind.COMMAND)
public interface CancelOrderUseCase {
    Mono<Void> handle(CancelOrderCommand cmd, UseCaseMetadata metadata);
}

@TransactionalUseCase
@ApplicationComponent
class CancelOrderService implements CancelOrderUseCase { /* ... */ }
```

Regeln, die ArchUnit prüft:
- Use Cases rufen keine Use Cases auf. Geteilter Code kommt als paketinterne `@ApplicationComponent` ins selbe `service`-Paket. Geteilte Bausteine lesen und rechnen; gespeichert wird nur im Service mit `@TransactionalUseCase`.
- Services sind nicht `public`.
- Klassen, die ein `@UseCase`-Interface implementieren, tragen `@ApplicationComponent`.

## Transaktionen
- **Annotation**: `@TransactionalUseCase` (Modul `dddonion-architecture`, ohne Attribute) steht an der **Service-Klasse**, nicht am Interface. Die Application-Schicht bleibt frei von Spring.
- **Nur für `COMMAND`**: Die Klasse muss ein `@UseCase`-Interface mit `kind = COMMAND` implementieren, sonst bricht der Start ab.
- **Pipeline** (außen nach innen): Observability, Retry, Transaktion, Service. Es gibt eine Observation pro Aufruf, auch bei Wiederholungen.
- **Retry**: nur bei `ConcurrentAggregateUpdateException`, höchstens 3 Versuche, Backoff ab 10 ms bis 200 ms, Jitter 0,5. Jeder Versuch ruft den Service neu auf, in einer neuen Transaktion. Sind die Versuche erschöpft, kommt die ursprüngliche Exception durch (REST: 409).
- **Warum zusammen**: Transaktion und Retry gehören zusammen, weil jeder Versuch seine Aggregates frisch laden muss.
- **Domain Events**: `DomainEventPublisherPort` ist im Boot-Modul als `PostCommitEvents.publisher()` verdrahtet. Der Service ruft ihn wie bisher auf, das Event landet aber in einem Puffer im Reactor-Context. Jeder Versuch hat einen eigenen, neuen Puffer. Nach erfolgreichem Commit (mit oder ohne Ergebnis) publiziert `UseCaseTransaction` die Events in Publish-Reihenfolge über den `ApplicationEventPublisher`, erst danach kommt das Ergebnis beim Aufrufer an. Ein Versuch, der mit Fehler endet (Rollback, auch für den Retry), publiziert nichts. Das Publizieren läuft in derselben Reactor-Kette, daher sieht der asynchrone Listener weiter die `correlationId` im MDC.
- **Nur im `@TransactionalUseCase`**: Außerhalb davon gibt es keinen Puffer, `publish` endet mit `IllegalStateException`; `domain_events_published_only_in_transactional_use_cases` fängt das schon im Build ab.
- Umgesetzt in `UseCaseTransaction` und `PostCommitEvents` (Boot-Modul) mit dem `TransactionalOperator` von Spring Boot.

## Bekannte Grenzen
- **Kein Outbox**: Stürzt der Prozess zwischen Commit und Publizieren ab, geht das Event verloren. Zuverlässige Zustellung bräuchte eine Outbox-Tabelle in derselben Transaktion.
- Events sind nur "at most once": Das Publizieren läuft nach dem Commit. Wirft ein Listener (oder der Executor, z.B. bei voller Queue), wird der Fehler mit dem Event-Typ geloggt und nicht weitergereicht; die übrigen Events werden trotzdem publiziert, und das Ergebnis des Use Cases gilt (kein Fehler für den Aufrufer, kein Retry), weil der Zustand bereits committet ist. Das Event selbst ist dann verloren.

## Technischer Stand
- Spring Boot: `4.0.1`
- Java: `25`
- Maven Wrapper: `3.9.12`

## Starten
```bash
docker-compose up -d
./mvnw -q -DskipTests spring-boot:run -pl dddonion-boot -am

# Customer anlegen (liefert 201 + Location + { "customerId": ... }) und die Id merken
# Die E-Mail ist eindeutig (sonst 409), daher mit Zeitstempel: so ist der Ablauf wiederholbar.
CUSTOMER_ID=$(curl -s -X POST http://localhost:8080/customers \
  -H 'Content-Type: application/json' \
  -d "{\"email\": \"test-$(date +%s)@example.com\", \"creditLimit\": 500.00}" \
  | sed -n 's/.*"customerId":"\([^"]*\)".*/\1/p')
echo "$CUSTOMER_ID"

# Customer lesen
curl http://localhost:8080/customers/$CUSTOMER_ID

# Kreditlimit ändern (liefert 204)
curl -i -X PUT http://localhost:8080/customers/$CUSTOMER_ID/credit-limit \
  -H 'Content-Type: application/json' \
  -d '{"creditLimit": 750.00}'

# Order anlegen (liefert 201 + Location + { "orderId": ... }) und die Id merken
# (422, wenn der Customer unbekannt ist oder das Kreditlimit überschritten würde)
ORDER_ID=$(curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d "{\"customerId\": \"$CUSTOMER_ID\", \"total\": 12.34}" \
  | sed -n 's/.*"orderId":"\([^"]*\)".*/\1/p')

# Order lesen
curl http://localhost:8080/orders/$ORDER_ID

# Vertrag / interaktive Doku
curl http://localhost:8080/openapi.yaml
open http://localhost:8080/swagger-ui.html
```

## Testen
```bash
# Alles inkl. Integrationstests (braucht Docker)
./mvnw -q verify

# Nur Unit-Tests
./mvnw -q test

# Nur Architekturtests
./mvnw -q -pl dddonion-archunit -am test

# Nur Boot-Integrationstests
./mvnw -q -pl dddonion-boot -am verify -Dit.test='*IT' -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false
```

## Hinweise
- Das Monitor-Modul loggt Domain Events absichtlich mit Verzögerung, um Asynchronität sichtbar zu machen.
- Der Bootstrap erstellt die konfigurierte MariaDB-Datenbank (`spring.liquibase.url`) automatisch per `CREATE DATABASE IF NOT EXISTS`, bevor Liquibase ausgeführt wird.
- Mit Lombok auf JDK 25 kann während der Kompilierung eine `sun.misc.Unsafe`-Warnung erscheinen.
- Ungültige Eingaben (z.B. ungültige E-Mail-Adresse bei der Customer-Registrierung oder negativer Betrag) werden mit HTTP 400 Bad Request als `application/problem+json` (RFC 7807) beantwortet.
- Die DB-Tabelle `orders` enthält die Zeitstempel-Spalten `created_at` und `updated_at`, die über `OrderRow` gelesen werden können.
- Das Domain-Modell kennt drei Zustände: `NEW` → `PLACED` (via `order.place()`) → `PAID` (via `order.pay()`). Ein `Customer` hat ein Kreditlimit und einen offenen Betrag; `reserveCredit` lässt Orders bis genau zum Limit zu.
- Die Tabelle `customers` hat eine eindeutige E-Mail (`uq_customers_email`) und `CHECK`-Constraints (Kreditlimit und offener Betrag nicht negativ). Eine bereits registrierte E-Mail ergibt `EmailAlreadyRegisteredException`.
- Beide Adapter nutzen Optimistic Locking mit der Version aus dem Domain-Objekt, nie aus der DB. Ein veralteter Stand führt zu `ConcurrentAggregateUpdateException`.
  - Customer: reines Concurrency-Token, verwaltet über Spring Datas `@Version`.
  - Order: die Version ist zugleich die Event-Sequenz und steigt mit jedem Übergang (`place()` → 1, `pay()` → 2). Der Adapter speichert sie unverändert und aktualisiert nur, wenn die Zeile noch `loadedVersion()` trägt.
  - Beide Adapter bilden einen veralteten Stand auf zwei Wegen auf `ConcurrentAggregateUpdateException` ab: keine passende Zeile (0 geänderte Zeilen) oder MariaDB-Fehler 1020 ("Record has changed since last read"). Letzteren meldet MariaDB je nach Version und Einstellung (`innodb_snapshot_isolation`, ab 11.6.2 standardmäßig an) für ein UPDATE auf eine Zeile, die eine andere Transaktion seit dem Lesen geändert hat. Die Erkennung liegt gemeinsam in `SnapshotConflicts`.
- `created_at`/`updated_at` verwaltet die DB; die Rows markieren sie als `@ReadOnlyProperty`, damit ein Update sie nicht überschreibt.
- Das Schema wird direkt in den bestehenden Changesets gepflegt, ohne Migrationen. Nach einer Schemaänderung muss die lokale Datenbank neu aufgebaut werden (`docker-compose down -v && docker-compose up -d`). Rückwärtskompatibilität ist für das Beispielprojekt kein Ziel.
- Es gibt bewusst keinen Fremdschlüssel zwischen `orders` und `customers` (Aggregate-Grenze).
- `Money` und `MonetaryAmount` (OpenAPI) akzeptieren jede Anzahl Nachkommastellen. Die DB speichert `DECIMAL(19,2)`, beim Persistieren wird also auf 2 Stellen gerundet.
- `Money` vergleicht nach Wert, unabhängig von der Skalierung: `100` ist gleich `100.00` (`equals` und `hashCode`). Der Betrag selbst bleibt so, wie er übergeben wurde.

## License
This project is licensed under the MIT License. See `LICENSE`.
