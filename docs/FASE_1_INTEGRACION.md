# Fase 1: integración funcional

Fecha de revisión: 23/10/2026. Implementación preparada y comprobada con pruebas aisladas; **la integración con MySQL real no está certificada**.

Actualización de la continuación: registro Angular, catálogos, balance por emergencia e identidad durable de recepciones implementados. **V2 está preparada y no aplicada.** Resultados actuales: 101 pruebas aisladas backend, 72 frontend y build Angular correctos; 4 pruebas MySQL omitidas por falta de entorno. El detalle y el inventario de esta continuación están al final. Las secciones originales conservan la auditoría y validaciones de la primera intervención.

## Auditoría del estado inicial

Se revisaron el estado y diffs de Git, V1, entidades, repositorios, servicios, controladores, DTO, rutas y pantallas existentes. Se conservaron los archivos nuevos previos y las eliminaciones de `config/.gitkeep` y `modules/.gitkeep`.

| Módulo | Estado encontrado | Alcance de esta intervención |
| --- | --- | --- |
| access | Autenticación, permisos y guardas existentes | Reutilizar `ACCESO_ADMINISTRAR` en los nuevos endpoints y ruta |
| audit | Consulta paginada y registro desde Emergencias/recepciones | Registrar creación de necesidades y cambios de asignación/entrega |
| emergencies | API y pantalla para emergencias y zonas | Enlazar cada zona con sus necesidades y entregas |
| resources | Catálogo, unidades, edición y activación; sin cálculo de existencias | Reutilizar recursos activos y su unidad, sin inventar inventario |
| donations | Registro por API, consulta, trazabilidad y recepción parcial/total; sin formulario Angular de registro | Consultar líneas recibidas compatibles y sus reservas |
| needs | Solo entidad y tabla | Registro por zona, consulta paginada y balances |
| deliveries | Solo entidades y tablas | API de totales acumulados, incrementos transaccionales, auditoría y formulario Angular |

No se detectaron errores de compilación en las comprobaciones ejecutadas. Las pruebas de autorización de los endpoints nuevos pasaron. Esto no sustituye la revisión transversal de seguridad de la fase 2.

## Relaciones y cantidades

- Emergencia → zonas afectadas → necesidades. Cada necesidad exige zona y recurso; la emergencia se obtiene de la zona. V1 no permite necesidades sin zona.
- Recurso → unidad de medida. No se convierten ni suman cantidades de recursos diferentes.
- Donación → líneas por recurso, con cantidad donada y cantidad recibida en acopio separadas.
- Detalle de entrega → entrega, línea de donación y necesidad. Esta relación de V1 ya permite vincular donaciones y necesidades; no hace falta otra tabla.
- Entrega → usuario responsable y fecha de base de datos. Auditoría conserva actor, vínculo, totales anteriores/nuevos e incrementos.

Las consultas suman `detalle_entrega.cantidad_asignada` y `cantidad_entregada` por separado. Las uniones no multiplican cantidades recibidas.

| Dato | Cálculo / significado |
| --- | --- |
| Requerida | `necesidad.cantidad_requerida` |
| Recibida | `detalle_donacion.cantidad_recibida_acopio`, por línea; nunca la cantidad prometida |
| Asignada a una necesidad | Suma de incrementos asignados a esa necesidad |
| Entregada a una necesidad | Suma de incrementos entregados a esa necesidad |
| Pendiente de asignar | Máximo entre cero y requerida menos asignada |
| Pendiente de entregar | Máximo entre cero y requerida menos entregada |
| Disponible por línea | Máximo entre cero y recibida menos asignada a todas las necesidades, solo si el estado permite operar |

La disponibilidad es compartida: verla desde varias necesidades no crea existencias adicionales. Una entrega posterior consume la reserva existente: no vuelve a restarse de disponibilidad. Los valores pendientes no equivalen a un déficit global de abastecimiento de toda la emergencia.

Solo se permiten donaciones del mismo recurso y emergencia. Se incluyen recepciones parciales en estado `REGISTRADA`, además de `RECIBIDA`, `ALMACENADA` y `CLASIFICADA`. `EN_TRANSITO`, `ENTREGADA`, `DEVUELTA` e `INCIDENCIA` quedan bloqueados. Una donación exclusiva de centro de acopio no se reasigna automáticamente a una emergencia. Los estados generales de donación y necesidad no se derivan automáticamente de entregas parciales.

