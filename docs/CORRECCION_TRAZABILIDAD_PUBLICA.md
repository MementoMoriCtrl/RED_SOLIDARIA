# Corrección de trazabilidad pública

## Diagnóstico

El historial público general se construía con `ubicacion=null` para todos los movimientos, incluso cuando sus operaciones relacionadas tenían centros institucionales. La plantilla convertía ese valor en «No indicada». La protección de privacidad era correcta para las direcciones libres de presentación, pero la proyección no aprovechaba las referencias estructuradas.

La sección de custodia tampoco exponía cantidades resumidas ni destinos de asignación como datos propios. Una asignación mantenía correctamente la custodia en distribución, pero no tenía un estado público específico. Varias entregas al mismo lugar se mostraban como posiciones distintas.

«Información de custodia no disponible» se mostraba exclusivamente cuando faltaba `trazabilidad` en la respuesta. Una lista de operaciones vacía no provoca ese mensaje en el servicio fuente. No se inspeccionó la respuesta del proceso real que originó el caso comunicado, por lo que no se afirma que su versión ejecutada coincida con el código del repositorio. Se añadió una prueba HTTP del servicio real con repositorios simulados que exige `trazabilidad` incluso sin operaciones.

## Backend

- `DonationCustodyRepository`: conserva las operaciones de recepción, traslado y entrega. Para recepciones históricas sin centro en la respuesta JSON, recupera el identificador institucional de la auditoría de recepción solo si coinciden exactamente donación, acción y fecha de la respuesta almacenada. No extrae direcciones del texto libre.
- `DonationCustodyService`: mantiene el cálculo independiente por línea de donación, distingue asignación de movimiento físico y agrupa posiciones del mismo centro/zona. Las copias de una fila fuente cuentan una sola vez; distintos detalles de entrega dentro de una misma operación se conservan.
- `DonationQueryService`: construye el historial público con los eventos estructurados, recurso, unidad, cantidades, origen, destino y zona. Conserva movimientos generales antiguos sin información inferida y evita repetir el resumen general cuando está cubierto por un evento detallado del mismo tipo y segundo. Esta eliminación de resúmenes no interviene en los cálculos de cantidades.
- DTO: `Resource` añade `Quantities` y `Allocation`; `PublicCustody` añade `recepcionAcopioConfirmada`. `PublicDonationMovementSummary` añade datos logísticos públicos. Los campos privados permanecen exclusivamente en los DTO administrativos.

Los endpoints y controles de autorización permanecen iguales:

- Público: `GET /api/donaciones/{codigoTrazabilidad}`.
- Privado: `GET /api/donaciones/administracion/{id}`, sujeto a `ACCESO_ADMINISTRAR`.

## Cantidades y ubicación

Cada recurso muestra declarada, recibida confirmada, en tránsito, custodia en distribución, sin asignar en distribución, asignada acumulada y entrega final registrada.

- Acopio: aceptada acumulada menos salidas y reservas históricas sin distribución.
- Tránsito: salidas que todavía no tienen recepción confirmada, conservando el origen como último punto confirmado.
- Custodia física en distribución: recibido en distribución menos entregado físicamente desde distribución.
- Sin asignar en distribución: recibido en distribución menos asignación acumulada desde distribución, con mínimo cero. Es una cantidad informativa; no sustituye los controles operativos de estado y autorización.
- Asignada: la zona figura como destino; el recurso sigue bajo custodia del centro y no se declara entregado.
- Entregada: solo cantidades positivas de registros de entrega existentes. Se indica la zona general y la fecha registrada. Una entrega parcial se distingue de una entrega de todas las cantidades confirmadas.
- Las etapas no se suman como existencias nuevas. Diferencias, datos históricos incompletos o centros ambiguos no se convierten en ubicaciones inventadas.

La confirmación de acopio se basa en una recepción institucional positiva o una salida real desde acopio, no en el estado global ni en el centro simplemente seleccionado durante el registro.

## Angular

La tarjeta «Ubicación y estado de tu donación» presenta resúmenes por recurso, estado, cantidades, último punto confirmado, destinos asignados y cronología. El resumen de acopio distingue recepción documentada de recepción aún no confirmada. El historial general muestra lugares institucionales y zonas de eventos estructurados; no imprime «No indicada» para sustituir la ausencia de una ubicación libre.

