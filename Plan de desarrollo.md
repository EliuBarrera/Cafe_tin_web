# Plan de desarrollo por fases — Gestión Cafetín

Oct 19, 2026 · @Alejandro

## Resumen

El desarrollo se organiza en ocho fases (0 a 7) que encajan en el cronograma: preparación y esqueleto de la API durante Diseño (19/10 – 06/11), construcción del 09/11 al 04/12, pruebas desde el 23/11 y despliegue del 07/12 al 18/12.

El orden acordado (API → app Android → panel) no cabe en cuatro semanas de desarrollo si se hace en serie. La propuesta es congelar el contrato de la API (OpenAPI) al cierre de la Fase 1 y, desde la Fase 2, avanzar en tres pistas paralelas: API, app y panel. Es una decisión del equipo.

&#91;embedded content: Fases del plan · 19 oct – 18 dic 2026\]

Desde el 9 nov, API, app y panel avanzan en paralelo sobre el contrato; las pruebas corren junto a las Fases 4 y 5 desde el 23 nov.

### Decisiones que hay que cerrar antes de la Fase 1

| Decisión | Recomendación | Por qué |
| --- | --- | --- |
| Infraestructura (**cerrada 02/11**) | Neon (base de datos, una rama por persona), Render (API en Docker), Vercel (panel) | Neon es PostgreSQL real (triggers, enums, vistas) con ramas para trabajar sin pisarse; Vercel no ejecuta Java, por eso la API va en Render. |
| Panel web: React 18 o Thymeleaf (**cerrada 02/11: React**) | React + TypeScript con Vite, en Vercel | El argumento del stack es "una API REST para dos clientes"; Thymeleaf renderiza en el servidor, no consume la API y obliga a una segunda configuración de seguridad (sesión además de JWT). Además hay experiencia previa en el equipo con React y TypeScript. |
| Pago (RF-06): pasarela real o simulada | Simulada, detrás de una interfaz `PasarelaPago` | Una pasarela real exige cuenta comercial y no aporta a lo que evalúa el curso; la interfaz permite cambiarla después sin tocar el resto. |
| Token de notificaciones (RF-08) | Columna `fcm_token` en `usuario`, en una migración V3 | El modelo físico no tiene dónde guardar el token del dispositivo, y sin él no se puede enviar un push. |
| "Tiempo real" (RF-04, RF-10) | Polling cada 5 s en el panel; push FCM al estudiante | Cumple el requisito con poco riesgo; SSE queda como mejora si sobra tiempo. |
| Producto agotado: ¿se oculta con `activo = FALSE`? | El catálogo muestra "AGOTADO" desde la vista y `activo` queda solo para la baja manual del dueño | Hoy `activo` significa dos cosas y, al recargar stock, no se sabe si reactivar el producto (ver Análisis, punto 7). |
| Migraciones | Flyway: V1 esquema, V2 datos de prueba, V3 en adelante para cambios | Todos trabajan con la misma BD y Claude Code no toca el esquema sin un archivo revisable. |
| Orden de trabajo | Tres pistas paralelas tras congelar el contrato | Ver arriba. |
| Pago en efectivo | Opción A: quitar `EFECTIVO` de la app. Opción B: el pedido en efectivo entra a despacho con pago `PENDIENTE` y el trabajador lo pasa a `PAGADO` al entregar (no reserva stock) | El informe (CU-02) dice que en efectivo se paga al recoger, pero el stock solo baja con `PAGADO` y el despacho filtra pagados: hoy el trabajador nunca vería esos pedidos. |
| Estado del pedido tras pagar | Queda `PENDIENTE` y el trabajador lo confirma (RF-11); corregir la postcondición de CU-02 en el informe, que dice "Confirmado" | Plan e informe se contradicen. Pagado/no pagado se distingue por `pago.estado`, no por `pedido.estado`. |
| Cancelar un pedido pagado | Opción A: solo se cancela sin pagar. Opción B: cancelar pagado = pago `REEMBOLSADO` + reponer stock en el servicio | No hay trigger que devuelva stock; sin regla, cancelar un pedido pagado pierde unidades. |
| Correcciones al esquema | Aplicarlas directo en `V1` al crearla (aún no se aplicó en ninguna BD): trigger de agotado, `fcm_token`, alertas solo al cruzar el mínimo | Evita migraciones de parche. Hay que reflejarlo en el informe (RN-03). |

iOS queda fuera del alcance (informe, §2.4 Limitaciones).

## Análisis del stack y del modelo físico

El stack es coherente con lo que el curso pide sustentar (capas, modelo relacional, roles). Los riesgos no están en la elección de tecnologías sino en cómo se juntan Hibernate y la lógica que vive en los triggers de PostgreSQL.

Lo que ya está bien resuelto:

- Una sola API REST para dos clientes justifica separar backend y frontend.
- La especialización (tabla por subtipo) se mapea directo con `@Inheritance(strategy = JOINED)`.
- Precio histórico en `detalle_pedido`, `CHECK` de stock no negativo, cardinalidades 1:1 con `UNIQUE` y vistas para las consultas pesadas.

### Puntos a resolver antes o durante la construcción

