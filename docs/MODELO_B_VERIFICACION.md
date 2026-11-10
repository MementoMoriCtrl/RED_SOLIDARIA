# Modelo B: donación presentada y verificación independiente

Actualización posterior: [administrador maestro](ADMINISTRADOR_MAESTRO.md) documenta la excepción exclusiva a la independencia, preparada en V5 y todavía no aplicada. El resto de este documento conserva el estado de la entrega V4.

Continuación del 09/11/2026. Esta entrega evoluciona las clases, tablas, endpoints y pantallas existentes. **V4 está preparada y NO aplicada. No se inició el backend contra desarrollo, no se modificaron datos MySQL y no se hicieron commits ni push.**

## Implementación

- El registro representa bienes presentados físicamente. Conserva cantidades preliminares en `cantidad_donada`, recibido en cero, código, responsable y fecha. Ahora deja auditoría `REGISTRO_DONACION` y movimiento tipado `DONACION_PRESENTADA`.
- La recepción existente confirma la verificación. El backend compara los ID del registrador y del usuario autenticado: una persona no puede verificar su propia donación. El mismo verificador independiente puede registrar varias parciales. No se crean roles nuevos.
- La petición ampliada conserva `cantidad` como incremento aceptado dentro de lo declarado y añade `examinada`, `noAceptada`, `excedente` y `cerrar` por recurso. Debe cumplirse **examinada = cantidad + noAceptada + excedente**. Todos los valores son DECIMAL(14,3) no negativos.
- La cantidad apta que cabe en lo declarado pendiente se recibe directamente. La parte apta que supera ese límite debe figurar como excedente. No puede declararse como aceptada para evitar la autorización.
- Material no aceptado, solicitud de excedente y cierre con diferencia exigen observaciones. Cerrar con cero aceptado es válido, incluso sin nuevo material examinado, con justificación. Una confirmación con cero aceptado no establece `fechaIngreso` ni genera stock.
- Cada recurso puede cerrarse explícitamente con faltante. Alcanzar toda su declaración aceptada también lo cierra. No se puede reabrir mediante otro UUID. Los excedentes pendientes pueden resolverse después del cierre.
- No se modifica la declaración original. La diferencia es **aceptada acumulada − declarada**. Es provisional mientras no se cierre el recurso o exista excedente pendiente. No se considera faltante definitivo durante una parcial.
- El estado general previo se conserva: `RECIBIDA` sigue correspondiendo a haber alcanzado las cantidades declaradas de todos los recursos. Un cierre parcial/cero puede seguir en `REGISTRADA`; el avance por recurso distingue el cierre. No se añadieron estados globales redundantes.

## Excedentes

Se reutiliza `recepcion_operacion`, sin crear un motor de aprobaciones ni tablas nuevas. La respuesta original es inmutable. La decisión tiene columnas independientes para resultado, responsable, fecha, justificación, UUID, SHA-256 y respuesta durable.

Un usuario con `ACCESO_ADMINISTRAR` aprueba o rechaza **todo el excedente de esa operación**. No hay autorización parcial por cantidades. Se reutiliza el permiso administrativo existente, con controles tanto en controlador como en servicio; no se exige otro aprobador distinto del verificador, porque esa separación adicional no fue solicitada. El verificador sí debe ser distinto del registrador.

- Pendiente: no incrementa existencias.
- Aprobado: incrementa el acumulado recibido exactamente una vez y reduce el excedente pendiente.
- Rechazado: reduce el pendiente sin aumentar stock; conserva el motivo y el resultado en el historial.
- La misma decisión/UUID/contenido/responsable recupera su respuesta. Una decisión distinta sobre un excedente ya resuelto devuelve 409.

La decisión se realiza bajo el bloqueo de la donación, en la misma transacción que cantidades, auditoría y movimiento. El UUID de decisión está asociado a la recepción original; no sustituye el UUID de recepción.

## Compatibilidad de recepciones

El endpoint POST existente permanece. Clientes anteriores pueden seguir enviando `recursoId` y `cantidad` positiva, sin campos de verificación. Se interpretan como cantidad examinada y aceptada, sin rechazo/excedente; conservan sus límites anteriores. Las verificaciones ampliadas identifican su versión en la huella.

