# PERF_PROGRESS — labflow_api

Registro de optimizaciones de performance del backend. El objetivo es **reducir la
cantidad de round-trips**: menos requests HTTP que atienda la API (cada uno paga el
piso de ~0.7 s navegador → Worker → Durable Object → contenedor, y cada uno es una
invocación de Cloudflare) y menos consultas a Postgres por request (evitar N+1).

Principios que NO se deben deshacer:
- Preferir endpoints por lote y de detalle sobre listados completos.
- Los DTO congelan snapshots (p. ej. rangos de referencia en `TestResult`); no
  cambiar ese comportamiento observable, solo cómo se cargan los datos.
- El scoping por `@TenantId` (laboratory_id) se aplica solo en las consultas; toda
  consulta nueva debe respetarlo (JPQL sobre las entidades lo hereda).

## Hecho

### 2026-08-01 — Batch fetching global: matar el N+1 de `invoice.order` en el listado de facturas y cuentas por cobrar
**Archivo:** `src/main/resources/application.properties`
(`spring.jpa.properties.hibernate.default_batch_fetch_size=50`).
**Problema:** el listado de facturas (`GET /invoices`, pantalla de alto tráfico que el
front pide con `pageSize=500`) y las cuentas por cobrar (`GET /invoices/receivables`)
mapean **cada** factura de la página con `toDTO(i, false)`, que dereferencia
`invoice.getOrder().getOrderNumber()`. `order` es un `@ManyToOne` **lazy** sin batch
fetching, así que al recorrer la página se dispara **una consulta por factura** solo
para inicializar el proxy de la orden — un N+1 clásico del lado de Postgres dentro de un
**único** request (que ya paga el piso de ~0.7 s navegador → Worker → DO → contenedor).
Una página de 500 facturas = **1 consulta de la página + 500 consultas de `order`**
(las colecciones `items` ya venían con `@BatchSize(50)`, no así el `@ManyToOne`). En una
instancia de 1 vCPU esos 500 round-trips seriales a Postgres inflan de forma notable el
tiempo de respuesta de esa pantalla.
**Cambio:** se activa `hibernate.default_batch_fetch_size=50` de forma **global**. Al
inicializar un proxy/colección lazy, Hibernate agrupa hasta 50 pendientes del mismo tipo
en un solo `... WHERE fk IN (?, ?, …)` en vez de una consulta por entidad. Solo cambia
**cómo** se cargan los datos, nunca **qué** datos ni el resultado observable; los DTO,
los endpoints, la paginación y el scoping por `@TenantId` quedan idénticos. Las
colecciones con `@BatchSize(50)` explícito (`Invoice.items`, `Quote.tests`,
`JournalEntry.lines`) conservan su tamaño (la anotación gana sobre el default); este
default cubre los huecos, empezando por el `@ManyToOne` `Invoice.order`.
**Impacto esperado:** el listado de facturas y las cuentas por cobrar bajan de
**1 + N → 1 + ⌈N/50⌉** consultas a Postgres por request (N = facturas en la página). En
una página de 500: de **501 → 11** consultas → **−490 round-trips a Postgres** en un
solo request, sin cambiar el número de llamadas HTTP (mismo 1 request) ni el payload.
Además, cualquier otro `@ManyToOne`/colección lazy sin batch explícito recorrido por
fila queda cubierto por el mismo default (p. ej. remisiones). **No** cambia la cantidad
de invocaciones de Cloudflare (es una mejora intra-request de CPU/BD del contenedor),
pero recorta el tiempo de respuesta de una pantalla de alto tráfico.
**Acople de despliegue:** **ninguno.** Cambio solo de la API (config), sin contrato ni
consumidor en el front. Se despliega de forma independiente.
**Verificación:** `mvn test "-Dtest=!LabflowapiApplicationTests" -Dmaven.compiler.release=21`
→ **52 tests, BUILD SUCCESS** (en este entorno solo hay JDK 21; se compiló con override
`-Dmaven.compiler.release=21`, **sin** tocar el `pom`, que sigue en Java 25). Latencia
real pendiente de confirmación humana (no hay entorno con API + BD para medir).

