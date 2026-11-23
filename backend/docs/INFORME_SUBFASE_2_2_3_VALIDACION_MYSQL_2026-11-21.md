# Informe de validación MySQL: concurrencia, atomicidad y protección del administrador maestro

## 1. Portada y objetivo

**Proyecto:** Red Solidaria  
**Subfase:** 2.2.3  
**Fecha del informe:** 21 de noviembre de 2026  
**Estado:** cierre técnico de los escenarios examinados

El objetivo es consolidar la evidencia de cuatro suites de integración sobre MySQL para evaluar concurrencia, rollback, preservación de datos y protección del administrador maestro. El alcance del veredicto se limita a las ejecuciones y aserciones identificadas en este informe.

## 2. Alcance y entorno de pruebas

Según el contexto de ejecución proporcionado, las suites se ejecutaron secuencialmente desde PowerShell integrado en VS Code, con MySQL 8.0.46, Java 25 y Spring Boot 4.1.1, sobre `red_solidaria_test` con el usuario `rs_test`. Las cuatro ejecuciones terminaron con `BUILD SUCCESS`. La conexión JDBC fue previamente verificada manualmente con `DATABASE()=red_solidaria_test`, `CURRENT_USER()=rs_test@127.0.0.1` y `SELECT 1=1`.

Se inspeccionaron los XML de Surefire disponibles en `backend/target/surefire-reports`, sus salidas y certificados incorporados, además del código de las suites y sus soportes. Los XML respaldan los resultados por prueba, los cierres de integridad y los certificados descritos abajo. Las versiones del entorno, la secuencia de ejecución y `BUILD SUCCESS` se consignan como información proporcionada para esta ejecución; los XML examinados no son, por sí solos, un registro de consola que demuestre la secuencia o los cuatro mensajes finales de Maven.

## 3. Salvaguardas de conexión y protección de datos

Las suites controladas de donaciones exigen la URL local exacta de `red_solidaria_test`, el usuario `rs_test` y una contraseña no vacía, sin recurrir a valores alternativos. Comprueban la URL física y la identidad de la conexión, requieren las versiones V1–V5 exitosas, deshabilitan Flyway e inicialización SQL y configuran Hibernate para validar el esquema. Preparan roles y usuarios sintéticos y datos relacionados, registran las transacciones observadas y comparan el estado protegido antes y después. Las salvaguardas incluyen aislamiento de clases dentro de JUnit, límites de tiempo, verificación de finalización de trabajadores y bloqueo de escenarios posteriores ante resultados inseguros.

La suite de protección del maestro comprueba el destino y usuario dedicados, V1–V5 y un maestro activo; deshabilita Flyway e inicialización SQL y valida el esquema. Crea un actor sintético asociado al rol existente del maestro, registra auditoría y compara instantáneas. Su objetivo es la cuenta maestra existente: no es una prueba exclusivamente sintética.

Estas medidas acotan y detectan cambios; no garantizan aislamiento frente a procesos externos. Las suites de donaciones confirman fixtures y operaciones previstas. La prueba del maestro retiene el actor sintético y auditorías; no debe interpretarse como una ejecución sin escrituras.

## 4. Resultados individuales de las cuatro suites

| Suite | Ejecutadas | Fallos | Errores | Omitidas | Tiempo Surefire (s) | Evidencia principal |
|---|---:|---:|---:|---:|---:|---|
| `PhaseOneControlledMySqlIntegrationTest` | 9 | 0 | 0 | 0 | 19,957 | Nueve cierres con `existingRowsUnchanged=true` y `masterHealthy=true`. |
| `ModelBComplementaryMySqlIntegrationTest` | 9 | 0 | 0 | 0 | 46,060 | Nueve cierres con ambos indicadores verdaderos. |
| `DonationRegistrationAtomicityControlledMySqlIntegrationTest` | 2 | 0 | 0 | 0 | 27,574 | Dos cierres con ambos indicadores verdaderos y dos certificados de rollback. |
| `MasterAccountProtectionMySqlIntegrationTest` | 8 | 0 | 0 | 0 | 33,179 | Integridad final de 20 tablas, 13 auditorías, maestro activo, permisos esenciales y bloqueo readquirido. |