## Contratos añadidos

Todos requieren `ACCESO_ADMINISTRAR`; las listas tienen páginas de 20 filas.

- `GET /api/admin/emergencias/{emergencyId}/zonas/{zoneId}/necesidades?page=0`
- `POST /api/admin/emergencias/{emergencyId}/zonas/{zoneId}/necesidades`: `recursoId`, `cantidad` positiva DECIMAL(14,3), `prioridad` y `observaciones` opcionales.
- `GET /api/admin/necesidades/{needId}/donaciones?page=0`
- `PUT /api/admin/necesidades/{needId}/donaciones/{sourceId}/entrega`: `totalAsignado` y `totalEntregado`, ambos **totales acumulados para esa pareja**.
- Ruta Angular: `/admin/emergencias/:id/zonas/:zoneId/necesidades`, accesible desde la zona en Emergencias.

El PUT calcula los incrementos contra los totales persistidos. Repetir exactamente los mismos totales no crea otra entrega, detalle ni auditoría. No se admiten disminuciones; un reintento obsoleto puede devolver 409 si los totales ya avanzaron. Esta semántica expresa un objetivo acumulado, no una nueva operación aditiva por cada envío.

Se bloquea primero la donación (el mismo bloqueo usado por recepción) y luego la necesidad. Bajo `READ_COMMITTED`, se refresca la línea y se consultan los acumulados después de adquirir los bloqueos. Así se han diseñado los límites compartidos entre necesidades y donaciones; faltan pruebas de concurrencia y rollback en MySQL para certificarlos. La auditoría se escribe en la misma transacción. No se añadieron ni ejecutaron migraciones.

## Archivos de esta intervención

Modificados:

- `backend/src/main/java/pe/redsolidaria/modules/needs/domain/Need.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/domain/Delivery.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/domain/DeliveryLine.java`
- `backend/src/main/java/pe/redsolidaria/modules/donations/persistence/DonationLineRepository.java`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/emergencies/emergency-management-page.component.ts`
- `frontend/src/app/emergencies/emergency-management-page.component.html`

Creados:

- `backend/src/main/java/pe/redsolidaria/modules/needs/api/NeedManagementController.java`
- `backend/src/main/java/pe/redsolidaria/modules/needs/api/dto/NeedDtos.java`
- `backend/src/main/java/pe/redsolidaria/modules/needs/application/NeedManagementService.java`
- `backend/src/main/java/pe/redsolidaria/modules/needs/persistence/NeedRepository.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/api/DeliveryManagementController.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/api/dto/DeliveryDtos.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/application/DeliveryManagementService.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/persistence/DeliveryRepository.java`
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/persistence/DeliveryLineRepository.java`
- `backend/src/test/java/pe/redsolidaria/modules/needs/NeedManagementServiceTest.java`
- `backend/src/test/java/pe/redsolidaria/modules/needs/NeedManagementWebTest.java`
- `backend/src/test/java/pe/redsolidaria/modules/needs/PhaseOneReadOnlyMySqlIntegrationTest.java`
- `backend/src/test/java/pe/redsolidaria/modules/deliveries/DeliveryManagementServiceTest.java`
- `frontend/src/app/needs/need.model.ts`
- `frontend/src/app/needs/need-api.service.ts`
- `frontend/src/app/needs/need-management-page.component.ts`
- `frontend/src/app/needs/need-management-page.component.html`
- `frontend/src/app/needs/need-management-page.component.css`
- `frontend/src/app/needs/need-management-page.component.spec.ts`
- `docs/FASE_1_INTEGRACION.md`

## Verificación