### 2026-07-29 — Crear orden con sus exámenes en 1 request: `POST /orders` acepta `testIds` (N+1→1) · cross-repo
**Archivos:** `LabOrderDTO` (campo `testIds`, solo escritura), `LabOrderServiceImp`
(`createOrder` + helper `attachTests`).
**Problema:** el alta de una orden desde el front (`ordenes/nueva.vue`, pantalla de
alto tráfico) hacía `POST /orders` y **luego un `POST /orders/{id}/tests` por examen,
en SERIE** (bucle N+1 del lado cliente). Cada request paga el piso de ~0.7 s
(navegador → Worker → Durable Object → contenedor) y es una invocación de Cloudflare.
Una orden de 8 exámenes = **1 + 8 = 9 requests seriales** ≈ **~6.3 s** solo en red
encolada, y 9 invocaciones de Worker/DO/contenedor.
**Cambio:** `LabOrderDTO` acepta un `testIds` **opcional y de solo escritura** (se
ignora al leer y al actualizar). `createOrder` crea la orden y, en la **misma
transacción**, arma los `LabTest` de cada examen y los persiste por `cascade` al
guardar la orden. Se replica exactamente el alta por examen: `testConfig=null`, sin
notas ni tipo de muestra, y **en el mismo orden** recibido, así el detalle muestra
los mismos exámenes en la misma secuencia. Si un `testId` no existe se lanza la misma
`ResourceNotFoundException("Test","testId",…)` que el alta individual y **toda la
creación se revierte** (atómica). El endpoint por examen `POST /orders/{id}/tests` se
conserva intacto para agregar un examen a una orden ya existente (lo usa el detalle).
**Impacto esperado:** el alta de orden baja de **N+1 → 1** request. En una orden de 8
exámenes: de 9 a 1 llamada → **−8 requests ≈ −8 invocaciones de Cloudflare** y hasta
**~5.6 s** menos de red serial por alta. Del lado de Postgres no cambia la cantidad de
`INSERT` (1 orden + N exámenes), pero pasan a **una sola transacción** en vez de N+1
transacciones/round-trips HTTP separados.
**Acople de despliegue:** **API primero.** El consumidor
(`labflow_frontend`, rama `claude/inspiring-ramanujan-knfnzz`,
`ordenes/nueva.vue`) envía `testIds` en el cuerpo de `POST /orders`. Desplegar la API
**antes** que el front; si el front sale primero, el API viejo ignoraría `testIds` y
la orden se crearía **sin exámenes** (regresión funcional). Ver PR del front.
**Verificación:** `mvn test "-Dtest=!LabflowapiApplicationTests"` → 52 tests, BUILD
SUCCESS (en este entorno solo hay JDK 21; se compiló/testeó con override
`-Dmaven.compiler.release=21`, **sin** tocar el pom, que sigue en Java 25). Latencia
real pendiente de confirmación humana (no hay entorno con API + BD para medir).