Los tiempos son los valores exactos de los XML, equivalentes a 19,96; 46,06; 27,57 y 33,18 segundos redondeados a dos decimales. Las ocho pruebas del maestro comprenden una prueba de auditoría, seis invocaciones parametrizadas de concurrencia y una verificación final.

## 5. Validación de concurrencia y bloqueos

Phase One comprobó reintentos simultáneos de recepción, competencia entre necesidades por existencias y reintentos de despacho y recepción logística. Las aserciones verificaron, según el escenario, una sola operación persistida para reintentos idénticos, un ganador ante la competencia y balances esperados. Modelo B comprobó decisiones simultáneas idénticas, aprobaciones con UUID distintos y competencia entre aprobación y rechazo; sus aserciones cubren respuesta repetida o un único ganador según el caso.

La suite del maestro probó seis pares de operaciones: estado/estado, rol/rol, permisos/permisos, estado/rol, rol/permisos y permisos/estado. El segundo intento permaneció bloqueado al menos 350 ms mientras el primero retenía el bloqueo real; ambos terminaron con rechazo 403 y rollback en cada par, con dos auditorías nuevas. La prueba final volvió a adquirir el bloqueo en una transacción nueva y verificó su liberación. Los resultados demuestran esos casos controlados, no todos los órdenes posibles, cargas, procesos externos ni la eliminación de deadlocks.

## 6. Validación de atomicidad y rollback

Phase One incluye aserciones de rollback ante fallos inyectados de auditoría en recepción, despacho, recepción en distribución y decisión de excedente. Estos resultados se limitan a los efectos comprobados por cada método.

Los dos certificados individuales de registro, incluidos en el XML Surefire de atomicidad, identifican explícitamente la transacción, las identidades observadas y su ausencia posterior:

| Fallo inyectado | Transacción del escenario | Identidades observadas y revertidas | Certificado |
|---|---|---|---|
| Persistencia de `movimiento_donacion` | `bb76aaad-ea37-4d6b-af71-ade7d624628b` | `donacion=51`, `detalle_donacion=53`, `movimiento_donacion=92` | `outcome=ROLLED_BACK`, `absenceVerified=true` |
| Persistencia de `registro_auditoria` | `fb3bc839-f33c-4a3c-ae69-59b2dcab6739` | `donacion=52`, `detalle_donacion=54`, `movimiento_donacion=93`, `registro_auditoria=162` | `outcome=ROLLED_BACK`, `absenceVerified=true` |

Los manifiestos finales registran `integrity-verified`, `observedCommitted=[]` para las operaciones y seis fixtures base confirmados por escenario. Las siete identidades revertidas son observaciones de inserciones revertidas, no filas confirmadas. Los fixtures base confirmados permanecen fuera del rollback de la operación.

## 7. Protección de la cuenta maestra y sus permisos esenciales

La prueba ejercitó la API contra el maestro existente y su rol, con un actor sintético. Comprobó el rechazo de cambios de estado, rol y permisos, el rollback de las transacciones rechazadas y la persistencia de la auditoría de rechazo. La verificación final reportó `unchangedTables=20`, `auditRows=13`, `masterActive=true`, `essentials=true` y `lockReacquired=true`. Las aserciones de permisos esenciales comprueban `ACCESO_ADMINISTRAR` y `DONACION_REGISTRAR`.

El contexto proporcionado también confirma `noCleanupOrMigration=true`; el XML inspeccionado contiene el cierre de integridad anterior, pero no el mensaje de retención que incluye ese indicador. El código configura Flyway deshabilitado y no ejecuta limpieza en esta suite. La evidencia no acredita protección frente a toda vía externa de escritura o administración.

## 8. Integridad de los registros preexistentes

Los XML de las tres suites controladas contienen 20 cierres de escenario: nueve, nueve y dos, respectivamente, todos con `existingRowsUnchanged=true` y `masterHealthy=true`. Las comprobaciones comparan el conjunto protegido y verifican el maestro durante los escenarios. La suite del maestro compara 20 tablas mediante instantáneas; excluye `registro_auditoria` de esa comparación porque espera nuevas auditorías, que comprueba por separado. Su línea de base se toma después de insertar el actor sintético, por lo que la comparación no implica ausencia de filas nuevas.

