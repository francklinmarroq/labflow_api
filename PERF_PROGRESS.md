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

## Inventario de oportunidades (pendientes, una por corrida futura)

- [ ] **`customerName` embebido en la página de órdenes:** el listado del front baja
  `/customers?pageSize=1000` solo para resolver nombres. Si `LabOrderResponse`
  incluyera el nombre del cliente por fila se ahorraría esa 2.ª llamada. Cross-repo.
- [ ] **Datos mínimos del paciente en el DTO de la orden:** el detalle pide
  `GET /customers/{id}` en serie tras `GET /orders/{id}` (necesita sexo/edad para
  los rangos). Embeber sexo/edad en `LabOrderDTO` ahorraría 1 round-trip serial.
  Evaluar contra el costo de agrandar el DTO. Cross-repo.
- [ ] **Revisar N+1 en otros mapeos a DTO** (facturas, remisiones, journal): mismas
  colecciones lazy recorridas en `toDTO`; auditar con logging de Hibernate.
- [ ] **Config HikariCP/JPA:** revisar `spring.jpa.open-in-view`, tamaño del pool y
  `default_batch_fetch_size`/`@BatchSize` para instancia única de 1 vCPU.
