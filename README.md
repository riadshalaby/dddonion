# dddonion — Reactive DDD + Onion + Hexagonal example (Spring Boot 4, WebFlux, R2DBC, Reactor Kafka, Liquibase)

Dieses Projekt demonstriert **Domain‑Driven Design (DDD)**, **Onion Architecture** und **Hexagonal Architecture (Ports & Adapters)** in einem reaktiven Spring‑Stack:
- **Web**: Spring **WebFlux**
- **Persistence**: **R2DBC** mit **MariaDB**
- **Messaging**: **Reactor Kafka** mit **Transactional Outbox (Polling)**
- **Migrations**: **Liquibase** (formatted SQL, im selben Prozess)
- **Ops**: **Actuator Readiness/Liveness**, Cleanup‑Cronjob, **ArchUnit**‑Regeln
- **Monitoring**: Separates Modul `dddonion-monitor` mit asynchronem Domain‑Event‑Logging
- **Tests**: JUnit 6 / Jupiter, Reactor Test, RestAssured, **Testcontainers** (MariaDB, Kafka)

## Module & Layer
```
dddonion/                      # Parent (groupId: net.rsworld.example.dddonion)
├─ dddonion-domain             # Domain Layer (reine Domäne)
│  └─ net.rsworld.example.dddonion.domain..
│     ├─ common/DomainEvent
│     ├─ order/model/Order, OrderId, OrderStatus
│     ├─ order/command/PlaceOrderCommand
│     ├─ order/event/OrderPlaced
│     └─ order/repository/OrderRepository (Port)
├─ dddonion-application        # Application Layer (Use Cases)
│  └─ net.rsworld.example.dddonion.application..
│     ├─ order/usecase/PlaceOrderUseCase
│     ├─ order/service/PlaceOrderService (UseCase-Implementierung)
│     └─ outbox/OutboxPort (Port)
├─ dddonion-adapters-webflux   # Inbound Adapter (HTTP)
│  └─ infrastructure/web/OrderController (REST API)
├─ dddonion-adapters-r2dbc     # Outbound Adapter (DB)
│  └─ infrastructure/persistence/r2dbc/..
│     ├─ dto/OrderRow
│     ├─ repo/OrderR2dbcRepository (Spring Data)
│     ├─ mapper/OrderMapper
│     └─ adapter/OrderRepositoryAdapter (implements Domain-Port)
├─ dddonion-outbox-reactive    # Outbound Adapter (Kafka Outbox)
│  └─ infrastructure/outbox/..
│     ├─ OutboxAdapter (implements Application-Port OutboxPort)
│     ├─ OutboxRepository (R2DBC Outbox-Table)
│     ├─ StoredEvent (Table-Mapping)
│     ├─ OutboxPublisher (Reactor Kafka Poller, @ConditionalOnProperty)
│     ├─ OutboxCleanupJob (@Scheduled)
│     └─ OutboxPublisherHealthIndicator (Readiness)
├─ dddonion-boot               # Bootstrap/Composition Root
│  └─ net.rsworld.example.dddonion.bootstrap..
│     ├─ DddOnionApplication (@SpringBootApplication, @EnableScheduling)
│     └─ BeansConfig (Wiring Domain/Application/Adapter)
├─ dddonion-monitor            # Monitoring (asynchrones Domain-Event-Logging via Spring-Events)
│  └─ net.rsworld.example.dddonion.monitor..
│     ├─ DomainEventLoggingListener (@Async("applicationTaskExecutor"), @EventListener)
│     └─ MonitorConfig / Executor (im Monitor-Modul als Bean "applicationTaskExecutor")
└─ dddonion-archunit           # Architekturtests (ArchUnit)
   └─ ..arch/ArchitectureTest (Regeln für Layers/Adapter/Ports)
```

## Technischer Stand
- Spring Boot: `4.0.1`
- Java: `25` (Maven Compiler `release=25`)
- Maven Wrapper: `3.9.12`

### Onion + Hexagon in diesem Projekt
- **Domäne (Kern)**: keine Abhängigkeiten auf Spring, Application oder Infrastruktur.  
  Beispiele: `Order`, `OrderId`, `OrderStatus`, `OrderPlaced`, `DomainEvent`, `OrderRepository`, `PlaceOrderCommand`.
- **Application (Ringe um die Domäne)**: Orchestriert Use‑Cases, kennt Ports für externe Systeme.  
  Beispiele: `PlaceOrderUseCase`, `PlaceOrderService`, `OutboxPort`.
- **Infrastruktur (Adapter, äußerer Ring)**: Implementiert Ports (DB/Kafka/HTTP), Framework‑Code.  
  Beispiele: `OrderRepositoryAdapter`, `OrderR2dbcRepository`, `OrderController`, `OutboxAdapter`, `OutboxRepository`, `OutboxPublisher`.