| # | Punto | Por qué importa | Qué hacer | Fase |
| --- | --- | --- | --- | --- |
| 1 | Enums nativos de PostgreSQL | Hibernate envía `varchar` y PostgreSQL rechaza el insert en columnas `estado_pedido_enum`, `tipo_usuario_enum`, etc. | `@Enumerated(EnumType.STRING)` en las entidades y `stringtype=unspecified` en la URL JDBC (cubre también el discriminador `tipo_usuario`). | 1 |
| 2 | Esquema `cafetin` | Sin configurarlo, Hibernate y Flyway trabajan en `public`. | `hibernate.default_schema=cafetin` y `spring.flyway.schemas=cafetin`; quitar `DROP/CREATE SCHEMA` y `SET search_path` del script al convertirlo en `V1`. | 0–1 |
| 3 | Columnas que calcula la BD | `detalle_pedido.subtotal` es `GENERATED` y `pedido.total` lo escribe un trigger: si Java intenta escribirlas falla, y si no refresca devuelve total 0. | Mapear ambas como solo lectura (`insertable = false, updatable = false`) y refrescar el pedido después de guardar el detalle. | 1, 3 |
| 4 | Triggers y caché de JPA | El stock baja dentro de PostgreSQL; la entidad `Inventario` cargada en la misma transacción queda desactualizada. | No leer stock del contexto de persistencia tras un pago; volver a consultar. Documentarlo en CLAUDE.md. | 3 |
| 5 | `trg_descontar_stock` solo se dispara en `UPDATE` | Si el servicio inserta el pago directamente como `PAGADO`, el stock no se descuenta. | Flujo fijo: `INSERT` pago `PENDIENTE` → pasarela → `UPDATE` a `PAGADO`. Un test de integración lo protege. | 1, 3 |
| 6 | Stock insuficiente se detecta tarde | El `CHECK cantidad_disponible >= 0` solo falla al confirmar el pago; con dos estudiantes comprando la última unidad, uno recibe un error de BD. | Validar stock al crear el pedido y capturar `DataIntegrityViolationException` al pagar → respuesta 409 "stock insuficiente" (RNF-03). | 3 |
| 7 | `producto.activo` tiene dos significados | El trigger lo pone en `FALSE` al agotarse (RF-17) y el dueño lo usa para deshabilitar (RF-19); al recargar stock (RF-18) no hay forma de saber si reactivar. | Quitar del trigger el `UPDATE producto SET activo = FALSE` (migración) y filtrar agotados con `estado_stock` de `v_catalogo_disponible`. Alternativa: aceptar que recargar reactiva. | 0 (decidir), 4 |
| 8 | Las alertas de stock no salen de la BD | Un trigger inserta en `notificacion` y `alerta_stock`, pero no puede llamar a Firebase. | El panel del dueño lista las alertas (polling); si se quiere push al dueño, el servicio de pago revisa alertas nuevas tras confirmar. | 4–5 |
| 9 | No hay dónde guardar el token FCM | Sin token no hay push (RF-08). | Migración V3 con `usuario.fcm_token`. | 0, 5 |
| 10 | `v_pedidos_en_curso` incluye pedidos sin pagar | El trabajador vería pedidos `PENDIENTE` que nunca se pagaron. | Filtrar `estado_pago = 'PAGADO'` en la consulta del despacho (o ajustar la vista). | 3 |
| 11 | Transiciones de estado sin regla explícita | El esquema acepta cualquier cambio de `estado`; RF-11 a RF-14 suponen un orden. | Una clase de servicio decide las transiciones válidas; las demás devuelven 409. | 3 |
| 12 | Códigos `PED-0427` y `codigo_verificacion` | Deben ser únicos y legibles. Con ≈750 pedidos/día, 4 dígitos se agotan en dos semanas. | `codigo_pedido` desde una secuencia sin relleno fijo (`PED-12345` cabe en `VARCHAR(12)`); código de recibo con el formato del informe `DR-<nº>-<4 dígitos aleatorios>` (Tabla 16), con reintento si choca con el `UNIQUE`. | 3 |
| 13 | Pruebas con H2 no sirven | H2 no ejecuta triggers PL/pgSQL, enums de PostgreSQL ni estas vistas. | Testcontainers con PostgreSQL 16 desde la Fase 1 (requiere Docker en cada equipo y en CI). | 1 |
| 14 | RF-03 con token en texto plano | `usuario.token_recupera` guarda el token tal cual. | Guardar un hash del token, expiración corta; en desarrollo el enlace se escribe en el log. | 2 |
| 15 | RNF-04 "cifrar datos de pago" | Con pasarela simulada no hay datos de tarjeta que guardar. | No guardar datos de tarjeta, solo `referencia`; HTTPS en el despliegue; contraseñas con BCrypt (ya previsto). | 3, 7 |
| 16 | Reintento de pago y `UNIQUE(id_pedido)` en `pago` | Si la pasarela rechaza, el informe permite reintentar o cambiar de método; un segundo `INSERT` viola el `UNIQUE`. | Reutilizar la fila existente (`FALLIDO`/`PENDIENTE` → actualizar método y estado). Test de rechazo y reintento. | 3 |
| 17 | Alertas de stock repetidas | `trg_alerta_stock_minimo` inserta una alerta por dueño en cada venta mientras el stock siga bajo el mínimo. | Alertar solo al cruzar el mínimo (`OLD.cantidad_disponible > NEW.stock_minimo`) o al llegar a 0; al recargar, marcar atendidas las abiertas. | 0, 4 |
| 18 | `pago.monto` y `pedido.total` no están ligados | Nada impide cobrar un monto distinto del total. | El servicio toma `monto` del total refrescado; test que lo comprueba. | 3 |
| 19 | El seed no permite iniciar sesión ni ver ingresos | Las contraseñas no son hashes BCrypt válidos y los pagos de los pedidos 1 y 2 quedan `PENDIENTE` (sin descuento de stock, reportes vacíos, pedido 2 fuera del despacho). | En `V2`: hashes BCrypt reales con una contraseña de demo documentada, `UPDATE` de esos pagos a `PAGADO` y fechas en varios días para los reportes. | 0 |
| 20 | Pedidos sin pagar que nunca expiran y `hora_recojo` sin validar | Se acumulan carritos abandonados en `PENDIENTE`; se puede pedir para una hora pasada. | Definir cancelación automática (o aceptarlo por escrito) y validar la hora en el servicio. | 3 |