Estas afirmaciones describen las comparaciones y momentos de las pruebas, no una consulta independiente del estado actual ni una garantía frente a cambios posteriores. Los registros sintéticos y auditorías retenidos deben considerarse al administrar la base de prueba.

## 9. Resumen estadístico consolidado

- **Suites:** 4.
- **Pruebas o invocaciones Surefire:** 28; **fallos:** 0; **errores:** 0; **omitidas:** 0.
- **Suma de tiempos de suite Surefire:** 126,770 segundos. Es una suma de duraciones reportadas, no una medición independiente del tiempo total transcurrido en consola.
- **Cierres controlados de integridad:** 20 de 20 con ambos indicadores verdaderos.
- **Certificados individuales de registro:** 2 rollbacks, con 3 y 4 identidades observadas y ausencia verificada.
- **Protección del maestro:** seis pares concurrentes, 13 auditorías y 20 tablas comparadas en el cierre.

## 10. Limitaciones y riesgos pendientes

- La evidencia abarca escenarios concretos y datos de prueba, no todas las combinaciones de carga, fallos, tiempos, configuraciones o escritores externos. No elimina el riesgo de deadlocks ni prueba recuperación ante todos ellos.
- Las comparaciones no sustituyen una auditoría independiente posterior de la base. Los datos sintéticos y auditorías retenidos son escrituras reales.
- No se realizó en esta elaboración una nueva conexión, consulta, migración ni ejecución de pruebas. Las versiones, la secuencia y los cuatro `BUILD SUCCESS` provienen del contexto proporcionado; no se recuperó un log de consola que los reproduzca. El indicador `noCleanupOrMigration=true` también se atribuye a dicho contexto.
- Los informes en `target` son artefactos locales susceptibles de sobrescritura; conviene preservar copias controladas de los XML y logs originales sin incluir secretos.
- Estos resultados no prueban el comportamiento integral de producción, el rendimiento bajo carga, la seguridad de todos los accesos, ni autorizan un despliegue o una migración.

## 11. Veredicto técnico

**Validación satisfactoria para los escenarios documentados de la subfase 2.2.3.** Los informes Surefire inspeccionados registran 28 pruebas exitosas sin fallos, errores ni omisiones; los certificados disponibles respaldan los dos rollbacks de registro y los cierres de integridad declarados. La protección y serialización del maestro quedaron verificadas para los seis pares ensayados y las aserciones indicadas. El veredicto no se extiende a escenarios no probados ni constituye aprobación de producción.

## 12. Conclusión y siguientes pasos

Se puede cerrar técnicamente la subfase respecto de los escenarios y evidencia enumerados. Como pasos posteriores, preservar los XML y logs de ejecución en un repositorio de evidencias controlado, documentar la gestión de fixtures y auditorías retenidos, y planificar por separado las pruebas de carga, concurrencia con otros escritores y recuperación de fallos que el proyecto requiera. Cualquier despliegue o migración requiere su propia revisión y autorización.

### Fuentes locales

- [Surefire Phase One](../target/surefire-reports/TEST-pe.redsolidaria.modules.donations.PhaseOneControlledMySqlIntegrationTest.xml).
- [Surefire Modelo B](../target/surefire-reports/TEST-pe.redsolidaria.modules.donations.ModelBComplementaryMySqlIntegrationTest.xml).
- [Surefire atomicidad y certificados](../target/surefire-reports/TEST-pe.redsolidaria.modules.donations.DonationRegistrationAtomicityControlledMySqlIntegrationTest.xml).
- [Surefire protección del maestro](../target/surefire-reports/TEST-pe.redsolidaria.modules.access.MasterAccountProtectionMySqlIntegrationTest.xml).
- [Soporte y salvaguardas controladas](../src/test/java/pe/redsolidaria/modules/donations/ControlledDonationMySqlSupport.java) y [suite de protección del maestro](../src/test/java/pe/redsolidaria/modules/access/MasterAccountProtectionMySqlIntegrationTest.java).