- **Bootstrap**: Startklasse, Konfiguration, Bean‑Wiring, Readiness/Liveness.
- **Monitor**: Querschnitt (Cross‑Cutting) für Event‑Beobachtung, separat gehalten.

> **Ports & Adapters (Hexagonal)**:
> - Outbound Ports: `OrderRepository` (Domain), `OutboxPort` (Application).
> - Outbound Adapters: `OrderRepositoryAdapter` (R2DBC), `OutboxAdapter` (Outbox‑Table).
> - Inbound Adapter: `OrderController` (HTTP).

## Welche Klassen gehören wohin?
### Domain (dddonion-domain)
- **Aggregate/Entity**: `Order` (enthält Invarianten, erzeugt Domain‑Events)
- **Value Object**: `OrderId`, ggf. `Money`, `Email` (als einfache Typen angedeutet)
- **Command**: `PlaceOrderCommand` (Validierung von Email/Total)
- **Domain Event**: `OrderPlaced` (trägt `sequence` = Version)
- **Repository‑Port**: `OrderRepository`

### Application (dddonion-application)
- **Use Case**: `PlaceOrderService` implements `PlaceOrderUseCase`
- **Outbound Port**: `OutboxPort` (persistiert Ereignisse)

### Infrastruktur (Adapters)
- **DB (R2DBC)**: `OrderRow` (DTO), `OrderR2dbcRepository`, `OrderMapper`, `OrderRepositoryAdapter`
- **HTTP (WebFlux)**: `OrderController`
- **Outbox/Kafka**: `OutboxRepository`, `OutboxAdapter`, `OutboxPublisher`, `OutboxCleanupJob`, `StoredEvent`

### Monitor (dddonion-monitor)
- **Listener**: `DomainEventLoggingListener` – hört auf `ApplicationEvent`s, die DomainEvents transportieren (z.B. direkt `DomainEvent` oder einen Wrapper wie `DomainEventPublished`), und loggt/aggregiert **asynchron**. Annotationen: `@Component`, `@Async("applicationTaskExecutor")`, `@EventListener`.
- **Konfiguration**: `MonitorConfig` stellt den Executor `applicationTaskExecutor` direkt bereit (Virtual Threads) und aktiviert Async-Verarbeitung via `@EnableAsync`.
- **Abhängigkeiten**: Keine Abhängigkeiten auf Adapter-Module (Web, R2DBC, Outbox). Darf die **Domain** (z.B. `DomainEvent`) und **Spring** referenzieren. ArchUnit regelt, dass Monitor nicht auf andere Adapter zugreift.
- **Zweck**: Cross‑Cutting Observability (Logging, Metriken, Tracing) ohne die Domäne oder Use‑Cases zu verunreinigen.


### Bootstrap (dddonion-boot)
- `DddOnionApplication`, `BeansConfig`, ggf. weitere Bootstrapping‑Konfiguration

## DDD — Domain‑Driven Design
- **Ubiquitous Language**: Domänenbegriffe in Code (`Order`, `OrderPlaced`, `PlaceOrderCommand`).
- **Aggregate & Invarianten**: `Order.place()` setzt Status, erhöht `version` und damit die Event‑Sequenz.  
  `PlaceOrderCommand` validiert Eingaben (Email gültig, Total ≥ 0).
- **Domain Events**: werden transaktional in der Outbox gespeichert (gleiches Commit wie Aggregate). Sowie an interessierte Listener weitergegeben.
- **Strikte Ordnung**: Kafka‑Key = `aggregateId`, Producer idempotent.


## Beispiele: neue Funktion einordnen
- **Neuer Use Case „PayOrder“**  
  - Domain: ggf. Event `OrderPaid`, Methode `order.pay()` mit Invarianten.  
  - Application: `PayOrderUseCase` + `PayOrderService`.  
  - Infrastruktur: Mapping/Repo bleibt (`OrderRepositoryAdapter`), Controller‑Endpoint in `OrderController`.  
  - Outbox: `OutboxAdapter` speichert `OrderPaid` automatisch (wie `OrderPlaced`).

- **Reine Query (keine Domänenänderung)**  
  - Application: Query‑UseCase + Query‑DTO im Application‑Layer (`application.order.query.*`).  
  - Infrastruktur: ggf. **separater** Lesemodell‑Adapter (eigenes Repo/SQL/Projection), kein Eingriff in Domäne.

## Outbox Pattern — Erklärung und Implementierung