### 2026-07-28 — Sexo/edad del paciente embebidos en `LabOrderDTO` (habilita 2→1 serial en el front) · cross-repo
**Archivos:** `LabOrderDTO` (`customerSex`, `customerAgeInDays`), `LabOrderServiceImp.toDTO`.
**Problema:** el detalle de la orden en el front pedía `GET /customers/{id}` **en
serie** tras `GET /orders/{id}` solo para conocer `sex`/`ageInDays` del paciente
(necesarios para elegir los rangos de referencia aplicables). Ese round-trip serial
paga íntegro el piso de ~0.7 s y es otra invocación Worker→DO→contenedor.
**Cambio:** `LabOrderDTO` ahora expone `customerSex` (enum `Sex`) y
`customerAgeInDays` (`Integer`), embebidos de **solo lectura** igual que
`customerName`: se ignoran al crear/actualizar (la orden se vincula por `customerId`)
y se **leen del mismo `Customer` que `toDTO` ya cargaba** para el nombre —**sin
consulta extra** a Postgres. En el listado el paciente ya venía por
`LEFT JOIN FETCH o.customer` (una sola query, sin N+1); en el detalle
(`findById` → `getOrderById`) es el mismo proxy ya accedido para `getName()`. El
resto del DTO y el comportamiento observable no cambian.
**Impacto esperado:** habilita que el detalle de la orden en el front elimine la
llamada serial a `GET /customers/{id}` (**2 llamadas seriales → 1**): **−1
invocación de Cloudflare** por apertura y ~**0.7 s** menos en la ruta crítica del
montaje (el request serial no se solapaba con nada). Del lado de Postgres: **0
queries nuevas** (mismos campos del `Customer` ya materializado).
**Acople de despliegue:** **API primero.** El consumidor
(`labflow_frontend`, rama `claude/inspiring-ramanujan-dv7ly5`) usa
`customerSex`/`customerAgeInDays`; debe desplegarse **después** de esta API. El front
mantiene fallback a `/customers/{id}` mientras la API vieja no los envíe.
**Verificación:** `mvn test -Dtest=!LabflowapiApplicationTests` → 52 tests, BUILD
SUCCESS. (En este entorno solo hay JDK 21; se compiló con `-Dmaven.compiler.release=21`
—el cambio es solo agregar dos campos/setters, 100% compatible con el target Java 25
del `pom`.) Latencia real pendiente de confirmación humana.

### 2026-07-26 — Endpoint por lote de corridas: `GET /orders/{orderId}/runs`
**Archivos:** `TestRunRepository`, `TestRunService(+Imp)`, `LabOrderController`.
**Problema:** el detalle y la impresión de una orden pedían las corridas de
resultados examen por examen (`GET /tests/{testId}/runs`, una llamada HTTP por
examen). Una orden con N exámenes disparaba N requests — N invocaciones de
Worker/Durable Object/contenedor — cada una pagando el piso de ~0.7 s. Además, el
mapeo a DTO recorría la colección lazy `results` de cada corrida, con riesgo de N+1
de consultas a Postgres por corrida.
**Cambio:** nuevo endpoint `GET /api/v1/orders/{orderId}/runs` que devuelve las
corridas de **todos** los exámenes de la orden en una sola llamada. La consulta usa
`LEFT JOIN FETCH r.results` + `LEFT JOIN FETCH res.parameter`, ordenada por
`(test.id, runNumber)`, así trae corridas + resultados + parámetro en **una sola
consulta** (sin N+1 de la colección). Cada `TestRunDTO` sigue llevando su `testId`,
así que el cliente agrupa por examen y obtiene exactamente los mismos datos y el
mismo orden que el endpoint por examen. Mismos permisos
(`ORDERS_VIEW`/`ORDERS_PRINT`/`ORDERS_ENTER_RESULTS`) y mismo scoping por tenant.
El endpoint por examen se conserva (lo usa el flujo de ingreso de resultados).
**Impacto esperado:** el detalle y la impresión de una orden bajan de **N → 1**
llamada para traer corridas (N = cantidad de exámenes de la orden). En una orden de
8 exámenes: **−7 requests** ≈ **−7 invocaciones de Cloudflare** y, con el piso de
~0.7 s, hasta **~4.9 s** menos de red encolada en el navegador (que limita la
concurrencia a ~6 conexiones). Del lado de Postgres, pasa de "1 query de corridas +
1 query de resultados por corrida, por examen" a **1 sola consulta** para toda la
orden.
**Acople de despliegue:** **API primero.** El consumidor en el front
(`labflow_frontend`, rama `claude/inspiring-ramanujan-6atyru`) usa este endpoint;
debe desplegarse **después** de que este endpoint esté disponible. El front
mantiene el fallback funcional solo si el endpoint existe; ver PR del front.
**Verificación:** `mvn test -Dtest=!LabflowapiApplicationTests` → 52 tests, BUILD
SUCCESS (JDK 25). Latencia real pendiente de confirmación humana (no hay entorno con
API + BD para medir).