Para las peticiones anteriores se mantiene la forma canónica V2/V3. La búsqueda de identidad precede a las restricciones nuevas de responsable/estado: repetir una operación histórica ya persistida recupera su respuesta, incluso si actualmente no se permitiría crear otra igual. Esto preserva historia, no autoriza una nueva autoverificación.

Los campos nuevos de respuesta admiten su ausencia al deserializar JSON antiguo. No se reescriben respuestas, UUID ni huellas históricos. Los detalles viejos pueden carecer de cantidades examinadas, responsable/centro en la respuesta y cierre por recurso; la UI los identifica como históricos, con movimientos disponibles.

## API

Se conservan todas las rutas existentes. Se amplía el POST de recepción y se añaden dos operaciones dentro del controlador actual:

- `GET /api/donaciones/administracion/{id}/verificacion`: cantidades, avance y diferencia por recurso; indica si el usuario puede verificar por ser distinto del registrador.
- `POST /api/donaciones/administracion/{id}/recepciones/{uuid}/excedente`: decisión con `operacionId` UUID propio, `aprobar` booleano y `observaciones` obligatorias.
- El GET de recepciones existente añade decisión, responsable, fecha y justificación de autorización cuando corresponde.

Ejemplo de confirmación: declaración original 10 kg, 12 kg aptos examinados:

```json
{
  "operacionId": "24eb9f05-a96d-429f-a716-5809d90fe82a",
  "ubicacion": "Acopio",
  "observaciones": "Se examinaron 12 kg aptos; 2 kg requieren autorización",
  "detalles": [
    { "recursoId": 8, "cantidad": 10, "examinada": 12, "noAceptada": 0, "excedente": 2, "cerrar": true }
  ]
}
```

Habilita 10 kg. Aprobar después esa recepción habilita los 2 kg adicionales, sin cambiar los 10 kg declarados originalmente. Son ejemplos de contrato, no datos insertados.

## Angular y privacidad

La pantalla de registro identifica donación presentada, declaración preliminar y ausencia de existencias. La ficha administrativa incorpora el botón **Consultar avance y autorizaciones**, cantidades por recurso, formulario de verificación, cierres y aprobación/rechazo de excedentes con justificación. No se creó una pantalla ni módulo adicional.

Las solicitudes inciertas conservan su UUID y contenido en sessionStorage por cuenta/donación. Las decisiones de excedente tienen su propio pendiente, que se recupera tras recrear la pantalla. La UI no actualiza inventario antes de confirmación del servidor. El backend conserva las validaciones aunque se evada la UI.

El enlace a Logística queda disponible fuera del formulario de recepción. Los errores del historial se muestran también fuera de ese formulario. El historial presenta las cantidades examinadas/aceptadas/no aceptadas/excedentes, responsable, fecha, centro y resolución. Los contadores de examinada/no aceptada se identifican **desde V4** para no fingir metadatos históricos inexistentes.

La consulta pública mantiene endpoint y pantalla. Los eventos nuevos tienen descripción pública controlada por `tipo_evento`: presentación, verificación, traslado, recepción en distribución, asignación y entrega. Si asignación y entrega ocurren juntas, se registran dos movimientos distintos. Las descripciones no se extraen de observaciones privadas. La ubicación libre de movimientos no se publica; se conserva la información pública del centro de acopio. Los eventos históricos sin tipo se presentan como movimiento registrado, sin atribuirles un tipo inventado.

## Inventarios e integración

Se mantiene el acumulado `cantidad_recibida_acopio` como única fuente recibida para la logística existente. La declaración, el material no aceptado y el excedente pendiente no se agregan como existencias. La autorización aprobada sí incrementa ese mismo acumulado, no un contador paralelo de stock.

Los traslados conservan sus bloqueos y saldos por lote. Las asignaciones conservan disponibilidad basada exclusivamente en recepción confirmada en distribución. No se reconstruyeron emergencias, necesidades ni entregas. Solo se tiparon movimientos logísticos y se separaron los eventos de asignación/entrega para la consulta pública.

## Migración preparada

