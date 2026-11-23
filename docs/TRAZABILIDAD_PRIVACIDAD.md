# Trazabilidad pública y administrativa

## Alcance

Lecturas de los registros existentes de V1–V5. No se añaden tablas, columnas, rutas logísticas, traslados entre acopios ni reglas de existencias. Los cambios de presentación anteriores y el ajuste JPA de `UserAccount` se conservan.

## Endpoints y contratos

- `GET /api/donaciones/{codigoTrazabilidad}`: conserva el acceso anónimo. `PublicDonationTraceResponse` añade `trazabilidad: PublicCustody`.
- `GET /api/donaciones/administracion/{id}`: conserva `ACCESO_ADMINISTRAR`. `DonationTraceResponse` añade `trazabilidadAdministrativa: AdministrativeCustody`.
- No cambia el contrato de registro, recepción, asignación, entrega o traslado. Tampoco cambian las autoridades ni la excepción del administrador maestro.

`CustodyDtos` define una lista pública explícita: centros institucionales, recurso/unidad, cantidades, fechas, estados, emergencia y nombre general de zona. `PrivateEvent` y `AdministrativeCustody` añaden responsables, identificadores de operación, notas y dirección original. La respuesta pública nunca serializa estos dos DTO ni los JSON completos de recepciones.

El lugar de presentación procede del primer movimiento `DONACION_PRESENTADA` o del movimiento inicial compatible anterior a V4. No se analiza su texto para deducir ciudad/distrito. La proyección pública utiliza siempre un mensaje fijo de privacidad. Las ubicaciones libres y notas de los movimientos generales siguen ocultas en la API pública.

Las direcciones institucionales proceden exclusivamente de `centro_acopio.ubicacion` y `centro_distribucion.ubicacion`. El modelo no dispone de una marca adicional de autorización de publicación; los catálogos deben contener direcciones institucionales, no domicilios privados. No se publica `zona_afectada.ubicacion`, descripciones ni datos de personas beneficiarias.

No existe un mecanismo de auditoría de lecturas sensibles en los servicios inspeccionados. No se añaden escrituras de auditoría a estas consultas: se conservan los registros de auditoría operativos actuales y se respeta la prohibición de modificar datos.

## Evidencia y custodia

`DonationCustodyRepository` realiza únicamente SELECT sobre:

- `traslado_recurso`, con sus centros, unidad, responsables y fechas.
- `recepcion_operacion`: campos conocidos de respuestas JSON y decisiones aprobadas, para identificar ingresos reales por recurso. No se devuelve el JSON al cliente público.
- `detalle_entrega`, `entrega`, `necesidad` y `zona_afectada`: incrementos de asignación y entrega por línea de donación.

`DonationCustodyService` ordena los eventos por fecha, fase y operación, sin paginar parcialmente la historia de una donación. Mantiene una historia independiente por `id_detalle_donacion`, incluso si dos recursos tienen el mismo nombre.

- El centro asociado al registro es previsto hasta que exista evidencia de recepción o salida física.
- En acopio: cantidad aceptada acumulada menos salidas y reservas históricas sin distribución. Si falta evidencia del centro, se indica custodia no determinada.
- En tránsito: cantidades enviadas en operaciones sin recepción confirmada. El destino se muestra como previsto; el último punto confirmado sigue siendo el origen.
- En distribución: recepción confirmada menos entregas físicas desde distribución. Las asignaciones siguen bajo custodia del centro; no se restan como si fueran entregas.
- Entregada: únicamente incrementos positivos de `cantidad_entregada`, con fecha y nombre general de zona.
- Diferencias de recepción: no se devuelven automáticamente al origen ni se presentan como recibidas. Se muestran en los eventos, sin asignarles una custodia inventada.
- Cantidades en varios puntos: se muestran por separado, sin atribuir una única ubicación a toda la donación ni sumar etapas como stock nuevo.

Estas son proyecciones informativas. No sustituyen ni modifican los cálculos de disponibilidad de los módulos operativos.

## Limitaciones

- No hay GPS ni localización durante el trayecto.
- Recepciones históricas sin evidencia detallada no reciben fechas, responsables ni centros inferidos. Las cantidades agregadas conocidas se mantienen y la ubicación incierta se declara.
- El centro/usuario se identifica con sus datos actuales de catálogo; no se crean instantáneas históricas nuevas.
- No se sigue una porción física individual del lote; se muestran eventos y cantidades por línea y centro, según el modelo directo actual.
- La consulta MySQL de las nuevas proyecciones debe verificarse en `red_solidaria_test`. Si faltan `TEST_DB_*`, no se conecta a la base principal ni se usa `root` como alternativa.