- Backend: `./mvnw.cmd '-Dtest=*,!RedSolidariaApplicationTests,!AccessRepositoryIntegrationTest,!*MySql*' test`: **95 pruebas, 0 fallos, 0 errores, 0 omitidas**. Se excluyeron explícitamente las pruebas que conectan con MySQL. Registro local: `backend/target/phase1-tests.log`.
- MySQL de solo lectura: `./mvnw.cmd '-Dtest=PhaseOneReadOnlyMySqlIntegrationTest' test`: **1 prueba omitida, 0 fallos, 0 errores**, porque no hay `DB_USERNAME` configurado. Tampoco hay `DB_PASSWORD` configurado. Registro local: `backend/target/phase1-mysql-readonly.log`.
- Esa prueba, cuando haya entorno local autorizado, usa `spring.flyway.enabled=false`, `ddl-auto=validate`, transacción de lectura y SELECT sobre historial Flyway y consultas de balances. No crea datos de prueba. No verifica escrituras ni concurrencia.
- Frontend: `npm test -- --watch=false`: **64 pruebas aprobadas en 18 archivos**.
- Angular: `npm run build`: **correcto**, incluida la nueva ruta y plantillas.
- `git diff --check`: **sin errores**.

## Pendientes para cerrar fase 1 y decisiones

1. Verificar esquema realmente desplegado, historial Flyway y consultas JPA contra MySQL. Solo se pudo inspeccionar V1 y los mapeos del repositorio.
2. Ejecutar el flujo completo desde navegador con backend y MySQL, sin usar ni modificar los datos actuales del usuario. Las pruebas de escritura, bloqueo, reintentos simultáneos y rollback necesitan una base de pruebas separada y autorizada.
3. Resuelto en código durante la continuación: registro Angular y búsqueda paginada de donantes, centros y emergencias, más altas administrativas de donantes, centros y unidades. Pendiente validación real.
4. Resuelto en código durante la continuación: balance agregado por recurso/emergencia con unidad explícita y faltante de abastecimiento. Pendiente verificar el SQL contra MySQL.
5. Definir edición/cancelación de necesidades, reversos de asignación/entrega y ciclo de estados. Actualmente solo se permite crear necesidades y avanzar totales; no se corrige historia mediante disminuciones.
6. Definir la reasignación de donaciones exclusivas de centro a emergencias. No se realiza sin una regla funcional explícita.
7. Riesgo previo: Recursos permite cambiar la unidad aunque existan cantidades históricas. Debe acordarse bloquear ese cambio o conservar la unidad histórica; no se hacen conversiones automáticas.
8. Resuelto en código durante la continuación: el POST de recepciones exige ahora `operacionId` UUID, con huella y respuesta durable en V2. Se requiere aplicar V2 en el entorno autorizado y actualizar clientes externos; no se aplicó ninguna migración aquí.
9. La fase 2 debe ampliar auditoría de operaciones preexistentes, revisar autenticación/CSRF y validar permisos y transiciones de forma transversal. Configuración del sistema sigue reservada a fase 3.

La primera intervención no requería esquema nuevo. La continuación añade V2 por la idempotencia durable, justificada abajo. Las reglas de reversos, reasignación y unidades históricas requieren una decisión funcional antes de ampliar esos comportamientos. La fase 1 permanece abierta.

## Continuación: funcionalidades implementadas

- `/donaciones/registro`, con guarda `DONACION_REGISTRAR`, usa el POST existente `/api/donaciones`. Valida donante, destino, recursos activos únicos, cantidades positivas DECIMAL(14,3) y límites de texto; muestra código y confirmación. Un registro no confirmado por fallo de red/servidor bloquea el reenvío en esa instancia de pantalla y pide verificar el listado. El registro de donaciones todavía no tiene idempotencia durable de servidor.
- `/admin/catalogos-donaciones`, con la misma pantalla reutilizada, permite al administrador crear donantes, centros y unidades. No concede `DONACION_REGISTRAR` al administrador: si su cuenta no tiene ese permiso, no puede registrar donaciones. La creación de catálogos queda auditada.
- `GET /api/donaciones/catalogos/{donantes|centros|emergencias}?texto=&page=0` devuelve páginas de 20 elementos, accesibles con `DONACION_REGISTRAR` o `ACCESO_ADMINISTRAR`. Solo expone ID, nombre y ubicación aplicable. Las altas usan `POST /api/admin/catalogos-donaciones/{donantes|centros|unidades}`. Se reutilizan recursos y lectura de unidades existentes, sin otro catálogo de recursos.
- `GET /api/admin/emergencias/{id}/balance` y componente Angular dentro de la ficha de emergencia. Agrega primero necesidades, reservas, recepciones y eventos por separado, para evitar multiplicaciones por joins. Cada fila corresponde a un recurso y su unidad; no suma recursos diferentes. Devuelve requerida, recibida histórica en acopio, asignada, entregada, disponible habilitada, pendiente de asignar, pendiente de entregar y faltante de abastecimiento.
- Faltante de abastecimiento = máximo entre cero y pendiente de asignar menos disponible. La disponibilidad ya excluye reservas; al entregar una reserva no se vuelve a descontar. Las necesidades registradas se incluyen sin deducir cancelaciones inexistentes o estados no definidos.
- Las donaciones nuevas no reciben fecha de ingreso al registrarse. La primera recepción efectiva establece esa fecha si aún no existe. No se corrigieron retrospectivamente fechas ni registros reales.