`V4__verificacion_fisica_donaciones.sql` es la siguiente versión encontrada en el árbol local. No se alteraron V1/V2/V3 y no se ejecutó V4.

- Añade contadores de examen/no aceptación/excedentes y cierre a detalle_donacion.
- Sustituye el CHECK que impedía recibido > declarado, necesario para excedentes autorizados. El backend conserva el límite de recepción normal y solo la decisión aprobada puede superarlo.
- Añade metadatos de decisión a recepcion_operacion y tipo_evento a movimiento_donacion.
- No cambia cantidades anteriores ni fabrica verificaciones/decisiones de operaciones históricas. Los contadores nuevos parten en cero; no prueban ausencia histórica de rechazo.

**No es una migración puramente aditiva de restricciones:** relaja el CHECK de cantidades para permitir la regla aprobada de excedentes. Debe revisarse antes de aplicar. MySQL tiene DDL con commits implícitos; una aplicación fallida necesita revisión, no asumir rollback global.

El estado realmente desplegado de V1/V2/V3 es desconocido. No se consultó MySQL ni flyway_schema_history. El arranque normal conserva Flyway habilitado y no debe realizarse contra desarrollo hasta revisar/autorizar el esquema. No deben convivir versiones de backend anteriores y posteriores a V4.

## Estado, riesgos y pendientes

- Implementación local del flujo y pruebas aisladas: resultados exactos al final.
- V4: preparada, no aplicada ni validada contra MySQL real.
- Pruebas de MySQL: usan Flyway desactivado y requieren una base exclusiva de pruebas con V1/V2/V3/V4 previamente autorizadas y aplicadas. No utilizan credenciales DB_* como fallback para escribir.
- No se verificaron en motor real ALTER CHECK, mapeos JPA, bloqueo, concurrencia o rollback. Tampoco se ejecutó recorrido de navegador con Angular/Spring/MySQL conectados.
- Los reintentos durables evitan repetir una solicitud; no identifican físicamente un mismo bulto presentado dos veces con UUID distintos. El personal debe evitar recontar material ya confirmado.
- Correcciones/reversos de una verificación o autorización ya consumida logísticamente siguen pendientes de una regla funcional. No se sobrescribe historia ni se disminuye stock automáticamente.
- Los límites anteriores de logística (un recurso por traslado, un destino por lote, donaciones sin emergencia no asignables) permanecen documentados en MODELO_LOGISTICO.md.
- El registro inicial conserva la semántica previa sin UUID durable; ante respuesta incierta la pantalla bloquea el reenvío y requiere comprobar el listado. Esta entrega conserva la idempotencia durable de recepción y añade la de decisión.

## Ejecución y prueba

Desde backend, sin conectar a MySQL:

```powershell
.\mvnw.cmd '-Dtest=*,!RedSolidariaApplicationTests,!AccessRepositoryIntegrationTest,!*MySql*' test
```

Desde frontend:

```powershell
npm test -- --watch=false
npm run build
```

Para pruebas MySQL, únicamente después de autorizar/preparar una base exclusiva `red_solidaria_test` con V1–V4, configurar TEST_DB_URL, TEST_DB_USERNAME y TEST_DB_PASSWORD fuera del código. No enviar contraseñas al chat ni usar datos de desarrollo. Ejecutar desde backend:

```powershell
.\mvnw.cmd '-Dtest=PhaseOne*MySqlIntegrationTest' test
```

Para recorrido manual, después de autorización del esquema: configurar DB_* del entorno autorizado; arrancar backend con `.\mvnw.cmd spring-boot:run` (puerto 8080) y frontend con `npm start` (4200, proxy existente). Usar dos cuentas actuales distintas: una registra con DONACION_REGISTRAR; la otra verifica con ACCESO_ADMINISTRAR. Probar una parcial, un rechazo/cierre con cero, un excedente pendiente y su aprobación/rechazo, y después trasladar solo lo aceptado. No arrancar contra desarrollo con Flyway habilitado antes de autorizar las migraciones pendientes.

## Resultados reales

