# PERF_PROGRESS — labflow_api

Registro de optimizaciones de performance del backend. El objetivo es **reducir la
cantidad de round-trips** que el front necesita contra la API (cada request tiene un
piso de ~0.7 s aun en caliente: navegador → Worker → Durable Object → contenedor de
1 vCPU) y **reducir las consultas a Postgres por request** (N+1, fetch joins,
paginación). Menos requests al API = menos invocaciones de Worker/DO/contenedor =
menos consumo de Cloudflare.

Principios que NO se deben deshacer:
- El multitenancy va por `@TenantId` (`laboratory_id`); Hibernate filtra solo. Las
  consultas nuevas heredan ese filtro; no lo puentées salvo los casos ya existentes
  del enlace público / invitaciones (SQL nativo explícito y documentado).
- Preferir endpoints por lote y de detalle sobre bajar listados completos.
- `/api/v1/health` y el cron de warm-up del contenedor no se tocan salvo mejora clara.

## Hecho

### 2026-07-25 — `GET /orders/{id}/runs`: todas las corridas de la orden en 1 request · cross-repo
**Archivos:** `controller/v1/LabOrderController.java`, `service/TestRunService.java`,
`service/TestRunServiceImp.java`, `repositories/TestRunRepository.java`,
`test/.../PostgresQueryCompatibilityTest.java`.
**Acople de despliegue:** consumido por el front (labflow_frontend) en el detalle de
orden. **Desplegar la API primero**; el front que lo usa se despliega después. El
endpoint es aditivo (no cambia ni quita nada existente), así que desplegarlo antes es
seguro aunque el front viejo todavía no lo llame.
**Problema:** el detalle de orden pedía `GET /tests/{id}/runs` **una vez por examen**
(N requests). Cada request es una invocación Worker→DO→contenedor (~0.7 s de piso) y
al menos una consulta a Postgres.
**Cambio:** endpoint nuevo `GET /api/v1/orders/{orderId}/runs` que devuelve todas las
corridas de la orden en una sola llamada. La consulta usa `DISTINCT ... JOIN FETCH
r.test LEFT JOIN FETCH r.results LEFT JOIN FETCH res.parameter` para traer examen,
resultados y parámetro de una vez y **no reintroducir un N+1** al mapear los DTO.
Devuelve exactamente los mismos `TestRunDTO` que `getRunsByTest` (cada uno lleva su
`testId`), ordenados por examen y número de corrida; el front los reagrupa por examen.
Mismos permisos que el endpoint por examen (`ORDERS_VIEW/PRINT/ENTER_RESULTS`) y 404
si la orden no existe.
**Impacto esperado:** el detalle de orden baja de **N a 1** request de corridas
(N = exámenes de la orden) y de N consultas (más sus resultados) a **1 consulta con
fetch joins**. Una orden con 8 exámenes: de 8 requests a 1 (**7 invocaciones
Cloudflare menos**) y de ~8+ consultas a 1. Sin cambio de comportamiento observable.
**Verificación:** `mvn test "-Dtest=!LabflowapiApplicationTests"` → BUILD SUCCESS
(52 tests, 0 fallos). Se agregó a `PostgresQueryCompatibilityTest` un smoke test del
nuevo JPQL con fetch joins (se ejecuta solo si hay Postgres real en localhost:55432;
sin él se salta, como el resto de esa clase). Latencia real pendiente de confirmación
humana (no hay entorno con API + BD para medir).

## Inventario de oportunidades (pendientes, una por corrida futura)

- [ ] **N+1 al listar corridas por examen**: `getRunsByTest` no hace fetch join de
  `results`/`parameter`; para un solo examen es 1 + M consultas. Bajo impacto (se usa
  puntualmente), pero se podría reusar el mismo patrón de fetch join del endpoint por
  orden.
- [ ] **DTO de orden con datos mínimos del paciente**: el front pide `GET
  /customers/{id}` después de `GET /orders/{id}` solo para sexo/edad (rangos) y
  nombre. Si el `LabOrderDTO` embebiera esos campos se ahorraría 1 round-trip serial
  en el detalle de orden (cross-repo; evaluar contra agrandar el DTO).
- [ ] **`customerName` en la página de órdenes**: el listado de órdenes del front baja
  `/customers?pageSize=1000` solo para resolver nombres. Embeber `customerName` en la
  página de órdenes ahorraría 1 request en esa pantalla (cross-repo).
- [ ] **Config HikariCP / JPA**: revisar tamaño de pool y `open-in-view` (instancia
  única, 1 vCPU) para no retener conexiones de más.