## V2 e idempotencia de recepciones

V1 tiene incrementos y auditorías, pero no una identidad única de solicitud ni respuesta recuperable. Por eso se añade, sin editar V1, `V2__idempotencia_recepciones.sql`, que crea solamente `recepcion_operacion`: ID, donación, UUID, SHA-256 canónico, respuesta JSON y fecha. La clave única es `(id_donacion, operacion_id)`; conserva FK restrictiva y no modifica filas anteriores.

El POST existente de recepción exige `operacionId` UUID. La huella incluye responsable, ubicación/observaciones normalizadas y recursos ordenados con cantidades normalizadas, con serialización determinista. Bajo `READ_COMMITTED`, el servicio bloquea la donación antes de consultar la clave. Si coincide, devuelve la respuesta original sin volver a recibir, crear movimiento o auditar, incluso si la donación ahora está completa. La misma clave con otro contenido/responsable devuelve 409. Otro UUID permite una recepción posterior legítima. Recepción, movimiento, auditoría e identidad se escriben en una misma transacción.

Angular guarda la solicitud pendiente en `sessionStorage`, separada por cuenta y donación, antes de enviarla; conserva UUID y contenido ante fallos sin confirmar, incluso al recrear la página en esa pestaña. No guarda contraseñas. Borra el pendiente tras confirmación o error 400 que no escribió. No permite editar una operación pendiente para reutilizar su identidad con otro contenido. Cerrar la pestaña/eliminar el almacenamiento puede perder la recuperación del cliente; no debe inventarse otra clave para repetir una recepción incierta.

V2 **no fue aplicada**. El arranque normal mantiene Flyway habilitado: debe revisarse y planificarse su aplicación antes de arrancar contra un entorno real. Otros clientes del POST de recepciones deberán enviar UUID estable por operación; omitirlo devuelve 400.

## Validación actual y entorno faltante

| Comprobación | Resultado |
| --- | --- |
| Backend, unitarias y MockMvc, sin MySQL | 101 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Frontend con HttpTestingController | 72 pruebas aprobadas en 20 archivos |
| `npm run build` | Correcto |
| `git diff --check` | Sin errores |
| MySQL SELECT-only | 1 omitida |
| MySQL controlado: persistencia/idempotencia, rollback y concurrencia | 3 omitidas |

Comandos backend: `./mvnw.cmd '-Dtest=*,!RedSolidariaApplicationTests,!AccessRepositoryIntegrationTest,!*MySql*' test` y `./mvnw.cmd '-Dtest=PhaseOne*MySqlIntegrationTest' test`. Registros locales: `backend/target/phase1-followup-tests.log` y `backend/target/phase1-followup-mysql.log`. Frontend: `npm test -- --watch=false`.

No están configuradas `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TEST_DB_URL`, `TEST_DB_USERNAME` ni `TEST_DB_PASSWORD`. No se consultó el esquema desplegado ni se abrió una conexión a MySQL. Se revisaron configuración, V1, V2 y mapeos locales.

Para habilitar las pruebas de escritura se necesita una base **exclusiva de pruebas**, previamente preparada con V1 y V2 y su historial Flyway correcto; URL local `jdbc:mysql://localhost:3306/red_solidaria_test` (también admite 127.0.0.1 y otro puerto, sin opciones de URL), `TEST_DB_USERNAME` y `TEST_DB_PASSWORD` del usuario restringido a esa base. No se usan credenciales predeterminadas ni fallback a `DB_*`. No enviar contraseñas al chat ni versionarlas. Las pruebas desactivan Flyway y validan el esquema; comprueban `database()` e historial antes de escribir. Crean sus propios registros sintéticos con identificadores únicos, sin borrar tablas ni datos; esos registros quedan en la base de pruebas.