### Was ist das Outbox Pattern?
Das **Outbox Pattern** sorgt dafür, dass Änderungen an der Domäne und das Veröffentlichen passender Nachrichten (Events) an andere Systeme **atomar** und **zuverlässig** erfolgen:
- Domänenänderung und Event‑Persistenz passieren in derselben Datenbank‑Transaktion (gleicher Commit).
- Ein separater Publisher liest die gespeicherten Events später aus der Outbox‑Tabelle und veröffentlicht sie (z.B. nach Kafka).
- Dadurch wird das klassische „Dual Write“-Problem (DB + Message-Broker) vermieden.

Kerneigenschaften:
- Events werden mit einer **Sequenz** pro Aggregat gespeichert (Reihenfolge garantiert).
- Ein **Publisher** liest ungepublizierte Events geordnet und veröffentlicht sie, anschließend werden sie als „published“ markiert.
- Ein **Cleanup-Job** entfernt alte, bereits veröffentlichte Events.

### Wie ist es hier umgesetzt?
- Die Domäne erzeugt Events beim Zustandswechsel (z.B. `Order.place()` erzeugt `OrderPlaced` mit `sequence = version + 1`).
- Der Application‑Service persistiert zuerst das Aggregate und schreibt anschließend alle entstandenen Events in die Outbox:
    - In `PlaceOrderService` werden die Events mittels `concatMap` nacheinander über den **Application‑Port** `OutboxPort` gespeichert (Reihenfolge bleibt erhalten).
- Der Outbox‑Port wird vom Infrastruktur‑Adapter implementiert:
    - `OutboxAdapter` serialisiert das `DomainEvent` nach JSON und ruft `OutboxRepository.save(...)` auf.
    - `OutboxRepository` speichert in die Tabelle `outbox_events` mit Spalten wie `aggregate_id`, `sequence`, `type`, `occurred_at`, `payload_json`, `published=false`.
    - Zeitstempel werden für MariaDB R2DBC als `LocalDateTime` in UTC gespeichert.
- Ein Publisher (Poller) liest periodisch unveröffentlichte Events:
    - `OutboxRepository.findBatch(limit)` liefert ungepublizierte Events sortiert nach `(aggregate_id, sequence)`.
    - Nach erfolgreichem Publish markiert `markPublished(id)` das Event als veröffentlicht.
    - Ein Cleanup löscht veröffentlichte Events älter als ein konfiguriertes Datum (`deletePublishedOlderThan(cutoff)`).

### Vorteile dieser Umsetzung
- **Zuverlässigkeit**: Keine verlorenen Events trotz Fehlern zwischen DB‑Commit und Broker‑Publish.
- **Reihenfolge**: Sequenz pro Aggregat und sequentielle Speicherung (`concatMap`) sichern die korrekte Event-Reihenfolge.
- **Transparenz**: Outbox‑Tabelle dient als Audit/Retry‑Quelle.
- **Entkopplung**: Domain kennt keine Broker‑APIs; Kommunikation läuft über Ports/Adapter.

### Datenfluss (vereinfacht)
1. Application: `PlaceOrderService.handle(cmd)`
2. Domain: `order.place()` erzeugt `OrderPlaced`
3. Persistenz: `Order` speichern (Transaktion)
4. Outbox: alle `order.pullEvents()` via `OutboxPort.save(...)` in `outbox_events` speichern
5. Publisher: liest ungepublizierte Events → veröffentlicht → `markPublished`
6. Cleanup: löscht alte, veröffentlichte Events

## Maven Dependencies

### Neuere Versionen der benutzten Dependencies anzeigen
```bash
./mvnw \
  -DprocessDependencyManagement=false \
  -DprocessPluginDependenciesInPluginManagement=false \
  -DshowVersionless=false \
  versions:display-dependency-updates
```

### Plugin Dependency Update anzeigen
```bash
./mvnw versions:display-plugin-updates
```

## Starten & Testen
```bash
docker-compose up -d
./mvnw -q -DskipTests spring-boot:run -pl dddonion-boot -am
curl -X POST "http://localhost:8080/orders?email=test@example.com&total=12.34"
```

Tests:
```bash
# Alle Unit-/IT-/Architekturtests
./mvnw -q test

# Nur Architekturtests
./mvnw -q -pl dddonion-archunit -am test

# Boot-ITs (inkl. Event-Logging-IT)
./mvnw -q -pl dddonion-boot -Dtest='*IT' test
```
Hinweise:
- Jackson serialisiert Java‑Zeittypen als ISO‑8601 (keine Timestamps) für die Outbox.
- Das Monitor‑Modul verzögert das Loggen demonstrativ um 2 Sekunden, um Asynchronität sichtbar zu machen.
- Mit Lombok auf JDK 25 kann beim Kompilieren eine `sun.misc.Unsafe`-Warnung erscheinen; sie ist aktuell erwartbar und nicht build-blockierend.

## License
This project is licensed under the MIT License. See `LICENSE`.
