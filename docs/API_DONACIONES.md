# API de trazabilidad de donaciones

El módulo registra donaciones y permite consultar su detalle e historial por código de trazabilidad. La generación de QR queda para una etapa posterior.

## Requisito del permiso

El documento funcional requiere que opere personal autorizado según sus permisos; no define códigos técnicos de permisos. El endpoint requiere `DONACION_REGISTRAR`. V1 no incluye permisos semilla. Un administrador con `ACCESO_ADMINISTRAR` debe crear el permiso con `POST /api/admin/permisos` y asignarlo a un rol mediante `PUT /api/admin/roles/{id}/permisos`. La operación de permisos reemplaza el conjunto completo: conserva todos los permisos que ya correspondan al rol e incluye `ACCESO_ADMINISTRAR` si ese rol ya lo tenía. No se permite otorgar `ACCESO_ADMINISTRAR` a un rol que no lo tenga.

La creación del permiso, lectura de roles/permisos y su asignación se hacen mediante los endpoints administrativos existentes, protegidos con `ACCESO_ADMINISTRAR`. No hay permiso semilla ni se debilita Spring Security.

## Registrar una donación

`POST /api/donaciones` requiere HTTP Basic y la autoridad `DONACION_REGISTRAR`. Devuelve `201 Created` y el DTO de la donación; `Location` apunta a `/api/donaciones/{codigoTrazabilidad}`.

Ejemplo de cuerpo:

```json
{
  "donanteId": 12,
  "emergenciaId": 4,
  "centroAcopioId": 8,
  "ubicacion": "Centro de acopio principal",
  "observaciones": "Recepción inicial",
  "detalles": [
    { "recursoId": 3, "cantidad": 25.000 },
    { "recursoId": 7, "cantidad": 10.500 }
  ]
}
```

`donanteId` y al menos uno entre `emergenciaId` y `centroAcopioId` deben referir a registros existentes. Las líneas deben incluir recursos existentes y únicos dentro de la donación. Las cantidades deben ser positivas y caber en `DECIMAL(14,3)`. `ubicacion` es texto y admite hasta 200 caracteres.

El cliente no puede enviar responsable, código ni estado inicial. El servicio toma el usuario autenticado como responsable, genera el código único en servidor, establece `REGISTRADA`, y guarda la donación, sus líneas y el movimiento inicial en una transacción. `fecha_registro` se genera según V1; `fecha_ingreso` usa la hora del registro en la columna ya existente. Un fallo al persistir el movimiento revierte la donación y sus detalles.

Los estados permitidos son los establecidos por V1: `REGISTRADA`, `RECIBIDA`, `ALMACENADA`, `CLASIFICADA`, `EN_TRANSITO`, `ENTREGADA`, `DEVUELTA` e `INCIDENCIA`. La migración no define transiciones; este alta crea únicamente el estado inicial y no incorpora reglas de transición.

## Consultar trazabilidad

`GET /api/donaciones/{codigoTrazabilidad}` es público y de solo lectura. Devuelve el código, estado, fechas, centro y ubicación logística cuando existen, recursos y cantidades, y movimientos ordenados cronológicamente con estados y ubicación. La respuesta pública omite IDs internos, datos del donante, emergencia, observaciones libres y nombre del responsable. Un código inexistente devuelve `404 Not Found` sin detalles internos.

El permiso público se limita a ese GET de un solo segmento. `POST /api/donaciones` conserva HTTP Basic y requiere `DONACION_REGISTRAR`. `/api/admin/**` conserva `ACCESO_ADMINISTRAR`; cualquier ruta no declarada como pública continúa requiriendo autenticación.

## Pruebas

La consulta pública se cubre con `DonationQueryAuthorizationWebTest` sin MySQL: valida el acceso anónimo, la respuesta sanitizada, el `404` y que rutas anidadas no quedan públicas. `DonationQueryServiceTest` cubre el DTO público y el servicio anterior. `AccessAuthorizationWebTest` valida `401` y `403` en administración; `DonationRegistrationAuthorizationWebTest` valida que el POST siga protegido. `DonationQueryMySqlIntegrationTest` consulta la donación real sin autenticación y requiere la configuración local de MySQL.

Desde `backend`:

```powershell
.\mvnw.cmd test
```

`DonationRegistrationServiceTest` y `DonationRegistrationAuthorizationWebTest` no requieren MySQL. `DonationRegistrationRollbackMySqlTest` requiere `DB_USERNAME` y `DB_URL` dirigido al MySQL local `red_solidaria`; crea temporalmente rol, usuario, donante, centro, unidad y recurso y los elimina al terminar. Comprueba que no quedan donación ni detalle si falla el movimiento inicial. La prueba se omite cuando el guard MySQL no detecta la configuración local requerida.