Las tres pruebas controladas preparadas comprueban: reintentos concurrentes de una recepción, una recepción legítima posterior y rechazo de cambio de contenido; rollback al fallar auditoría; competencia entre dos necesidades por existencias de una donación, entrega posterior de reservas y balance agregado. **No fueron ejecutadas contra un motor real**. También faltan recorridos de navegador con Angular, Spring Boot y MySQL conectados; las pruebas frontend usan HTTP simulado.

## Inventario de archivos de la continuación

Modificados en backend:

- `modules/donations/api/dto/DonationReceiptDtos.java`
- `modules/donations/application/DonationReceiptService.java`, `DonationRegistrationService.java`
- `modules/donations/domain/Donation.java`, `Donor.java`
- `modules/donations/persistence/DonorRepository.java`
- `modules/resources/api/dto/ResourceDtos.java`
- `modules/resources/domain/CollectionCenter.java`, `MeasurementUnit.java`
- `modules/resources/persistence/CollectionCenterRepository.java`

Las rutas anteriores son relativas a `backend/src/main/java/pe/redsolidaria/`.

Creados en backend (misma raíz Java):

- `modules/donations/api/DonationCatalogController.java`, `api/dto/DonationCatalogDtos.java`
- `modules/donations/application/DonationCatalogService.java`
- `modules/donations/domain/ReceiptOperation.java`, `persistence/ReceiptOperationRepository.java`
- `modules/emergencies/api/EmergencyBalanceController.java`, `api/dto/EmergencyBalanceDtos.java`
- `modules/emergencies/persistence/EmergencyBalanceRepository.java`
- Fuera de la raíz Java: `backend/src/main/resources/db/migration/V2__idempotencia_recepciones.sql`

Pruebas backend modificadas: `DonationReceiptServiceTest`, `DonationReceiptAuthorizationWebTest`, `DonationRegistrationServiceTest` en `modules/donations`, y `PhaseOneReadOnlyMySqlIntegrationTest` en `modules/needs`. Creadas: `modules/donations/DonationCatalogWebTest.java`, `modules/donations/PhaseOneControlledMySqlIntegrationTest.java` y `test/ControlledTestMySqlCondition.java`, relativas a `backend/src/test/java/pe/redsolidaria/`.

Frontend modificado, relativo a `frontend/src/app/`:

- `app.routes.ts`, `auth/auth.guards.ts`
- `shared/app-shell.component.ts`, `.html`
- `donations/donation-admin.model.ts`, `donation-admin.service.ts`, `donation-admin.service.spec.ts`
- `donations/donation-administration-detail-page.component.ts`, `.html`, `.spec.ts`
- `emergencies/emergency-management-page.component.ts`, `.html`, `.spec.ts`

Frontend creado: `donations/donation-registration-page.component.ts`, `.html`, `.css`, `.spec.ts` y `emergencies/emergency-balance.component.ts`, `.spec.ts`. Documento actualizado: `docs/FASE_1_INTEGRACION.md`.

## Riesgos y criterios de cierre aún abiertos

- Verificar y aplicar V2 únicamente en el entorno autorizado, actualizar clientes externos y validar JPA/SQL, transacciones, bloqueos y recuperación tras reinicio contra MySQL real.
- Completar E2E de navegador con cuentas de permisos distintos y datos exclusivamente de prueba.
- El registro inicial de donaciones todavía no tiene idempotencia durable: ante respuesta incierta debe conciliarse con el listado, no reenviar sin verificar. La pantalla bloquea el reintento durante esa sesión de componente.
- Recursos conserva la posibilidad previa de cambiar su unidad: sigue pendiente acordar bloqueo o unidad histórica. Esta intervención solo permite crear unidades nuevas, sin convertir ni reescribir cantidades históricas.
- Reasignaciones desde centros, reversos y reglas de edición/cancelación o cierre de necesidades siguen pendientes de definición funcional. No se implementaron esas operaciones ni se modificaron estados generales automáticamente.
- No se hicieron commits, push, restauraciones, cambios de datos reales ni ejecución de migraciones. Permanecen las dos eliminaciones de `.gitkeep` y todo el trabajo previo. La fase 1 sigue abierta; no se avanzó a otras fases.
