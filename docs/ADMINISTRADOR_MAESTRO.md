# Administrador maestro: preparación local, V5 no aplicada

Esta continuación sustituye la prohibición absoluta de autoverificación del Modelo B: el administrador maestro puede verificar donaciones propias. Los demás administradores conservan la independencia. Ninguna otra validación comercial cambia.

## Identificación exclusiva

`ADMIN_MAESTRO` es una autoridad efectiva de cuenta, no un permiso heredado del rol. Se deriva de `usuario.admin_maestro = 1`, cuenta activa y rol con `ACCESO_ADMINISTRAR`. No se consulta el correo para autorizar operaciones. `/api/auth/me` y HTTP Basic usan el mismo cálculo de permisos efectivos.

La columna admite NULL o 1 y tiene una restricción UNIQUE: como máximo una cuenta puede estar marcada. La entidad la mapea como solo lectura para escrituras JPA. Ningún DTO ni endpoint permite otorgarla; crear usuarios o reasignar roles no copia la marca. Se rechaza crear/asignar el permiso reservado mediante la API de permisos. Una asignación errónea de ese código a un rol no produce la autoridad efectiva.

## Migración propuesta

`V5__administrador_maestro_exclusivo.sql` está preparada, NO ejecutada. No se cambiaron V1–V4.

Antes de ALTER, una comprobación exige usuario 3, correo autorizado, rol 4 ADMINISTRADOR, estado ACTIVO y los dos permisos actuales. El correo es una comprobación de identidad de despliegue, no un mecanismo de autorización en Java. También rechaza un código ADMIN_MAESTRO preexistente en el catálogo, que requiere conciliación manual.

V5 añade la columna nullable y sus restricciones, y marca únicamente el usuario 3. No cambia roles, cantidades, operaciones ni movimientos históricos. La cuenta nsm_26@hotmail.com no recibe ninguna modificación.

Aplicación pendiente de autorización y revisión del destino real. No arrancar normalmente este backend contra desarrollo: Flyway aplicaría las migraciones pendientes. MySQL tiene DDL con commits implícitos; un fallo puede dejar DDL o el procedimiento de validación pendiente y necesita inspección, no un reintento automático. Evitar escrituras concurrentes durante el despliegue y comprobar después que hay exactamente una marca, en el usuario esperado. No convivir versiones anteriores y posteriores a V5.

## Verificación y trazabilidad

GET de avance y POST de recepción consultan la misma condición de maestro en la entidad. UUID, huellas, bloqueos, cierres, cantidades, justificaciones y autorización de excedentes permanecen. El replay histórico conserva su respuesta original.

Las nuevas recepciones añaden `registradorId` y `autoverificacionMaestro` a la respuesta durable. `responsableId` sigue siendo el verificador real. Los campos nuevos son nullable para JSON histórico; no se reescriben operaciones previas. La auditoría principal conserva ambos IDs, el uso de excepción y las cantidades examinadas/no aceptadas/excedentes, además de fecha y responsable existentes.

Angular muestra la condición de maestro desde el perfil pero solo habilita la operación si el servidor devuelve `puedeVerificar`. El historial muestra IDs reales e identifica autoverificación del maestro; los datos históricos ausentes se indican como no disponibles.

## Validación aislada

La suite backend excluye MySQL y arranque completo. `MasterVerificationWebTest` integra seguridad HTTP, controlador, servicio real y serialización con persistencia simulada, cubriendo maestro/administrador normal sobre donaciones propias/ajenas. Las pruebas de servicio comprueban auditoría, idempotencia y conservación de las reglas comerciales. Las pruebas de acceso cubren exclusividad entre usuarios del mismo rol y rechazo del permiso reservado. Angular cubre el formulario, IDs y prevalencia de la autorización del servidor.

No se certifican aquí DDL, restricciones UNIQUE/CHECK ni mapeo JPA contra MySQL real. Tampoco se hizo un recorrido conectado en navegador. Las bases de pruebas futuras deben preparar explícitamente V5 bajo autorización; no ejecutar esta migración orientada al usuario 3 contra fixtures arbitrarios.

La validación previa utiliza un procedimiento temporal: el usuario de despliegue necesita permisos de creación, ejecución y eliminación de rutinas. Si la validación aborta, revisar y retirar el procedimiento antes de un nuevo intento autorizado.

## Resultados finales de esta continuación

- Backend: `mvnw.cmd -o '-Dtest=*,!RedSolidariaApplicationTests,!AccessRepositoryIntegrationTest,!*MySql*' test`: 132 pruebas, cero fallos, errores u omitidas. Incluye las cuatro combinaciones de integración HTTP aislada.
- Angular: `npm test -- --watch=false`: 133 aprobadas, 21 archivos.
- `npm run build`: correcto; advertencia previa de CSS del registro, 4,68 kB frente a 4 kB.
- `git diff --check`: sin errores de whitespace.
- MySQL/Flyway: no ejecutados. V5 preparada y pendiente de autorización; la cuenta real no ha sido modificada.

## Archivos de esta continuación

Backend, relativos a `backend/src/main/java/pe/redsolidaria/`:

- `config/SecurityConfiguration.java`
- `modules/access/domain/UserAccount.java`
- `modules/access/application/AccessManagementService.java`
- `modules/donations/application/DonationReceiptService.java`
- `modules/donations/api/dto/DonationReceiptDtos.java`

Pruebas, relativas a `backend/src/test/java/pe/redsolidaria/modules/`:

- `access/AccessManagementServiceTest.java`
- `access/MasterAdministratorPermissionTest.java` (nuevo)
- `donations/DonationVerificationServiceTest.java`
- `donations/MasterVerificationWebTest.java` (nuevo)

Frontend, relativos a `frontend/src/app/donations/`:

- `donation-admin.model.ts`
- `donation-administration-detail-page.component.ts`
- `donation-administration-detail-page.component.html`
- `donation-administration-detail-page.component.spec.ts`

También se preparó `backend/src/main/resources/db/migration/V5__administrador_maestro_exclusivo.sql` y se actualizaron `docs/API_ACCESO.md` y `docs/MODELO_B_VERIFICACION.md`, además de este documento nuevo. No hubo commits ni push.