### 2026-07-27 — `customerName` embebido en el DTO de la orden (listado sin 2.ª llamada) · cross-repo
**Archivos:** `LabOrderDTO`, `LabOrderRepository`, `LabOrderServiceImp`.
**Problema:** el listado de órdenes del front (pantalla de alto tráfico) bajaba
`GET /customers?pageSize=1000` **en paralelo** a `GET /orders` solo para resolver el
nombre del paciente por fila. Era una 2.ª invocación de Worker/Durable Object/
contenedor (piso de ~0.7 s) y una consulta a Postgres que trae **todos** los
clientes del laboratorio aunque la página muestre 15. Además, el mapeo del listado
llamaba `order.getCustomer()` sobre un `@ManyToOne` sin `JOIN FETCH`, con riesgo de
N+1 al inicializar el paciente de cada orden.
**Cambio:** `LabOrderDTO` ahora incluye `customerName` (solo lectura; se ignora al
crear/actualizar, la orden se sigue vinculando por `customerId`). `toDTO` lo puebla
con `order.getCustomer().getName()`. Para el listado se agrega
`findByStatusNotFetchCustomer` con `LEFT JOIN FETCH o.customer`, que trae órdenes +
paciente en **una sola consulta**; al ser un `@ManyToOne` (fetch to-one, no multiplica
filas) la paginación por SQL sigue siendo correcta y respeta el `@TenantId` (JPQL
sobre la entidad lo hereda). Mismos datos y mismo orden observables.
**Impacto esperado:** el listado de órdenes baja de **2 → 1** llamada al API por
apertura (**−1 request** ≈ **−1 invocación de Cloudflare**), y se elimina una consulta
`SELECT * FROM customers` completa (potencialmente cientos/miles de filas) por otra
que ya venía embebida en el fetch join. Del lado de Postgres el listado pasa de
"1 query de órdenes + 1 query de todos los clientes (+ posible N+1 del paciente)" a
**1 sola consulta** con join.
**Acople de despliegue:** **API primero.** El front
(`labflow_frontend`, rama `claude/inspiring-ramanujan-t5vhv9`) deja de pedir
`/customers` y lee `customerName` del DTO; tiene fallback `Paciente #{id}` si el
nombre no viene, así que no rompe si el front sale antes, pero para ver los nombres
la API debe desplegarse **primero**.
**Verificación:** `mvn test -Dtest=!LabflowapiApplicationTests -Djava.version=21` →
52 tests, BUILD SUCCESS. (El pom apunta a Java 25; este entorno solo tiene JDK 21, así
que se compiló con release 21 vía override de línea de comandos —sin tocar el pom—; el
cambio es compatible con ambas versiones.) Latencia real pendiente de confirmación
humana (no hay entorno con API + BD para medir).

## Inventario de oportunidades (pendientes, una por corrida futura)

- [x] **`customerName` embebido en la página de órdenes** (hecho 2026-07-27, ver arriba).
- [x] **Datos mínimos del paciente en el DTO de la orden:** hecho 2026-07-28 —
  `LabOrderDTO` embebe `customerSex`/`customerAgeInDays` (sin query extra) y el front
  eliminó la llamada serial a `GET /customers/{id}` en el detalle (2→1 serial). Ver
  entrada en «Hecho».
- [ ] **Revisar N+1 en otros mapeos a DTO** (facturas, remisiones, journal): mismas
  colecciones lazy recorridas en `toDTO`; auditar con logging de Hibernate.
- [~] **Config HikariCP/JPA:** `default_batch_fetch_size=50` activado 2026-08-01 (ver
  «Hecho»: mata el N+1 de `Invoice.order` en el listado de facturas y cuentas por
  cobrar). Pendiente aún: revisar `spring.jpa.open-in-view` (hoy default `true`; para
  apagarlo hay que anotar `@Transactional(readOnly=true)` los métodos que mapean a DTO
  fuera de transacción, p. ej. `getAllInvoices`) y el tamaño del pool de HikariCP para
  la instancia única de 1 vCPU.