## Repositorio y forma de trabajo con Claude Code

Un monorepo con las tres piezas y un `CLAUDE.md` en la raíz permite que Claude Code vea el esquema, el contrato de la API y los clientes en la misma sesión. El `CLAUDE.md` propuesto va como archivo aparte, listo para copiar.

```
gestion-cafetin/
├── CLAUDE.md                  contexto común para Claude Code
├── .env.example               variables: conexión a Neon, JWT, CORS (sin secretos reales)
├── .github/workflows/api.yml  CI: ./mvnw verify en cada PR
├── docs/                      análisis, diagramas, openapi.yaml, matriz de trazabilidad
├── api/                       Spring Boot 3 · Java 17+ · Maven
│   ├── CLAUDE.md              reglas del backend
│   └── src/main/resources/db/migration/   V1 esquema, V2 datos, V3…
├── panel-web/                 React 18 + TypeScript (Vite)
│   └── CLAUDE.md
└── app-android/               Kotlin + Jetpack Compose
    └── CLAUDE.md
```

### Reglas de trabajo

1. Una rama por tarea (`feat/p2-crear-pedido`) y un PR pequeño que revisa otra persona del equipo. En la sustentación cada quien debe poder explicar su código.
2. Cada tarea empieza en modo plan: Claude lee los archivos relevantes y propone el plan; el equipo lo corrige y recién entonces se edita.
3. Todo prompt dice cómo verificar: el comando que debe pasar (`./mvnw verify`, `npm test`, `./gradlew testDebugUnitTest`).
4. Contexto concreto en cada prompt: el RF, las tablas y el endpoint del contrato.
5. El esquema solo cambia con una nueva migración `V<n>__descripcion.sql` revisada por el equipo; nunca editando V1 o V2 ya aplicadas.
6. Cuando el equipo decide algo o Claude repite un error, se anota en el `CLAUDE.md` correspondiente.
7. Sesión nueva (o `/clear`) entre tareas que no tienen relación, para no arrastrar contexto.

### Ramas y protección de `main`

`main` está protegida: nadie (ni el dueño del repo) sube cambios directo a ella. Todo entra por un pull request revisado por otra persona del equipo.

**Configurar la protección (una vez, lo hace el dueño del repo):**

1. En GitHub, abrir el repo → **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**.
2. **Ruleset name:** `proteger-main`. **Enforcement status:** `Active`.
3. **Target branches** → **Add target** → **Include default branch** (es `main`).
4. **Bypass list:** dejarla vacía, para que la regla aplique a todos.
5. Marcar estas reglas:
   - **Restrict deletions** — nadie puede borrar `main`.
   - **Block force pushes** — nadie puede reescribir su historial.
   - **Require a pull request before merging**, con **Required approvals: 1** y **Dismiss stale pull request approvals when new commits are pushed**.
   - **Require status checks to pass** → agregar el check `verify` del workflow `API` (aparece en la lista después de que corra una vez en un PR).
6. **Create**.
7. Agregar a los compañeros: **Settings** → **Collaborators** → **Add people** (con permiso *Write*).

Para comprobarlo: un `git push origin main` directo debe ser rechazado con un mensaje de que la rama está protegida.

**Flujo de trabajo para cada tarea:**

```
git switch main
git pull                                   # partir siempre del main actualizado
git switch -c feat/p2-crear-pedido         # una rama por tarea
# ... cambios, tests en verde ...
git add <archivos>
git commit -m "RF-05: crear pedido con detalle"
git push -u origin feat/p2-crear-pedido    # GitHub muestra el enlace para abrir el PR
```

Después, en GitHub: abrir el PR hacia `main`, pedir revisión a un compañero, corregir lo que pida y, con la aprobación y el CI en verde, **Squash and merge** y borrar la rama.

**Nombres de rama:** `<tipo>/<paquete>-<tarea-corta>`.

| Tipo | Uso | Ejemplo |
| --- | --- | --- |
| `feat/` | Funcionalidad nueva | `feat/p4-recarga-stock` |
| `fix/` | Corrección de un error | `fix/p3-transicion-listo` |
| `docs/` | Solo documentación | `docs/manual-dueno` |
| `test/` | Solo pruebas | `test/p2-pago-concurrente` |
| `chore/` | Configuración, CI, dependencias | `chore/ci-jacoco` |

Si `main` avanzó mientras trabajabas: `git switch main && git pull && git switch <tu-rama> && git merge main`, resolver conflictos, correr los tests y volver a hacer push.

### Reparto del equipo por módulos

Las fases no se pueden repartir una por persona porque dependen unas de otras: 0, 1 y 2 son la base común y la 7 necesita todo. Lo que sí se reparte son los **paquetes funcionales**: cada persona se queda con un módulo de punta a punta (API + pantallas). Funciona una vez cerrada la base común y respetando los puntos de acoplamiento.

| Fase | Depende de | ¿Independiente? |
| --- | --- | --- |
| 0 Repo y entorno | — | No: base de todos. Se reparte por tareas (Neon/CI, V1/V2, CLAUDE.md). |
| 1 Entidades y contrato | 0 | Parcial: las 14 entidades son compartidas y las crea una persona; cada dueño de módulo escribe en `openapi.yaml` los DTOs y endpoints de su paquete. |
| 2 Cuentas (P1) | 1 | Bloquea solo el login. Los demás avanzan con `@WithMockUser` en los tests y un token de prueba. |
| 3 Pedidos y despacho (P2, P3) | 1, 2 | Sí, con el catálogo e inventario del seed. |
| 4 Inventario e ingresos (P4, P5) | 1, 2 | Sí, con los pagos del seed (una vez corregido). |
| 5 Notificaciones | 3 | Sí, si la Fase 1 fija el evento `PedidoEstadoCambiado`: la Fase 3 lo publica y la Fase 5 lo escucha. |
| 6 Pruebas | todas | Transversal: cada módulo entrega sus tests; la matriz y la carga se consolidan al final. |
| 7 Despliegue | todas | No; se adelantan el Dockerfile y un despliegue temprano. |