| Comprobación | Resultado |
| --- | --- |
| Backend unitario/MockMvc sin MySQL | **122 pruebas, 0 fallos, 0 errores, 0 omitidas** |
| Angular Vitest/HTTP simulado | **106 pruebas aprobadas, 0 fallidas, 21 archivos** |
| Compilación de producción Angular | **Correcta**; advertencia previa de CSS del registro: 4,68 kB frente a presupuesto 4 kB (+682 bytes) |
| MySQL protegido | **10 descubiertas, 10 omitidas**, 0 fallos/errores; faltan DB_* y TEST_DB_* |
| git diff --check | **Sin errores de whitespace** |

Las pruebas nuevas comprueban registro sin stock, usuario distinto, parciales, material no aceptado, cierre con cero, conservación de cantidades, rechazo de reapertura, excedentes pendientes/aprobados/rechazados, reintentos de decisión y privacidad. Incluyen una huella V2 original reconstruida de forma independiente y lectura de JSON histórico sin los campos nuevos. Angular comprueba el cierre con cero, el bloqueo de autoverificación y la recuperación de la misma decisión/UUID después de recrear la página.

Las pruebas MySQL preparadas añaden aprobación durable, cierre con cero y rollback de cantidades/decisión por fallo de auditoría. **No se ejecutaron contra MySQL**. Los mocks no certifican locks reales, rollback JDBC/JPA ni la migración. No se arrancó el backend de desarrollo ni se ejecutaron las otras suites que escriben sobre esa base.

Registros locales ignorados por Git: `backend/target/model-b-tests.log`, `backend/target/model-b-mysql.log`, `frontend/model-b-frontend-tests.log`, `frontend/model-b-build.log`. Las primeras ejecuciones detectaron incompatibilidad de booleanos ausentes en JSON antiguo, fixtures que no simulaban ID persistido y verificación de Mockito a través del proxy de seguridad. Se corrigieron y se ejecutó nuevamente la regresión. Los resultados de la tabla son los finales, no los intentos fallidos.

## Archivos de esta continuación

Se conservaron todos los cambios locales previos. No se modificaron los archivos del dashboard, guards, rutas, SecurityConfiguration, necesidades, balances ni las migraciones V1/V2/V3 durante esta continuación. Los únicos cambios en logística/entregas tipan los eventos de trazabilidad; sus cálculos de cantidades se mantienen.

Nuevos:

- `backend/src/main/resources/db/migration/V4__verificacion_fisica_donaciones.sql`
- `backend/src/test/java/pe/redsolidaria/modules/donations/DonationVerificationServiceTest.java`
- `docs/MODELO_B_VERIFICACION.md`

Modificados, relativos a `backend/src/main/java/pe/redsolidaria/modules/`:

- `donations/api/DonationReceiptController.java`
- `donations/api/dto/DonationReceiptDtos.java`
- `donations/api/dto/DonationDtos.java`
- `donations/application/DonationRegistrationService.java`
- `donations/application/DonationReceiptService.java`
- `donations/application/DonationQueryService.java`
- `donations/domain/DonationLine.java`
- `donations/domain/DonationMovement.java`
- `donations/domain/ReceiptOperation.java`
- `logistics/LogisticsService.java`
- `deliveries/application/DeliveryManagementService.java`

Pruebas modificadas, relativas a `backend/src/test/java/pe/redsolidaria/modules/`:

- `donations/DonationRegistrationServiceTest.java`
- `donations/DonationReceiptServiceTest.java`
- `donations/DonationReceiptAuthorizationWebTest.java`
- `donations/DonationQueryServiceTest.java`
- `donations/PhaseOneControlledMySqlIntegrationTest.java`
- `needs/PhaseOneReadOnlyMySqlIntegrationTest.java`

Frontend modificado, relativo a `frontend/src/app/donations/`:

- `donation-admin.model.ts`
- `donation-admin.service.ts`
- `donation-registration-page.component.ts`
- `donation-registration-page.component.html`
- `donation-registration-page.component.spec.ts`
- `donation-administration-detail-page.component.ts`
- `donation-administration-detail-page.component.html`
- `donation-administration-detail-page.component.css`
- `donation-administration-detail-page.component.spec.ts`
- `donation-trace.model.ts`
- `donation-trace-page.component.html`

Documentación modificada: `README.md`, `docs/MODELO_LOGISTICO.md`.