## Archivos de esta implementación

Backend:

- `modules/donations/api/dto/CustodyDtos.java` (nuevo).
- `modules/donations/api/dto/DonationDtos.java`.
- `modules/donations/application/DonationCustodyService.java` (nuevo).
- `modules/donations/application/DonationQueryService.java`.
- `modules/donations/persistence/DonationCustodyRepository.java` (nuevo).
- Pruebas `DonationCustodyServiceTest`, `DonationCustodyPrivacyWebTest` (nuevas) y `DonationQueryServiceTest`.

Frontend, bajo `src/app/donations`:

- `donation-custody.model.ts`, `donation-custody.component.ts` y su `.spec.ts` (nuevos).
- `donation-trace.model.ts`, `donation-trace-page.component.ts` y `.html`.
- `donation-admin.model.ts`, `donation-administration-detail-page.component.ts`, `.html` y `.spec.ts`.

## Validación manual pendiente

Validación automatizada ejecutada:

| Comando | Resultado |
|---|---|
| Backend: `mvnw.cmd -B -DskipTests compile` | BUILD SUCCESS |
| Backend: `mvnw.cmd -B test` | BUILD SUCCESS: 168 casos, 143 aprobados, 25 omitidos, cero fallos/errores |
| Angular: `npm.cmd test -- --watch=false`, con `VITEST_MAX_WORKERS=2` en el proceso | 22 archivos y 139 pruebas aprobadas |
| Angular: `npm.cmd run build -- --configuration production` | Correcto; advertencia existente de presupuesto CSS del formulario de registro (4,68 kB frente a 4 kB) |
| `git diff --check` | Sin errores de whitespace |

Las ejecuciones iniciales detectaron dos problemas en pruebas nuevas de Java (referencia ambigua y preparación de un mock), corregidos antes de la ejecución satisfactoria final. El primer intento de la suite Angular se interrumpió por un trabajador terminado con SIGTERM; la repetición con dos trabajadores pasó completa. No se cambió la configuración del proyecto para limitar trabajadores.

Las variables `TEST_DB_*` no están disponibles. Los casos MySQL fueron omitidos por las condiciones existentes; no se ejecutó el SQL de las nuevas proyecciones en ningún esquema. No se arrancó la aplicación contra desarrollo, ni se ejecutó Flyway. El diff no contiene cambios en V1–V5, seguridad o servicios operativos de existencias.

Utilizar registros ya existentes en un entorno autorizado; estas instrucciones no autorizan crear ni modificar datos de producción.

1. En incógnito, abrir `/trazabilidad` y consultar un código. No debe solicitar credenciales.
2. Consultar una donación con domicilio privado y otra con un punto de colecta: comprobar en la respuesta de red completa que no aparecen direcciones originales, nombres personales, responsables, notas privadas ni identificadores de operaciones. La referencia de presentación será el mensaje fijo.
3. Con una cuenta con `ACCESO_ADMINISTRAR`, abrir el detalle: debe mostrar el lugar original exacto, las notas, responsables, centros y fechas. Sin sesión debe devolver 401 y sin autoridad 403.
4. Consultar recepción parcial y total: solo las cantidades confirmadas deben aparecer en custodia; un recurso pendiente no debe heredar el estado del otro.
5. Consultar una salida sin recepción: mostrar último origen confirmado, destino previsto y cantidad en tránsito, sin afirmar llegada.
6. Consultar una recepción en distribución con diferencia: mostrar recibido real y diferencia en el historial; no restituirla al origen.
7. Consultar asignación sin entrega y entrega parcial: la primera debe mostrarse como reserva; solo la segunda confirma entrega a población y zona.
8. Revisar en anchos de 360, 390, 768 y 1280 px: tarjetas y cronología deben permitir leer direcciones, cantidades y fechas sin desbordar. Comprobar también navegación por teclado y zoom al 200 %.

Las pruebas automatizadas utilizan repositorios/HTTP simulados. No certifican el SQL contra MySQL ni sustituyen el recorrido visual en navegador.