| Responsable | Módulo | API | Cliente | RF |
| --- | --- | --- | --- | --- |
| José Daniel Lizca | Cuentas, notificaciones e infraestructura | Fase 2 (JWT, roles, `SecurityConfig`), Fase 5 (FCM), CI y Fase 7 | Login y registro en app y panel; shell del panel con menú por rol | RF-01–03, RF-08 (envío) |
| Eliú Barrera | Pedidos, pagos y despacho (núcleo) | Fase 3 completa, incluida la clase de transiciones de estado | App Android: catálogo, carrito, pago, recibo, historial | RF-04–07, RF-09–14 |
| Maria Elizabeth Ruiz | Inventario e ingresos | Fase 4 completa | Panel web: tablero de despacho y pantallas del dueño | RF-15–22 |

Así cada responsable puede explicar un flujo completo en la sustentación, la máquina de estados tiene un solo dueño y la app (Eliú) y el panel (Maria) no se pisan.

**Puntos de acoplamiento (se acuerdan en la Fase 1):**

1. `docs/openapi.yaml` congelado: los clientes trabajan contra el contrato aunque el endpoint devuelva 501.
2. Entidades compartidas: cualquier cambio después de la Fase 1 va en un PR que revisa el dueño del módulo afectado.
3. `SecurityConfig` y `application.yml` son de Lizca; los demás piden sus reglas por PR.
4. Migraciones con números reservados: Lizca `V3`–`V9`, Eliú `V10`–`V19`, Maria `V20`–`V29`. Dos `V3` en ramas distintas rompen Flyway.
5. Evento `PedidoEstadoCambiado` en `comun`: lo publica Eliú y lo consume Lizca.
6. Formato de error `{codigo, mensaje, detalles}` y `@RestControllerAdvice` en `comun`: se crean en la Fase 1 y no los cambia cada módulo.
7. El seed (`V2`) es la fuente de datos común: corregirlo primero (punto 19 del Análisis).

**Tareas por responsable** (en orden de dependencia, sin fechas):

| Etapa | José Daniel Lizca | Eliú Barrera | Maria Elizabeth Ruiz |
| --- | --- | --- | --- |
| Base común (Fases 0–1) | Proyecto Neon y ramas, CI, V1 y V2 corregidas | Entidades JPA + test de humo | Contrato OpenAPI + `comun` (errores, evento) |
| Módulo propio | Seguridad JWT + login en app y panel | Catálogo + crear pedido (tests primero) | Productos e inventario (RF-15, RF-19) |
| Módulo propio | FCM + despliegue temprano (Render + Vercel) | Pagar, máquina de estados, despacho (API) + app | Alertas, recarga, reportes + panel (despacho y dueño) |
| Pruebas | Carga con k6, trazabilidad | Pruebas del núcleo y de UI de la app | Pruebas del panel, aceptación por rol |
| Cierre | Producción, APK firmado | Ensayo de la demo | Manuales |

### Plantilla de prompt

```
Contexto: RF-05 (armar pedido). Tablas: pedido, detalle_pedido. Endpoint: POST /api/pedidos (docs/openapi.yaml).
Tarea: implementa PedidoService.crear y su controller. Copia el precio actual del producto a precio_unitario.
No calcules el total en Java (lo hace trg_calcular_total_pedido): refresca el pedido después de guardar.
Antes de editar: lee CLAUDE.md y las entidades Pedido y DetallePedido, y propón un plan.
Hecho cuando: ./mvnw verify pasa e incluye un test con Testcontainers que crea un pedido de 2 productos y comprueba el total.
```

## Fase 0 — Repositorio y entorno · 19/10 – 23/10

Al terminar, cualquiera del equipo clona el repo, se conecta a su propia rama de Neon y abre Claude Code con el contexto ya cargado.

- [x] Crear el repo en GitHub con la estructura de arriba y `.gitignore` para Java, Node y Android.
- [x] Proteger `main` (ver "Ramas y protección de `main`").
- [ ] Agregar a los compañeros como colaboradores con permiso *Write*.
- [x] Copiar el `CLAUDE.md` raíz y crear uno corto en `api/`, `panel-web/` y `app-android/`.
- [x] `.env.example` con la conexión a Neon, el secreto JWT y los orígenes CORS (sin valores reales).
- [ ] Crear el proyecto en Neon, una rama por persona e invitar al equipo (ver "Configurar Neon").
- [x] Convertir `01_gestion_cafetin_schema.sql` en `V1__esquema.sql` sin `DROP SCHEMA`, `CREATE SCHEMA` ni `SET search_path` (Flyway crea el esquema; si el script lo borra, borra también el historial de migraciones). `02_gestion_cafetin_datos.sql` pasa a `V2__datos_prueba.sql` con las correcciones del punto 19 del Análisis. Las correcciones de esquema decididas (puntos 7, 9 y 17) van directo en `V1`.
- [x] Mover `Documentación/` a `docs/` para que coincida con `CLAUDE.md`.
- [ ] Cerrar las decisiones del Resumen y escribirlas en `CLAUDE.md`. Cerradas el 02/11: `fcm_token`, producto agotado, alertas, infraestructura (Neon, Render, Vercel) y panel en React. Faltan: pasarela, tiempo real, efectivo y cancelación.
- [x] Workflow de GitHub Actions que ejecuta `./mvnw verify` en `api/` en cada PR (se omite mientras no exista `api/pom.xml`).
- [ ] Docker instalado en cada equipo: lo necesitan los tests (Testcontainers), aunque la base de desarrollo esté en Neon.

