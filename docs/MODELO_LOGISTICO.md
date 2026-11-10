# Modelo logístico y trazabilidad de Red Solidaria

Revisión: 09/11/2026. Implementación local; **no certificada contra MySQL real**. No se ejecutaron migraciones, commits ni push. No se modificaron datos reales.

## A. Auditoría y diagnóstico

Se revisaron las entidades/relaciones, DTO, repositorios, transacciones, servicios y controladores de `donations`, `deliveries`, `needs`, `emergencies`, `resources`, `audit` y `access`; SecurityConfiguration, rutas/guards, servicios/pantallas Angular, V1/V2 y `FASE_1_INTEGRACION.md`. No se encontró AGENTS.md en el árbol del proyecto. Había cambios locales previos en los tres archivos del dashboard administrativo y en HTML/spec del shell; se conservaron. El shell y su expectativa de navegación reciben además un enlace logístico.

| Módulo | Funcionalidad existente que se conserva | Adaptación |
| --- | --- | --- |
| donations | Registro, consulta privada/pública, recepción parcial, movimientos, UUID/huella/respuesta durable V2 | Identificar acopio al recibir; historial paginado de operaciones V2; enlaces a logística |
| resources | Recursos, unidades, altas/consulta de acopios reutilizadas desde catálogos | Centro de distribución separado; impedir cambiar unidad con historia |
| needs | Necesidades por zona, prioridad/observaciones, cantidades requeridas/asignadas/entregadas y pendientes | Mantener CRUD existente; disponibilidad procede de distribución |
| deliveries | PUT de totales acumulados por necesidad y lote, bloqueo donación → necesidad, auditoría | Nuevas reservas solo sobre recepción confirmada en distribución; movimiento de asignación/entrega final |
| emergencies | Emergencias, zonas, navegación a necesidades y balance calculado | Balance añade traslado, tránsito, recepción en distribución y diferencias |
| access | Basic Auth, permisos y guards independientes para registrar/administrar | Logística requiere ACCESO_ADMINISTRAR también en backend |
| audit | Registro transaccional y consulta administrativa | Altas/edición de distribución, salida y recepción de traslados |

Antes de esta adaptación se podía reservar directamente `cantidad_recibida_acopio`; no existía evidencia de ingreso a distribución. Donación registrada, recepción, reserva y entrega ya eran conceptos separados; no se duplican esos módulos.

### Estado real de MySQL

Se comprobó exclusivamente la presencia de variables de entorno, sin mostrar sus valores: DB_URL, DB_USERNAME, DB_PASSWORD, TEST_DB_URL, TEST_DB_USERNAME y TEST_DB_PASSWORD no estaban configuradas. No se abrió conexión a MySQL. El estado desplegado de V1 y V2 es **desconocido**; las notas históricas del proyecto no sustituyen `flyway_schema_history`.

