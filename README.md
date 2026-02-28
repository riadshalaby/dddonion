# dddonion - Reactive DDD + Onion + Hexagonal example (Spring Boot 4, WebFlux, R2DBC, Liquibase)

Dieses Projekt demonstriert Domain-Driven Design (DDD), Onion Architecture und Hexagonal Architecture (Ports & Adapters) in einem reaktiven Spring-Stack.

## Stack
- Web: Spring WebFlux
- Persistenz: R2DBC mit MariaDB
- Migrationen: Liquibase (formatted SQL)
- Ops: Actuator Readiness/Liveness, ArchUnit
- Monitoring: separates Modul `dddonion-monitor` mit asynchronem Domain-Event-Logging
- Tests: JUnit 6/Jupiter, Reactor Test, RestAssured, Testcontainers (MariaDB)

## Module
```text
dddonion/
|- dddonion-domain
|  |- domain/common/DomainEvent
|  |- domain/order/model/Order, OrderId, OrderStatus
|  |- domain/order/command/PlaceOrderCommand
|  |- domain/order/event/OrderPlaced
|  \- domain/order/repository/OrderRepository (Port)
|- dddonion-application
|  |- application/event/DomainEventPublisherPort
|  |- application/order/usecase/PlaceOrderUseCase
|  \- application/order/service/PlaceOrderService
|- dddonion-adapter-webflux
|  \- infrastructure/web/OrderController
|- dddonion-adapter-r2dbc
|  \- infrastructure/persistence/r2dbc/... (Adapter + Repo + Mapper + DTO)
|- dddonion-monitor
|  \- monitor/DomainEventLoggingListener + MonitorConfig
|- dddonion-boot
|  \- bootstrap/DddOnionApplication + BeansConfig
\- dddonion-archunit
   \- Architekturregeln (Layer, Abhaengigkeiten, Adapter-Isolation)
```

## Architekturzuordnung
- Domain: enthaelt Aggregate, Value Objects, Domain Events und Domain-Ports; keine Abhaengigkeit auf Spring/Infrastruktur.
- Application: orchestriert Use Cases, ruft Domain und Ports auf.
- Adapter (Infrastruktur): implementieren Ports und binden Frameworks/IO (HTTP, DB).
- Boot: Composition Root (Bean-Wiring + Startkonfiguration).
- Monitor: asynchrone Beobachtung von Domain Events via Spring Events.

## Datenfluss: Order anlegen
1. HTTP `POST /orders?email=...&total=...` trifft auf `OrderController`.
2. Controller ruft `PlaceOrderUseCase`.
3. `PlaceOrderService` erstellt ein `Order`-Aggregate und fuehrt `order.place()` aus.
4. Repository-Port `OrderRepository` persistiert das Aggregate via R2DBC-Adapter.
5. Entstandene Domain Events werden nach erfolgreichem Persistieren ueber den Application-Port `DomainEventPublisherPort` publiziert.
6. `DomainEventLoggingListener` verarbeitet diese Events asynchron.

## Technischer Stand
- Spring Boot: `4.0.1`
- Java: `25`
- Maven Wrapper: `3.9.12`

## Starten
```bash
docker-compose up -d
./mvnw -q -DskipTests spring-boot:run -pl dddonion-boot -am
curl -X POST "http://localhost:8080/orders?email=test@example.com&total=12.34"
```

## Testen
```bash
# Alle Tests
./mvnw -q test

# Nur Architekturtests
./mvnw -q -pl dddonion-archunit -am test

# Nur Boot-Integrationstests
./mvnw -q -pl dddonion-boot -Dtest='*IT' test
```

## Hinweise
- Das Monitor-Modul loggt Domain Events absichtlich mit Verzoegerung, um Asynchronitaet sichtbar zu machen.
- Der Bootstrap erstellt die konfigurierte MariaDB-Datenbank (`spring.liquibase.url`) automatisch per `CREATE DATABASE IF NOT EXISTS`, bevor Liquibase ausgeführt wird.
- Mit Lombok auf JDK 25 kann waehrend der Kompilierung eine `sun.misc.Unsafe`-Warnung erscheinen.
- Ungültige Eingaben (z.B. ungültige E-Mail-Adresse oder negativer Betrag) werden mit HTTP 400 Bad Request beantwortet.
- Die DB-Tabelle `orders` enthält die Zeitstempel-Spalten `created_at` und `updated_at`, die über `OrderRow` gelesen werden können.
- Das Domain-Modell kennt drei Zustände: `NEW` → `PLACED` (via `order.place()`) → `PAID` (via `order.pay()`).

## License
This project is licensed under the MIT License. See `LICENSE`.