### Configurar Neon (una vez, lo hace Lizca)

1. Crear una cuenta en [neon.tech](https://neon.tech) e iniciar sesión con GitHub.
2. **New project**: nombre `cafetin`, **Postgres 16**, región **AWS US East 2 (Ohio)**. Render despliega la API en Ohio y conviene que estén en la misma región para que las consultas sean rápidas.
3. La rama `main` de Neon es **producción**: nadie desarrolla contra ella.
4. **Branches** → **New branch** desde `main`: crear `dev-lizca`, `dev-eliu` y `dev-maria`.
5. **Settings** → **Collaborators** (o la organización del proyecto): invitar a Eliú y a Maria.
6. Cada uno, en su rama: **Connect** → desmarcar **Connection pooling** → copiar host, usuario y contraseña a su `.env` (formato en `.env.example`). La conexión debe ser la **directa** (host sin `-pooler`).
7. Al arrancar la API en el perfil `dev`, Flyway crea el esquema `cafetin`, aplica V1 y V2 y fija la zona horaria `America/Lima`.

Para volver a empezar con datos limpios: en Neon, **Branches** → tu rama → **Reset from parent**, y arrancar la API otra vez.

Hecho cuando: cada integrante tiene su rama en Neon y su `.env` local, y conecta con `psql "<cadena de conexión>"` (o desde el editor SQL de Neon).

## Fase 1 — Esqueleto de la API, entidades y contrato · 26/10 – 06/11

Al cerrar Diseño, la API arranca contra PostgreSQL con las 14 entidades validadas por Hibernate (`ddl-auto: validate`) y el contrato OpenAPI queda congelado para que app y panel empiecen en paralelo.

- [ ] Proyecto Spring Boot 3 con Web, Data JPA, Validation, Security, driver PostgreSQL, Flyway, springdoc-openapi y Testcontainers.
- [ ] `application.yml` con perfiles `dev` y `test`: `ddl-auto: validate`, `default_schema: cafetin`, URL JDBC con `stringtype=unspecified`; V2 (datos de prueba) solo en `dev`.
- [ ] Paquetes por módulo y, dentro de cada uno, capas `controller` / `service` / `repository` / `dto`: `cuentas`, `catalogo`, `pedidos`, `despacho`, `inventario`, `reportes`, `comun`. Así se ven los cinco paquetes del análisis y la arquitectura en capas que hay que sustentar.
- [ ] Entidades: `Usuario` abstracta (JOINED, discriminador `tipo_usuario`) con `Estudiante`, `Trabajador`, `Dueno`; `Notificacion` (JOINED) con `AlertaStock`; `Categoria`, `Proveedor`, `Producto`, `Inventario` (1:1), `Pedido`, `DetallePedido`, `Pago` (1:1), `Recibo` (1:1).
- [ ] Las 5 vistas como entidades de solo lectura (`@Immutable`), cada una con su `@Id` (`dia` en ingresos diarios, `id_producto` en más vendidos).
- [ ] Manejo de errores único (`@RestControllerAdvice`) con formato `{codigo, mensaje, detalles}`; violación de integridad → 409.
- [ ] Contrato: todos los controllers con sus DTOs y respuesta 501, exportado a `docs/openapi.yaml` (ver Contrato de la API).
- [ ] Tests de humo con Testcontainers: el contexto arranca, Flyway aplica V1–V2, y un `UPDATE` de pago a `PAGADO` descuenta el stock.

Prompt sugerido:

```
Lee CLAUDE.md y api/src/main/resources/db/migration/V1__esquema.sql.
Crea las 14 entidades JPA en sus paquetes de módulo con los nombres exactos de tablas y columnas.
Herencia JOINED con discriminador tipo_usuario. detalle_pedido.subtotal y pedido.total son de solo lectura.
No modifiques el SQL. Propón primero el plan y la lista de clases.
Hecho cuando ./mvnw verify pasa con ddl-auto=validate y un test de Testcontainers que carga un Estudiante.
```

Hecho cuando: `./mvnw verify` en verde y Swagger UI muestra todos los endpoints del contrato.

## Fase 2 — Cuentas y acceso (RF-01 a RF-03) · 09/11 – 13/11

Los tres roles inician sesión con JWT y cada endpoint queda protegido por rol desde el primer día (RNF-05). En paralelo, la app y el panel arrancan con login contra la API real.

**API**

- [ ] Spring Security sin sesión, filtro JWT, BCrypt para contraseñas.
- [ ] `POST /api/auth/registro` (estudiante), `POST /api/auth/login` (token + rol), `POST /api/auth/recuperar`, `POST /api/auth/restablecer`.
- [ ] `POST /api/trabajadores` solo para `DUENO` (el dueño registra a los trabajadores); el dueño inicial viene de V2.
- [ ] Reglas por ruta: `/api/inventario/**` y `/api/reportes/**` → `DUENO`; `/api/despacho/**` → `TRABAJADOR` y `DUENO`; `/api/pedidos/**` → `ESTUDIANTE`.
- [ ] Token de recuperación guardado como hash y con expiración; en `dev` el enlace se escribe en el log.
- [ ] CORS para el origen del panel.
- [ ] Tests: login correcto, contraseña errónea → 401, correo duplicado → 409, estudiante en `/api/reportes` → 403.

**App Android**

- [ ] Proyecto Compose con MVVM, Navigation Compose, Retrofit + OkHttp (interceptor que añade el JWT) y DataStore para el token.
- [ ] Pantallas de login, registro y recuperación. En el emulador la API está en `10.0.2.2:8080`; tráfico sin HTTPS permitido solo en el build `debug`.

**Panel web**

- [ ] Vite + React + TypeScript, React Router, TanStack Query y tipos generados desde `docs/openapi.yaml`.
- [ ] Login y menú según rol (trabajador: Despacho; dueño: todo), rutas protegidas.

Prompt sugerido (API):

```
Implementa la seguridad descrita en CLAUDE.md, sección Seguridad. Usuarios en la tabla usuario
(correo, contrasena con BCrypt, tipo_usuario). Roles: ESTUDIANTE, TRABAJADOR, DUENO.
Propón primero el plan y la lista de clases.
Hecho cuando hay tests de integración para: login OK, contraseña errónea 401, estudiante en /api/reportes 403.
```

Hecho cuando: los tres roles del seed inician sesión desde Swagger, la app y el panel.

## Fase 3 — Pedidos, pagos y despacho (RF-04 a RF-14) · 16/11 – 27/11

Es el núcleo del sistema: al terminar, un estudiante pide y paga desde la app y un trabajador lo despacha desde el panel verificando el recibo. Por eso tiene dos semanas.

**API**

- [ ] Catálogo (`GET /api/catalogo`) desde `v_catalogo_disponible`, con filtro por categoría.
- [ ] Crear pedido: valida stock por ítem, copia el precio a `precio_unitario`, genera `codigo_pedido`, deja que el trigger calcule el total y refresca el pedido.
- [ ] Pagar: interfaz `PasarelaPago` con `PasarelaSimulada`; `INSERT` pago `PENDIENTE` → pasarela → `UPDATE` a `PAGADO` (descuenta stock) → crea `Recibo` con código de verificación. Si la BD rechaza por stock → 409 y se revierte la transacción.
- [ ] Máquina de estados en una sola clase: `PENDIENTE` (pagado) → `CONFIRMADO` → `EN_PREPARACION` → `LISTO` → `ENTREGADO`; `CANCELADO` solo desde `PENDIENTE`. Cualquier otra transición → 409.
- [ ] Despacho: lista desde `v_pedidos_en_curso` filtrando pagados; confirmar (asigna `id_trabajador`), preparar, listo, verificar código del recibo, entregar (exige recibo verificado).
- [ ] Historial del estudiante y detalle del pedido con estado y recibo.
- [ ] Cada cambio de estado registra una fila en `notificacion`; el push llega en la Fase 5.

**Pruebas clave (Testcontainers)**

- [ ] Pedido de 2 productos → total correcto.
- [ ] Pago aprobado → baja el stock y se crea el recibo.
- [ ] Dos pagos concurrentes por la última unidad → uno pasa, el otro recibe 409 (RNF-03).
- [ ] Transición inválida (`LISTO` → `CONFIRMADO`) → 409.
- [ ] Entregar sin recibo verificado → 409.
- [ ] Pago rechazado y reintento con otro método → un solo registro en `pago`, termina `PAGADO`.
- [ ] `pago.monto` igual a `pedido.total`.
- [ ] Cancelación según lo decidido (sin pagar, o pagado con reembolso y stock repuesto).
- [ ] Estudiante que consulta un pedido ajeno → 403 o 404.

**App Android:** catálogo con estado de stock, carrito, hora de recojo, pago (método), pantalla de recibo con el código grande, historial.

**Panel web:** tablero de pedidos en curso por columnas de estado, refresco cada 5 s, botones de acción y campo para verificar el código.

Prompt sugerido (escribir los tests primero):

```
Implementa la máquina de estados del pedido descrita en CLAUDE.md, sección Estados del pedido.
Una sola clase decide las transiciones; los controllers solo la llaman.
Primero escribe los tests de transiciones válidas e inválidas y muéstramelos.
Después implementa hasta que pasen con ./mvnw verify.
```

Hecho cuando: el flujo completo pedir → pagar → confirmar → listo → verificar → entregar funciona de punta a punta con datos del seed.

## Fase 4 — Inventario e ingresos (RF-15 a RF-22) · 23/11 – 04/12

El dueño gestiona productos y stock desde el panel, ve las alertas que generan los triggers y consulta ingresos y productos más vendidos. Arranca a mitad de la Fase 3 porque casi no depende de pedidos.

**API**

- [ ] Alta de producto con su inventario inicial en la misma transacción (RF-15); editar y deshabilitar (RF-19); CRUD de categorías y proveedores.
- [ ] Recarga de stock (RF-18) según lo decidido sobre `activo` (Análisis, punto 7).
- [ ] Alertas: lista desde `alerta_stock` y `v_alertas_reposicion` con el teléfono del proveedor; marcar como atendida.
- [ ] Ingresos (RF-21): por día desde `v_ingresos_diarios`; semana y mes agrupando con `date_trunc`.
- [ ] Más vendidos (RF-22) desde `v_productos_mas_vendidos`. La vista no tiene fecha: si se pide filtrar por periodo, hace falta una consulta propia.
- [ ] Historial de ventas (RF-20): los pagos en `PAGADO` ya son el registro de cada venta.
- [ ] Tests: una venta que baja del mínimo genera alerta; la recarga actualiza la fecha; estudiante y trabajador → 403 en `/api/inventario/**` y `/api/reportes/**`.

**Panel web:** productos (tabla y formulario), inventario con nivel frente al mínimo, alertas con contacto del proveedor, reportes con gráfico por día, semana o mes y ranking de productos.

Prompt sugerido:

```
Implementa GET /api/reportes/ingresos?periodo=dia|semana|mes&desde=&hasta= para el rol DUENO.
Para dia usa la vista v_ingresos_diarios; para semana y mes agrupa pagos PAGADO con date_trunc.
Agrega un test con Testcontainers que inserte pagos en dos semanas distintas y compruebe los totales.
```

Hecho cuando: el dueño del seed registra un producto, lo vende hasta el mínimo, ve la alerta, recarga y consulta el reporte.

## Fase 5 — Notificaciones push y tiempo real (RF-08, RF-10, RF-17) · 30/11 – 04/12

El estudiante recibe un push cuando su pedido pasa a confirmado, listo o entregado; el panel ya se actualiza por polling desde la Fase 3.

- [ ] Proyecto en Firebase. `google-services.json` en la app y la credencial de servicio del Admin SDK en el backend como variable de entorno, nunca en el repo.
- [ ] Migración V3 con `usuario.fcm_token`, si no se hizo en la Fase 0.
- [ ] App: registrar el token tras el login (`PUT /api/usuarios/me/fcm-token`) y pedir el permiso de notificaciones en Android 13 o superior.
- [ ] API: `NotificacionService` guarda en `notificacion` y envía por FCM después del commit (`@TransactionalEventListener(phase = AFTER_COMMIT)`), así no se notifica un cambio revertido y el pago sigue por debajo de 5 s (RNF-02).
- [ ] Panel: contador de alertas de stock para el dueño (polling).
- [ ] Respaldo si FCM se retrasa: la app consulta sus notificaciones de la tabla al abrirse.
- [ ] Opcional si sobra tiempo: SSE en `/api/despacho/stream` para reemplazar el polling del panel.
- [ ] Tests: un cambio de estado crea la notificación y llama al cliente FCM (simulado en tests).

Hecho cuando: en un dispositivo físico llega el push "Tu pedido está listo" al marcarlo desde el panel.

## Fase 6 — Pruebas y trazabilidad · 23/11 – 11/12

Las pruebas no esperan al final: cada fase entrega sus tests y esta fase consolida la trazabilidad RF/RNF → prueba, la prueba de carga de hora punta y la aceptación por rol.

| Nivel | Herramienta | Qué cubre |
| --- | --- | --- |
| Unitarias | JUnit 5 + Mockito | Máquina de estados, generación de códigos, reglas de servicio |
| Integración | Spring Boot Test + Testcontainers (PostgreSQL 16) | Triggers, vistas, seguridad por rol, flujos completos |
| API | REST Assured o colección Postman/Newman | Casos de aceptación por RF contra el contrato |
| App Android | JUnit + pruebas de UI de Compose | ViewModels y pantallas críticas (login, carrito, pago) |
| Panel web | Vitest + Testing Library | Tablero de despacho y formularios del dueño |
| Carga | k6 o JMeter | RNF-02 y RNF-03: pedidos simultáneos de hora punta, confirmación por debajo de 5 s |
| Aceptación | Lista de RF con evidencia | Recorrido por rol con los datos del seed |

- [ ] Matriz de trazabilidad en `docs/`: RF-01…RF-22 y RNF-01…RNF-08 → casos de prueba → resultado.
- [ ] Umbral de cobertura en servicios acordado por el equipo (JaCoCo) y verificado en CI.
- [ ] Prueba de carga del flujo crear + pagar, con el objetivo del informe: ≈100 pedidos por hora en hora punta (4 trabajadores), cada confirmación por debajo de 5 s.
- [ ] Pruebas exploratorias en un teléfono real y registro de defectos como issues de GitHub.

Prompt sugerido:

```
Genera docs/trazabilidad.md: para cada RF-01..RF-22 y RNF-01..RNF-08 de CLAUDE.md, lista los tests
que lo cubren (busca en api/src/test, panel-web y app-android) y marca los que no tienen cobertura.
No escribas tests todavía.
```

Hecho cuando: cada RF tiene al menos un caso con resultado registrado y los RNF medibles tienen evidencia.

## Fase 7 — Despliegue y cierre · 07/12 – 18/12

Se publica una versión estable para la demo: base de datos en Neon (rama `main`), API en Render, panel en Vercel y app como APK firmado. Los tres dan HTTPS (RNF-04).

- [ ] `Dockerfile` multietapa para la API; `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `CORS_ORIGINS` y la credencial de Firebase por variables de entorno.
- [ ] **Render**: *New* → *Web Service* desde el repo de GitHub, *Root Directory* `api`, entorno *Docker*, región **Ohio**, perfil `prod`, variables de entorno con la conexión **directa** a la rama `main` de Neon, *Health Check Path* `/actuator/health`. Revisar las condiciones del plan gratuito al desplegar: si el servicio se duerme sin tráfico, el primer arranque de la JVM tarda.
- [ ] **Vercel**: *Add New* → *Project* desde el repo, *Root Directory* `panel-web`, framework Vite, variable `VITE_API_URL` con la URL de Render, `vercel.json` con rewrite a `/index.html`. Agregar el dominio de Vercel a `CORS_ORIGINS` en Render.
- [ ] **Neon**: la API en `prod` aplica solo las migraciones del esquema (sin V2) sobre la rama `main`.
- [ ] App: APK de release firmado con la URL de producción, distribuido directamente (sin Play Store).
- [ ] RNF-06: health check con Spring Boot Actuator y reinicio automático del contenedor. El 99 % de disponibilidad no se puede demostrar en dos semanas; se documenta cómo se mediría.
- [ ] Datos de demo: dueño, dos trabajadores, varios estudiantes y un catálogo realista; V2 no se aplica en producción salvo para la demo.
- [ ] Manual de usuario por rol y README técnico por módulo (Claude Code puede redactar el borrador a partir del código; el equipo lo revisa).
- [ ] Ensayo completo de la demo y tag `v1.0`.

Hecho cuando: la demo corre de punta a punta sobre la versión desplegada, sin depender de ninguna laptop del equipo.

## Contrato de la API (propuesta para la Fase 1)

Estos endpoints cubren RF-01 a RF-22; se congelan en `docs/openapi.yaml` al cierre de la Fase 1 y cualquier cambio posterior se acuerda en el equipo. Las acciones de despacho usan `POST` porque son transiciones de estado, no ediciones.

| Paquete | Método y ruta | Rol | RF |
| --- | --- | --- | --- |
| Cuentas | `POST /api/auth/registro` | Público | RF-01 |
| Cuentas | `POST /api/auth/login` | Público | RF-01, RF-02 |
| Cuentas | `POST /api/auth/recuperar` | Público | RF-03 |
| Cuentas | `POST /api/auth/restablecer` | Público | RF-03 |
| Cuentas | `GET /api/trabajadores` · `POST /api/trabajadores` | DUENO | RF-02 |
| Cuentas | `PUT /api/usuarios/me/fcm-token` | Autenticado | RF-08 |
| Pedidos | `GET /api/catalogo?categoria=` | Autenticado | RF-04 |
| Pedidos | `GET /api/categorias` | Autenticado | RF-04 |
| Pedidos | `POST /api/pedidos` | ESTUDIANTE | RF-05 |
| Pedidos | `POST /api/pedidos/{id}/pago` | ESTUDIANTE | RF-06, RF-07, RF-16, RF-20 |
| Pedidos | `POST /api/pedidos/{id}/cancelar` | ESTUDIANTE | RF-05 |
| Pedidos | `GET /api/pedidos/{id}` | ESTUDIANTE (propio) | RF-08 |
| Pedidos | `GET /api/pedidos/mios` | ESTUDIANTE | RF-09 |
| Despacho | `GET /api/despacho/pedidos` | TRABAJADOR, DUENO | RF-10 |
| Despacho | `POST /api/despacho/pedidos/{id}/confirmar` | TRABAJADOR, DUENO | RF-11 |
| Despacho | `POST /api/despacho/pedidos/{id}/preparar` | TRABAJADOR, DUENO | RF-11 |
| Despacho | `POST /api/despacho/pedidos/{id}/listo` | TRABAJADOR, DUENO | RF-12 |
| Despacho | `POST /api/despacho/pedidos/{id}/verificar` | TRABAJADOR, DUENO | RF-13 |
| Despacho | `POST /api/despacho/pedidos/{id}/entregar` | TRABAJADOR, DUENO | RF-14 |
| Inventario | `GET /api/productos` · `POST /api/productos` | DUENO | RF-15 |
| Inventario | `PUT /api/productos/{id}` · `PATCH /api/productos/{id}/estado` | DUENO | RF-19 |
| Inventario | `PATCH /api/inventario/{idProducto}/recarga` | DUENO | RF-18 |
| Inventario | `GET /api/alertas` · `PATCH /api/alertas/{id}/atendida` | DUENO | RF-17 |
| Inventario | CRUD `/api/categorias` y `/api/proveedores` (escritura) | DUENO | RF-15 |
| Ingresos | `GET /api/reportes/ingresos?periodo=dia\|semana\|mes` | DUENO | RF-21 |
| Ingresos | `GET /api/reportes/mas-vendidos` | DUENO | RF-22 |
| Ingresos | `GET /api/reportes/ventas` | DUENO | RF-20 |

## Riesgos y mitigaciones

El riesgo mayor es el calendario: cuatro semanas para tres clientes. Lo que más protege el plan es congelar el contrato a tiempo y recortar extras antes que el núcleo.

| Riesgo | Señal temprana | Mitigación |
| --- | --- | --- |
| No alcanza el tiempo de desarrollo | Al 13/11 la app o el panel aún no inician sesión | Pistas paralelas; recortar primero SSE, QR y gráficos avanzados, nunca el flujo pedir → pagar → despachar |
| Lógica repartida entre triggers y Java | Totales en 0, stock que no baja, tests que pasan en local y fallan en CI | Testcontainers desde la Fase 1 y la sección "Lo que hace la BD" de `CLAUDE.md` |
| Código generado que el equipo no entiende | PRs grandes aprobados sin preguntas | PRs pequeños; cada autor explica su PR; pedir a Claude que justifique sus decisiones antes de aceptar |
| Enums y herencia fallan al arrancar | Error de tipo en el primer insert | Resolverlo en la Fase 1 con el test de humo |
| Firebase se retrasa | Sin token registrado al 30/11 | Notificaciones también en tabla y consultadas por la app al abrirse |
| Secretos en el repositorio | Claves en un diff | `.env` fuera de Git, `.env.example` sin valores reales, revisar los diffs antes de cada commit |
| Despliegue de última hora | Nada desplegado al 04/12 | Desplegar una versión temprana al cerrar la Fase 3 |
| Arranque en frío en la demo | La primera petición tarda varios segundos (Render dormido o Neon suspendido) | Abrir la app y el panel unos minutos antes de la demo para despertar la API y la base; si no alcanza, un plan pago de Render solo para el mes de la demo |
| Límites del plan gratuito | Neon o Render avisan de cuota agotada | Revisar el uso cada semana en sus paneles; los tests usan Testcontainers, no Neon, para no gastar cuota |

## Referencias

Del proyecto: `CONTEXTO_PROYECTO.md`, `01_gestion_cafetin_schema.sql`, `02_gestion_cafetin_datos.sql`, Análisis del problema, Diagrama de clases y Cronograma (GanttProject).

Claude Code:

- [Mapa de la documentación de Claude Code](https://code.claude.com/docs/en/claude_code_docs_map.md), consultado el 19/10/2026.
- Listadas en ese mapa, para profundizar: [CLAUDE.md y memoria del proyecto](https://code.claude.com/docs/en/memory.md), [Planificar antes de editar (flujos comunes)](https://code.claude.com/docs/en/common-workflows.md), [Buenas prácticas](https://code.claude.com/docs/en/best-practices.md), [Worktrees para sesiones en paralelo](https://code.claude.com/docs/en/worktrees.md).