Una respuesta sin el campo de custodia se identifica como consulta incompleta, no como ausencia real de movimientos. No se consulta la API privada como alternativa ni se deducen lugares a partir del estado global.

La vista administrativa conserva dirección particular, responsables, observaciones e historial. El componente compartido añade información sin reducir esas capacidades. No cambian las reglas del administrador maestro ni de verificación independiente.

## Privacidad

El domicilio señalado en la solicitud se utiliza como dato sintético de regresión: debe aparecer en el DTO administrativo y estar ausente de toda la serialización pública, incluidos eventos anidados. Las pruebas también excluyen nombres personales, notas, identificadores de operación, correo, teléfono y autenticación.

Solo los catálogos institucionales proporcionan direcciones públicas. Las ubicaciones libres de presentación y de movimientos nunca se reutilizan en los eventos públicos. No se analiza texto libre para deducir distritos o ciudades.

## Archivos de esta corrección

Backend, bajo `backend/src/main/java/pe/redsolidaria/modules/donations/`:

- `api/dto/CustodyDtos.java`.
- `api/dto/DonationDtos.java`.
- `application/DonationCustodyService.java`.
- `application/DonationQueryService.java`.
- `persistence/DonationCustodyRepository.java`.

Pruebas backend:

- `DonationCustodyServiceTest.java`.
- `DonationCustodyPrivacyWebTest.java`.

Frontend, bajo `frontend/src/app/donations/`:

- `donation-custody.model.ts`.
- `donation-custody.component.ts` y `.spec.ts`.
- `donation-trace.model.ts`.
- `donation-trace-page.component.html` y `.spec.ts`.

Se conserva el trabajo anterior en otros archivos. El helper y las capturas de revisión se generaron en `frontend/tmp/`, excluido por Git.

## Comprobaciones y límites

- Backend: ejecución final completa de `mvnw.cmd -B "-Dtest=*Test,!*MySql*,!AccessRepositoryIntegrationTest" test`: BUILD SUCCESS, 149 pruebas, cero fallos, errores u omisiones. La selección excluye las pruebas de base de datos. Las 17 regresiones de custodia/privacidad también pasaron en una ejecución específica.
- Durante la ampliación de pruebas se corrigió un fixture que contenía entregas desde distribución sin registrar el traslado previo; la ejecución final utiliza el recorrido completo.
- Pruebas Angular: 142 aprobadas, 22 archivos, con dos trabajadores de Vitest.
- Build de producción: correcto. Advertencia previa del CSS de registro de donaciones: 4,68 kB frente al presupuesto de 4 kB; no se modificó ese CSS.
- Revisión en Chrome headless: 16 escenarios sobre el build de producción y una API local sintética. Siete estados públicos a 1440 y 390 px, más administración en ambos anchos. Sin desbordamiento horizontal ni errores de consola. Se inspeccionaron las cuatro capturas generadas.
- Se comprobaron recepción pendiente, acopio, tránsito, distribución, asignación sin entrega, entrega registrada y varios recursos. Administración conserva la dirección exacta y responsables simulados.
- No se usó el backend real ni ninguna base en esa revisión visual. El reporte local está en `frontend/tmp/trace-visual/report.json`.
- Las pruebas backend excluyen explícitamente las clases MySQL, la integración de repositorios y el arranque de contexto con MySQL. No se usan credenciales alternativas ni se crean fixtures en `red_solidaria_test`.
- No existen variables `TEST_DB_*` en la sesión. Las consultas SQL y el caso real de la donación ID 6 quedan pendientes de validación contra un entorno autorizado; no se certifican a partir de pruebas simuladas.
- No se reinició la aplicación contra desarrollo, no se ejecutó Flyway y no se modificaron bases de datos. V1–V5 y los servicios operativos de existencias permanecen intactos. No se realizaron commits ni push.

Antes de evaluar el caso real debe verificarse que el proceso servidor ejecuta el backend actualizado y que su respuesta incluye `trazabilidad`. No se debe interpretar una respuesta de una versión anterior como evidencia de ausencia de custodia.