Consulta de solo lectura pendiente en un entorno con credenciales autorizadas:

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM flyway_schema_history ORDER BY installed_rank;
```

## B. Instalaciones y modelo

- Se conserva `centro_acopio` y su relación con donación. Puede haber varios acopios.
- `centro_distribucion` es una tabla separada, sin reutilizar acopios como destinos. No se crea ningún centro con datos inventados. El administrador registra el principal; el esquema admite otros.
- La recepción inicial puede identificar `centroAcopioId` si la donación se registró solo para una emergencia. Debe existir el acopio. No permite cambiar de acopio ni atribuir retrospectivamente existencias antiguas sin ubicación.
- Una operación de traslado transporta **un lote/recurso de una línea de donación**. Un despacho con varios recursos se registra como varias operaciones identificables. No existe todavía una cabecera de transporte que las agrupe atómicamente.
- Cada traslado conserva acopio, distribución, unidad, lote original, cantidades, responsable de cada etapa, fechas del servidor, observaciones, estado, UUID y huellas. La línea permite seguir el origen hasta donación y donante.

## C. Traslado, inventario e idempotencia

Estados: EN_TRANSITO → RECIBIDO o RECIBIDO_CON_DIFERENCIA. La recepción se confirma una vez; no se infiere de un cambio de estado general de la donación.

| Cantidad por lote | Cálculo |
| --- | --- |
| Recibida en acopio | Acumulado existente de recepciones físicas |
| Trasladada | Suma de cantidades enviadas, incluidas las ya recibidas |
| Libre en acopio | Máximo(0, recibida acopio − trasladada − reservas históricas) |
| En tránsito | Enviada de operaciones todavía EN_TRANSITO |
| Recibida distribución | Cantidad efectivamente confirmada en cada traslado |
| Libre distribución | Máximo(0, recibida distribución − nuevas reservas) |
| Diferencia | Enviada − recibida de traslados confirmados |
| Asignada / entregada | Sumas independientes de incrementos de detalle_entrega |

La salida y la recepción toman el mismo bloqueo de donación que recepción en acopio y entregas, con aislamiento READ_COMMITTED. Así dos traslados o reservas de un lote compiten por el mismo saldo. No hay un contador duplicado de existencias: los saldos se derivan de las operaciones persistidas. Auditoría, movimiento e identidad se escriben en la misma transacción JPA/JDBC y DataSource.

El UUID de salida es clave primaria global durable. La huella incluye actor, lote, destino, cantidad decimal normalizada y observaciones. El reintento devuelve el traslado persistido sin repetir salida/auditoría; si el traslado avanzó, devuelve su **estado actual**. La recepción conserva su propio UUID y huella y permite repetir exactamente esa confirmación. Reutilizar identidad con otro contenido/responsable devuelve 409. Un conflicto concurrente de clave primaria revierte la transacción; el reintento recupera la operación correspondiente.

Angular guarda UUID y contenido en sessionStorage por cuenta antes de enviar y los recupera al recrear la pantalla. Ante red/5xx conserva la operación y bloquea nuevas operaciones hasta reintentar. No almacena contraseñas. Una respuesta definitiva 400/404/409 libera la solicitud rechazada. Cerrar la pestaña o borrar sessionStorage puede perder el pendiente: se debe revisar el historial antes de repetir una operación incierta.

Recepción menor que envío exige observaciones, registra diferencia y solo habilita lo recibido. Cero recibido se admite con explicación. Nunca se aceptan cantidades superiores a las enviadas. No se devuelve automáticamente la diferencia a acopio; reversos y conciliaciones requieren una regla aprobada.

## D. Necesidades

Se reutilizan las rutas y servicios existentes. Para una necesidad de arroz: seleccionar recurso con unidad KG, cantidad 100, prioridad y observaciones opcionales. La unidad procede del catálogo; no se suman ni convierten recursos/unidades diferentes.

La zona determina la emergencia. Se conserva la compatibilidad existente de recurso y emergencia: las donaciones sin emergencia no se asignan automáticamente a una zona.

## E. Asignaciones, entregas y balances

El PUT existente sigue recibiendo **totales acumulados**, calcula incrementos, rechaza disminuciones y no duplica eventos si se repiten los mismos totales. Las nuevas reservas se limitan al ingreso confirmado en distribución. Entregar después una reserva no vuelve a reservarla. Las asignaciones y entregas quedan como incrementos separados y se registran también en los movimientos administrativos de la donación.

V3 añade `origen_distribucion` a detalle_entrega. Las filas anteriores quedan false, sin tocar sus cantidades ni fabricar traslados. Las nuevas líneas de servicio son true. Las reservas antiguas siguen consumiendo acopio; se permite avanzar su entrega final para conservar el flujo previo, pero cualquier incremento nuevo de reserva exige recepción en distribución. No se debe reingresar ni trasladar la cantidad histórica ya reservada.

El balance agrega cada relación antes de unirla para no multiplicar cantidades por las zonas o entregas. Requerida, recibida acopio, trasladada, en tránsito, recibida distribución, diferencia, disponible distribución, asignada, entregada, sin asignar, pendiente de entregar y faltante se consultan por recurso/unidad. Faltante = máximo(0, requerida − asignada − disponible distribución). Pendiente de entregar = máximo(0, requerida − entregada). Los campos calculados no tienen endpoints de edición.

Los cambios de unidad de un recurso con donaciones/necesidades se rechazan en el servicio y mediante trigger MySQL. Sin historia se conserva la edición existente. No se hace conversión de unidades.

## F. API y navegación

Se conservan los endpoints de necesidades, donaciones, emergencias y entregas documentados en FASE_1_INTEGRACION.md. Nuevos contratos:

| Método / ruta | Uso |
| --- | --- |
| GET /api/admin/logistica/centros-distribucion | Consulta centros |
| POST /api/admin/logistica/centros-distribucion | Alta: nombre, ubicacion, descripcion |
| PUT /api/admin/logistica/centros-distribucion/{id} | Edición de datos del centro |
| GET /api/admin/logistica/existencias?page=0 | Saldos por lote (20 filas) |
| GET /api/admin/logistica/traslados?page=0 | Historial de traslados (20 filas) |
| POST /api/admin/logistica/traslados | Salida: operacionId UUID, detalleDonacionId, centroDistribucionId, cantidad, observaciones |
| POST /api/admin/logistica/traslados/{uuid}/recepcion | Recepción: operacionId UUID propio, cantidad, observaciones |
| GET /api/donaciones/administracion/{id}/recepciones?page=0 | Historial de recepciones V2, 20 operaciones; contenido original de respuesta |

Cambios de contratos existentes:

- POST de recepción añade `centroAcopioId` opcional; obligatorio funcionalmente cuando no se identificó acopio y no hay recepciones anteriores. Se mantiene la huella V2 anterior cuando se omite el nuevo campo, para recuperar reintentos previos.
- GET de balance añade `trasladada`, `recibidaDistribucion`, `enTransito`, `diferencia`; `disponible` ahora representa distribución.
- La consulta de fuentes para necesidades mantiene `recibidaAcopio` histórico y cambia `disponible` a distribución.

Todos estos endpoints son administrativos. Angular incorpora `/admin/logistica` con administratorGuard, navegación lateral, pestaña de centros de distribución en catálogos y enlaces desde el detalle de donación. El historial de recepciones se carga mediante su botón; el historial general conserva los eventos anteriores a V2.

## Migraciones

V1 y V2 se conservaron sin cambios. **V3__logistica_distribucion.sql está preparada y NO aplicada**. Crea centros/traslados, distingue nuevas reservas y protege unidades históricas. No contiene credenciales ni datos semilla. Requiere MySQL compatible con el proyecto y permiso CREATE TRIGGER en el usuario de migración. No arrancar el backend normal contra desarrollo sin autorización: application.properties mantiene Flyway habilitado y podría ejecutar V2/V3 pendientes.

MySQL tiene DDL con commits implícitos. Revisar y respaldar el entorno antes de aplicar la migración en una ventana autorizada; si un paso falla, no asumir que todo el DDL se revirtió ni editar una migración ya registrada.

## Validación

Los resultados exactos y el inventario de archivos figuran al final de este documento. Las pruebas aisladas usan mocks/HTTP simulado: no prueban el motor SQL, el trigger, bloqueo real ni rollback real.

Se ampliaron las pruebas controladas MySQL para preparar su propio acopio/distribución, bloquear asignación antes de recepción, competir entre necesidades, repetir salidas/recepciones simultáneas, recibir con diferencia, entregar parcialmente y verificar rollback por fallo de auditoría. Solo apuntan a red_solidaria_test y tienen Flyway desactivado. Requieren V1/V2/V3 **previamente aplicadas con autorización**. No se habilitan con DB_* de desarrollo.

## Riesgos y decisiones pendientes

1. Falta ejecutar pruebas contra MySQL y recorrido Angular → Spring → MySQL en navegador. No se certifican SQL, trigger, concurrencia ni rollback reales sin ellas.
2. Conciliar donaciones históricas sin acopio y reservas antiguas mediante un procedimiento acordado; esta implementación no modifica esos datos.
3. Las reservas existentes identifican lote, no centro de distribución. Como salvaguarda, un lote no puede enviarse a dos destinos distintos; otros lotes pueden ir a otros centros. La división del mismo lote entre centros requiere ampliar la identidad de las reservas y confirmar el contrato. No se crea una segunda implementación de entregas para eludirlo.
4. Falta definir devolución, cancelación, diferencias por excedentes, correcciones de recepción, reasignación entre emergencias y cierre de incidencias. Se conserva la evidencia y se rechazan operaciones ambiguas.
5. Las entregas son a la población de la zona/necesidad, como en el modelo anterior. No existe todavía padrón nominal, firma de beneficiario ni constancia individual; requieren definir datos y privacidad antes de modelarlos.
6. El alta de catálogos conserva la semántica previa sin UUID; ante respuesta incierta se debe revisar el listado. El UUID durable sí cubre recepción, salida y recepción en distribución; las entregas conservan objetivos acumulados durables.
7. Cada salida abarca un recurso/lote. Falta agrupación de varios recursos bajo un vehículo/manifiesto y reserva localizada para dividir un lote entre centros.
8. Las existencias muestran saldos físicos por lote; un estado bloqueado de donación sigue impidiendo operar en backend. El balance de emergencia excluye disponibilidad en estados no habilitados.
9. La trazabilidad administrativa incluye traslados, cantidades y entregas. La consulta pública mantiene su contrato y privacidad previos: no expone responsables, huellas ni observaciones privadas.

## Pasos de ejecución y prueba

1. Revisar diff y esta documentación. Consultar flyway_schema_history en modo lectura con credenciales del entorno; no enviarlas al chat.
2. Preparar una base local **exclusiva de pruebas** red_solidaria_test con usuario restringido. Autorizar por separado la aplicación de V1/V2/V3 allí. No se preparó ni alteró esa base durante esta intervención.
3. Proporcionar TEST_DB_URL=jdbc:mysql://localhost:3306/red_solidaria_test, TEST_DB_USERNAME y TEST_DB_PASSWORD en el entorno; ejecutar las pruebas controladas indicadas al final. No usar valores de desarrollo como fallback.
4. Solo después de revisar/aprobar el despliegue de esquema de desarrollo, configurar DB_* y arrancar backend. Para una comprobación previa sin aplicar esquema, usar spring.flyway.enabled=false; Hibernate seguirá validando el esquema.
5. Arrancar frontend con npm start y el proxy existente. Usar cuentas con permisos previos, sin modificar roles automáticamente.
6. Registrar un acopio y el centro de distribución principal; crear recurso Arroz/KG y emergencia/zona; registrar necesidad 100 KG.
7. Registrar donación para emergencia y acopio; recibir físicamente; consultar recepciones. Trasladar parte del lote: verificar reducción de acopio y disponibilidad cero en distribución hasta confirmar recepción.
8. Confirmar recepción (si hay diferencia, documentarla); reservar para la necesidad con totalEntregado=0; guardar una entrega parcial aumentando solo totalEntregado. Consultar balance e historial. Reenviar las mismas identidades/totales para comprobar que no crecen los movimientos.

## Resultados exactos de esta revisión

| Comprobación | Resultado |
| --- | --- |
| Backend unitario y MockMvc, sin MySQL | 113 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Frontend Vitest/HTTP simulado | 103 aprobadas, 0 fallidas, 21 archivos |
| Producción Angular | Correcta; advertencia existente de CSS de registro: 4,68 kB frente a presupuesto de 4 kB (+682 bytes) |
| MySQL protegido (solo lectura + entorno de pruebas) | 7 pruebas descubiertas, 7 omitidas; 0 fallos, 0 errores. Sin credenciales |
| Contratos API | MockMvc verifica autorización, UUID y cantidades de logística; pruebas existentes verifican necesidades/entregas y catálogos; Angular verifica solicitudes y recuperación de UUID |
| git diff --check | Sin errores de whitespace |

Comandos ejecutados (desde backend o frontend respectivamente):

```powershell
.\mvnw.cmd '-Dtest=*,!RedSolidariaApplicationTests,!AccessRepositoryIntegrationTest,!*MySql*' test
.\mvnw.cmd '-Dtest=PhaseOne*MySqlIntegrationTest' test
npm test -- --watch=false
npm run build
git diff --check
```

Registros locales ignorados por Git: backend/target/logistics-tests.log, backend/target/logistics-mysql.log, frontend/logistics-frontend-tests.log y frontend/logistics-build.log. El primer intento Angular falló por spawn EPERM del aislamiento; se ejecutó correctamente con autorización fuera de ese aislamiento. Maven también requirió acceso autorizado a su caché. Ninguno de estos comandos ejecutó migraciones en desarrollo.

No se ejecutaron las otras suites que escriben en MySQL de desarrollo (por ejemplo rollback/bootstrap/repositorios), ni se arrancó la aplicación contra esa base. Los siete skips no son resultados satisfactorios de integración SQL.

## Inventario de archivos

Los tres archivos admin-dashboard-page.component.{html,ts,spec.ts} ya tenían cambios al comenzar y **no se editaron en esta intervención**. shared/app-shell.component.{html,spec.ts} también tenían cambios previos, conservados al integrar el nuevo enlace. El inventario siguiente incluye los archivos modificados por esta intervención y los nuevos; las rutas de pruebas y producción se distinguen por su ubicación.

- `README.md` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/application/DeliveryManagementService.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/domain/DeliveryLine.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/deliveries/persistence/DeliveryLineRepository.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/donations/api/DonationReceiptController.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/donations/api/dto/DonationReceiptDtos.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/donations/application/DonationReceiptService.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/donations/domain/Donation.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/donations/persistence/ReceiptOperationRepository.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/emergencies/api/dto/EmergencyBalanceDtos.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/emergencies/persistence/EmergencyBalanceRepository.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/logistics/LogisticsController.java` (nuevo)
- `backend/src/main/java/pe/redsolidaria/modules/logistics/LogisticsDtos.java` (nuevo)
- `backend/src/main/java/pe/redsolidaria/modules/logistics/LogisticsRepository.java` (nuevo)
- `backend/src/main/java/pe/redsolidaria/modules/logistics/LogisticsService.java` (nuevo)
- `backend/src/main/java/pe/redsolidaria/modules/needs/application/NeedManagementService.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/resources/application/ResourceManagementService.java` (modificado)
- `backend/src/main/java/pe/redsolidaria/modules/resources/persistence/ResourceRepository.java` (modificado)
- `backend/src/main/resources/db/migration/V3__logistica_distribucion.sql` (nuevo)
- `backend/src/test/java/pe/redsolidaria/modules/deliveries/DeliveryManagementServiceTest.java` (modificado)
- `backend/src/test/java/pe/redsolidaria/modules/donations/DonationReceiptServiceTest.java` (modificado)
- `backend/src/test/java/pe/redsolidaria/modules/donations/PhaseOneControlledMySqlIntegrationTest.java` (modificado)
- `backend/src/test/java/pe/redsolidaria/modules/logistics/LogisticsAuthorizationWebTest.java` (nuevo)
- `backend/src/test/java/pe/redsolidaria/modules/logistics/LogisticsServiceTest.java` (nuevo)
- `backend/src/test/java/pe/redsolidaria/modules/needs/NeedManagementServiceTest.java` (modificado)
- `backend/src/test/java/pe/redsolidaria/modules/needs/PhaseOneReadOnlyMySqlIntegrationTest.java` (modificado)
- `backend/src/test/java/pe/redsolidaria/modules/resources/ResourceManagementServiceTest.java` (modificado)
- `docs/FASE_1_INTEGRACION.md` (modificado)
- `docs/MODELO_LOGISTICO.md` (nuevo)
- `frontend/src/app/app.routes.ts` (modificado)
- `frontend/src/app/donations/donation-admin.model.ts` (modificado)
- `frontend/src/app/donations/donation-admin.service.ts` (modificado)
- `frontend/src/app/donations/donation-administration-detail-page.component.html` (modificado)
- `frontend/src/app/donations/donation-administration-detail-page.component.spec.ts` (modificado)
- `frontend/src/app/donations/donation-administration-detail-page.component.ts` (modificado)
- `frontend/src/app/donations/donation-registration-page.component.html` (modificado)
- `frontend/src/app/donations/donation-registration-page.component.spec.ts` (modificado)
- `frontend/src/app/donations/donation-registration-page.component.ts` (modificado)
- `frontend/src/app/emergencies/emergency-balance.component.ts` (modificado)
- `frontend/src/app/logistics/distribution-centers.component.ts` (nuevo)
- `frontend/src/app/logistics/logistics-api.service.ts` (nuevo)
- `frontend/src/app/logistics/logistics-page.component.html` (nuevo)
- `frontend/src/app/logistics/logistics-page.component.spec.ts` (nuevo)
- `frontend/src/app/logistics/logistics-page.component.ts` (nuevo)
- `frontend/src/app/needs/need-management-page.component.html` (modificado)
- `frontend/src/app/shared/app-shell.component.html` (modificado)
- `frontend/src/app/shared/app-shell.component.spec.ts` (modificado)

## Continuación: modelo B
La recepción evolucionó a verificación física independiente, con cierre por recurso, material no aceptado y autorización durable de excedentes. V4 está preparada y no aplicada. Consulta [Modelo B](MODELO_B_VERIFICACION.md) para contratos, compatibilidad, inventario de archivos y resultados actuales. Los resultados y limitaciones previos de este documento se conservan como registro histórico.
