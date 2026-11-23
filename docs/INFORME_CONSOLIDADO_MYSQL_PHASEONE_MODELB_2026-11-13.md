# Informe consolidado de integración MySQL: PhaseOne, Model B, DonationQuery, DonationRegistration y Atomicity

Fecha de las ejecuciones y del informe: **13 de noviembre de 2026**. Los registros de aplicación utilizan el desplazamiento **UTC−05:00**, correspondiente a Lima.

**Resumen ejecutivo actualizado:** **22 escenarios aprobados**, **418 registros confirmados** —286 `COMMITTED_BASE` y 132 `COMMITTED_OPERATION`—, **73 commits** y **16 rollbacks**. La quinta suite, DonationRegistrationAtomicity, añade doce fixtures base confirmados y siete identidades observadas y revertidas; estas siete identidades **no se suman como registros confirmados**. Ambos escenarios preservan las 381 identidades anteriores exigidas. Los 418 registros son los confirmados por estas cinco suites, **no el total de registros de la base de datos**.

## 1. Alcance, fuentes y criterio de consolidación

La evidencia histórica de PhaseOne y Model B se basa exclusivamente en los cuatro artefactos Surefire siguientes:

- [Resumen de PhaseOneControlledMySqlIntegrationTest](../backend/target/surefire-reports/pe.redsolidaria.modules.donations.PhaseOneControlledMySqlIntegrationTest.txt).
- [XML, salida estándar y salida de error de PhaseOne](../backend/target/surefire-reports/TEST-pe.redsolidaria.modules.donations.PhaseOneControlledMySqlIntegrationTest.xml).
- [Resumen de ModelBComplementaryMySqlIntegrationTest](../backend/target/surefire-reports/pe.redsolidaria.modules.donations.ModelBComplementaryMySqlIntegrationTest.txt).
- [XML, salida estándar y salida de error de Model B](../backend/target/surefire-reports/TEST-pe.redsolidaria.modules.donations.ModelBComplementaryMySqlIntegrationTest.xml).

No se ejecutaron pruebas, SQL ni migraciones para elaborar este documento. No se abrieron conexiones ni se consultó o modificó la base. En esta actualización se modificó únicamente este informe.

La incorporación inicial de DonationQuery utilizó los datos verificados proporcionados, correspondientes a su ejecución controlada del 13/11/2026. Esa revisión no inspeccionó sus XML o logs originales; las limitaciones de detalle de aquella incorporación se conservan en su sección específica.

DonationRegistration incorpora los resultados de la ejecución del 13/11/2026 ya auditada mediante su resumen Surefire, XML y manifiestos. Las rutas y el detalle de esa auditoría se conservan en la sección 9. No se ejecutaron Maven, Spring Boot ni conexiones para actualizar el informe.

La quinta suite se incorpora a partir de sus informes Surefire, manifiestos finales y certificados `CONTROLLED_REGISTRATION_ROLLBACK`, detallados en la sección 10. Para comprobar la aritmética se releyeron los XML existentes de las cinco suites, sumando exclusivamente las filas y transacciones de los manifiestos finales `integrity-verified`. El XML de DonationQuery disponible ahora confirma las cantidades históricas de 14 filas base, una operación y dos commits; no se reemplazan las limitaciones documentadas de su incorporación inicial.

Para PhaseOne y Model B, los resultados individuales proceden de los elementos `testcase`. Las cantidades de filas y transacciones se obtienen exclusivamente del manifiesto final `integrity-verified` de cada escenario. No se suman nuevamente los manifiestos `preparation-confirmed`, porque contienen estados anteriores de las mismas filas.

## 2. Resultado y duración por suite

| Suite | Pruebas | Aprobadas | Fallos | Errores | Omitidas | Duración Surefire |
|---|---:|---:|---:|---:|---:|---:|
| PhaseOneControlledMySqlIntegrationTest | 9 | 9 | 0 | 0 | 0 | 16,07 s |
| ModelBComplementaryMySqlIntegrationTest | 9 | 9 | 0 | 0 | 0 | 15,62 s |
| DonationQueryMySqlIntegrationTest | 1 | 1 | 0 | 0 | 0 | No proporcionada |
| DonationRegistrationControlledMySqlIntegrationTest | 1 | 1 | 0 | 0 | 0 | 9,394 s |
| DonationRegistrationAtomicityControlledMySqlIntegrationTest | 2 | 2 | 0 | 0 | 0 | 25,505 s |
| **Total** | **22** | **22** | **0** | **0** | **0** | **88,421 s según los cinco XML** |

Para conservar el historial, DonationQuery mantiene arriba la indicación de duración no proporcionada en su incorporación inicial. Su XML, leído para esta actualización, registra **21,835 s**. El total de 88,421 s utiliza las precisiones originales: 16,070 + 15,617 + 21,835 + 9,394 + 25,505; es una suma de duraciones de suite, no una ejecución continua.

Los 31,69 segundos son la suma de las duraciones de ambas suites, no una duración continua entre las dos ejecuciones. Las duraciones de suite no deben confundirse con la suma de los tiempos individuales: incluyen trabajo de inicialización y del ciclo de prueba. Los mensajes de arranque sitúan PhaseOne a las 15:29 y Model B a las 15:56, UTC−05:00.

## 3. Los 22 escenarios y sus resultados

En las tablas, `C` identifica transacciones `COMMITTED` y `R` transacciones `ROLLED_BACK`. Las cantidades incluyen la transacción de preparación de cada escenario. Las columnas de filas contienen únicamente asignaciones confirmadas del escenario, no sus filas preexistentes protegidas.

### PhaseOneControlledMySqlIntegrationTest

| N.º | Escenario | Resultado | Tiempo | COMMITTED_BASE | COMMITTED_OPERATION | C | R |
|---|---|---|---:|---:|---:|---:|---:|
| P1 | `auditFailureRollsBackReceiptMovementAndOperationIdentity` | Aprobado | 1,183 s | 14 | 0 | 1 | 1 |
| P2 | `zeroAcceptedClosurePersistsWithoutStock` | Aprobado | 0,673 s | 14 | 5 | 2 | 0 |
| P3 | `competingNeedsCannotOverallocateAndDeliveryDoesNotReserveStockTwice` | Aprobado | 1,505 s | 14 | 17 | 7 | 3 |
| P4 | `concurrentRetriesPersistOnceAndANewOperationCanReceiveAgain` | Aprobado | 0,857 s | 14 | 6 | 4 | 1 |
| P5 | `approvalAuditFailureRollsBackInventoryAndDecision` | Aprobado | 0,634 s | 14 | 5 | 2 | 1 |
| P6 | `auditFailureRollsBackDistributionReceipt` | Aprobado | 0,731 s | 14 | 7 | 3 | 1 |
| P7 | `surplusAuthorizationIsDurable` | Aprobado | 0,644 s | 14 | 7 | 4 | 0 |
| P8 | `logisticsRetriesAndPartialDeliveryForA100KgNeed` | Aprobado | 1,114 s | 14 | 14 | 9 | 0 |
| P9 | `auditFailureRollsBackDispatchAndItsDurableIdentity` | Aprobado | 0,575 s | 14 | 4 | 2 | 1 |
| **Total** | | **9 aprobados** | | **126** | **65** | **34** | **8** |

### ModelBComplementaryMySqlIntegrationTest

| N.º | Escenario | Resultado | Tiempo | COMMITTED_BASE | COMMITTED_OPERATION | C | R |
|---|---|---|---:|---:|---:|---:|---:|
| B1 | `historicalV2AndV3ResponsesReplayWithoutRewritingHistory` | Aprobado | 1,834 s | 14 | 2 | 4 | 2 |
| B2 | `automaticClosureRequiresAllResourcesAndShortClosureCannotReopen` | Aprobado | 0,902 s | 16 | 8 | 3 | 1 |
| B3 | `simultaneousDifferentApprovalUuidsHaveOneWinner` | Aprobado | 1,026 s | 14 | 7 | 4 | 1 |
| B4 | `registrantCannotVerifyAndIndependentUserCan` | Aprobado | 0,619 s | 14 | 4 | 2 | 1 |
| B5 | `rejectionPersistsMetadataAndRetriesWithoutAdditionalEffects` | Aprobado | 0,713 s | 14 | 7 | 4 | 0 |
| B6 | `simultaneousApprovalAndRejectionHaveOneWinner` | Aprobado | 1,076 s | 14 | 7 | 4 | 1 |
| B7 | `completingTheOnlyResourceAutomaticallyClosesTheDonation` | Aprobado | 0,700 s | 14 | 4 | 2 | 0 |
| B8 | `simultaneousIdenticalApprovalsReturnTheSameResponseOnce` | Aprobado | 0,655 s | 14 | 7 | 4 | 0 |
| B9 | `successiveVerificationsPreserveEveryCounterDeclarationAndHistory` | Aprobado | 0,887 s | 14 | 16 | 6 | 0 |
| **Total** | | **9 aprobados** | | **128** | **62** | **33** | **6** |

El orden de PhaseOne y Model B mostrado es el de los elementos `testcase` de cada XML. Ninguno contiene elementos `failure`, `error` o `skipped`.

### DonationQueryMySqlIntegrationTest

| N.º | Escenario | Resultado | Tiempo | COMMITTED_BASE | COMMITTED_OPERATION | C | R |
|---|---|---|---|---:|---:|---:|---:|
| DQ1 | Consulta pública de donación por trazabilidad; el nombre literal del método no fue proporcionado en la evidencia de esta actualización | Aprobado | No proporcionado | 14 | 1 | 2 | 0 |

El resultado verificado es una prueba aprobada, sin fallos, errores ni omisiones, y `BUILD SUCCESS`.

### DonationRegistrationControlledMySqlIntegrationTest

| N.º | Escenario | Resultado | Tiempo | COMMITTED_BASE | COMMITTED_OPERATION | C | R |
|---|---|---|---:|---:|---:|---:|---:|
| DR1 | `registersDonationDetailMovementAndAuditInOneCommittedTransaction` | Aprobado | 1,695 s | 6 | 4 | 2 | 0 |

El resumen y XML acreditan una prueba aprobada, sin fallos, errores ni omisiones. La duración individual es distinta de los 9,394 s de la suite, que incluyen su ciclo de inicialización.

### DonationRegistrationAtomicityControlledMySqlIntegrationTest

| N.º | Escenario | Resultado | Tiempo | COMMITTED_BASE | COMMITTED_OPERATION | Identidades ROLLED_BACK | C | R |
|---|---|---|---:|---:|---:|---:|---:|---:|
| A1 | `rollsBackDonationDetailAndMovementWhenMovementPersistenceFails` | Aprobado | 5,338 s | 6 | 0 | 3 | 1 | 1 |
| A2 | `rollsBackFourObservedInsertsWhenAuditPersistenceFails` | Aprobado | 2,205 s | 6 | 0 | 4 | 1 | 1 |
| **Total** | | **2 aprobados** | | **12** | **0** | **7** | **2** | **2** |

El orden es el de los `testcase` del XML. Las siete identidades revertidas se distinguen de los doce fixtures confirmados y de las dos transacciones revertidas.

## 4. Filas confirmadas y resultados transaccionales

| Concepto | PhaseOne | Model B | DonationQuery | DonationRegistration | Atomicity | Total |
|---|---:|---:|---:|---:|---:|---:|
| Filas COMMITTED_BASE | 126 | 128 | 14 | 6 | 12 | 286 |
| Filas COMMITTED_OPERATION | 65 | 62 | 1 | 4 | 0 | 132 |
| **Filas nuevas confirmadas** | **191** | **190** | **15** | **10** | **12** | **418** |
| Identidades de filas con estado ROLLED_BACK | 2 | 0 | 0 | 0 | 7 | 9 |
| Transacciones de preparación confirmadas | 9 | 9 | 1 | 1 | 2 | 22 |
| Transacciones de escenario confirmadas | 25 | 24 | 1 | 1 | 0 | 51 |
| **Transacciones confirmadas** | **34** | **33** | **2** | **2** | **2** | **73** |
| **Transacciones revertidas** | **8** | **6** | **0** | **0** | **2** | **16** |
| Transacciones ACTIVE o UNKNOWN al finalizar | 0 | 0 | No desglosadas en la incorporación inicial | 0 | 0 | Véase limitación histórica |
| Filas ASSIGNED al finalizar | 0 | 0 | No desglosadas en la incorporación inicial | 0 | 0 | Véase limitación histórica |

**Antecedente de corrección aritmética:** en la incorporación de DonationRegistration se propusieron 272 filas base y 134 operaciones. Los resultados de las primeras cuatro suites suman **126 + 128 + 14 + 6 = 274 base** y **65 + 62 + 1 + 4 = 132 operaciones**, es decir, **406 registros**. Se conserva este antecedente sin reclasificar filas.

**Reconciliación de la quinta suite:** 274 + 12 = **286 base**; 132 + 0 = **132 operaciones**; 406 + 12 = **418 confirmados**; 71 + 2 = **73 commits**; 14 + 2 = **16 rollbacks**; 20 + 2 = **22 escenarios aprobados**. Las siete identidades adicionales `ROLLED_BACK` elevan a nueve las identidades revertidas documentadas de las cinco suites, pero no incrementan los registros confirmados.

Las 191 identidades confirmadas de PhaseOne y las 190 de Model B son únicas dentro de sus respectivos conjuntos y no presentan intersección al comparar `(tabla, PK)`. Las 381 filas son la suma documental de los fixtures nuevos confirmados de ambas suites; no constituyen un conteo actual de toda la base ni incluyen las filas restauradas previas.

Las transacciones sin inserciones nuevas también se contabilizan: por ejemplo, un reintento puede terminar en commit sin generar otra fila. Por ello, el número de transacciones no equivale al de operaciones con efectos nuevos ni al de sentencias SQL.

Las dos identidades revertidas de PhaseOne son:

- `movimiento_donacion/16`, en P1.
- `traslado_recurso/f8a02053-fa0d-43f2-a30f-a985d3973cf0`, en P9.

No se incluyen en las 381 filas históricas confirmadas ni en el acumulado actualizado de 418. Model B registra seis transacciones revertidas, aunque ninguna identidad de fila permanezca clasificada como ROLLED_BACK en sus manifiestos finales. Una transacción puede revertir actualizaciones o terminar antes de asignar un ID nuevo. Las siete identidades revertidas de Atomicity se detallan por separado en la sección 10.

El desglose de COMMITTED_OPERATION es:

| Tabla | PhaseOne | Model B | DonationQuery | DonationRegistration | Atomicity | Total |
|---|---:|---:|---:|---:|---:|---:|
| `donacion` | 0 | 0 | 0 | 1 | 0 | 1 |
| `detalle_donacion` | 0 | 0 | 0 | 1 | 0 | 1 |
| `recepcion_operacion` | 9 | 13 | 0 | 0 | 0 | 22 |
| `movimiento_donacion` | 19 | 16 | 1 | 1 | 0 | 37 |
| `registro_auditoria` | 28 | 33 | 0 | 1 | 0 | 62 |
| `traslado_recurso` | 3 | 0 | 0 | 0 | 0 | 3 |
| `entrega` | 3 | 0 | 0 | 0 | 0 | 3 |
| `detalle_entrega` | 3 | 0 | 0 | 0 | 0 | 3 |
| **Total** | **65** | **62** | **1** | **4** | **0** | **132** |

Los ceros de `donacion` y `detalle_donacion` en las tres primeras suites se refieren únicamente a `COMMITTED_OPERATION`: sus filas de preparación ya están contabilizadas como `COMMITTED_BASE`.

## 5. Integridad y protección de PhaseOne durante Model B

| Evidencia final | PhaseOne | Model B |
|---|---:|---:|
| Manifiestos `integrity-verified` | 9 de 9 | 9 de 9 |
| Mensajes `existingRowsUnchanged=true` | 9 de 9 | 9 de 9 |
| Mensajes `masterHealthy=true` | 9 de 9 | 9 de 9 |
| Diferencias entre IDs confirmados del ledger y `confirmedRecords` | 0 | 0 |
| Excepciones autorizadas de referencia a padres existentes | 0 | 0 |
| Manifiestos finales fallidos o indeterminados | 0 | 0 |

En los nueve escenarios de cada suite aparecen las etapas `preparation-confirmed` e `integrity-verified`, seguidas por el correspondiente mensaje `CONTROLLED_FIXTURE_INTEGRITY` con ambos indicadores en `true`.

**La protección de los 191 registros de PhaseOne durante Model B queda respaldada por los artefactos:**

1. Cada uno de los nueve manifiestos finales de Model B contiene `requiredExistingRows` con **191 identidades únicas**.
2. Cada inventario coincide exactamente con el conjunto de filas COMMITTED_BASE y COMMITTED_OPERATION de los nueve manifiestos finales de PhaseOne: **cero faltantes, cero adicionales**.
3. Cada escenario de Model B termina con `existingRowsUnchanged=true` y `masterHealthy=true`.
4. Las filas nuevas de Model B no reutilizan ninguna de esas 191 identidades.

Esto confirma lo certificado por el auditor durante esas ejecuciones. No es una nueva consulta a MySQL ni una comparación independiente de todos los valores históricos entre suites: los XML no proporcionan aquí un juego completo de imágenes de columnas de PhaseOne para recalcular esa comparación fuera del auditor. El inventario acredita identidades y el mensaje de integridad acredita la preservación respecto de la captura inicial de cada escenario.

El escenario histórico B1 aporta además dos entradas `historicalReceiptSeeds`: PK **11**, versión **2**, y PK **12**, versión **3**, ambas de `recepcion_operacion`. Sus transacciones figuran COMMITTED y sus filas COMMITTED_OPERATION; los identificadores de transacción coinciden con el ledger y las correlaciones coinciden con el escenario. No se confunden con filas preexistentes de PhaseOne.

No se registran fallos de callbacks, trabajadores pendientes, deadlocks, timeouts concurrentes o bloqueos de integridad en la salida examinada. Los rollbacks de escenarios aprobados y los fallos de auditoría contemplados por sus nombres no son fallos de callbacks ni resultados inciertos del auditor.

## 6. Destino e identidad MySQL registrados

Los diagnósticos JDBC históricos de PhaseOne y Model B coinciden:

| Dato | Valor registrado |
|---|---|
| URL JDBC | `jdbc:mysql://127.0.0.1:3306/red_solidaria_test` |
| Catálogo | `red_solidaria_test` |
| Cuenta declarada en los manifiestos | `rs_test@127.0.0.1` |
| Driver | MySQL Connector/J |
| Versión del servidor | 8.0.46 |
| Dialecto | MySQLDialect |
| Aislamiento mostrado en el diagnóstico de arranque | REPEATABLE_READ, con el mismo valor por defecto |

La cuenta aparece en los 18 manifiestos finales. El diagnóstico JDBC no imprime por separado una fila cruda de `CURRENT_USER()`; la identificación de cuenta se atribuye a los manifiestos del auditor. El aislamiento mostrado es el diagnóstico de arranque, no una afirmación de que todas las transacciones de negocio utilizaran ese aislamiento.

No aparecen destinos ni cuentas alternativos en los mensajes JDBC y manifiestos examinados. Esta afirmación se limita a lo registrado, sin reconstruir conexiones no documentadas.

## 7. Advertencias y limitaciones

PhaseOne y Model B incluyen estas advertencias:

- **Spring Security:** existe un AuthenticationProvider explícito; los beans UserDetailsService no se utilizan para configurar automáticamente el login de usuario y contraseña. No se registra un fallo de autenticación de los escenarios por este aviso.
- **Mockito/JDK:** el mock maker se autoasocia mediante un agente Byte Buddy. El JDK advierte de futuras restricciones a la carga dinámica de agentes. Es un pendiente de compatibilidad de ejecución de pruebas, sin fallos asociados en estas ejecuciones.
- **Metadatos Hibernate:** autocommit y tamaños mínimo/máximo del pool figuran como `undefined/unknown`; el diagnóstico de catálogo/esquema muestra `red_solidaria_test/undefined`. Estos valores no son estados UNKNOWN del ledger. No hubo transacciones ACTIVE o UNKNOWN al finalizar.

Límites de las conclusiones:

- La revisión histórica comprendió los cuatro informes citados de PhaseOne y Model B. No se contrastó un log Maven externo para acreditar su línea literal `BUILD SUCCESS`; sus resultados satisfactorios están respaldados por Surefire. Para DonationQuery, `BUILD SUCCESS` forma parte de los datos verificados aportados para esta actualización.
- No se realizó una comprobación posterior del estado actual de MySQL ni se modificaron o limpiaron fixtures.
- Los indicadores de integridad se atribuyen a los controles que los emitieron. No se inspeccionó código ni configuración adicional para elaborar esta consolidación.
- Los resultados no acreditan por sí solos exclusión frente a escritores externos, otros procesos o cambios posteriores a las ejecuciones.
- Los enlaces apuntan a artefactos de `target/surefire-reports`, que podrían sobrescribirse o desaparecer con futuras ejecuciones o limpiezas. Este documento conserva sus resultados consolidados, pero no sustituye una copia íntegra de los XML originales.
- No se extrapolan los resultados a otras suites, a toda la aplicación, a producción o a pruebas de carga prolongada.

## 8. Ejecución controlada de DonationQuery del 13/11/2026

### Resultado, destino y preservación

| Dato verificado | Resultado |
|---|---|
| Suite | `DonationQueryMySqlIntegrationTest` |
| Pruebas | 1 aprobada; 0 fallos, 0 errores y 0 omitidas |
| Resultado Maven | `BUILD SUCCESS` |
| Base de datos | `red_solidaria_test` |
| Usuario MySQL | `rs_test@127.0.0.1` |
| Servidor | MySQL 8.0.46 |
| Integridad de filas existentes | `existingRowsUnchanged=true` |
| Administrador Maestro | `masterHealthy=true` |
| Inventario anterior exigido y preservado | 381 identidades de PhaseOne y Model B |
| Rollbacks | 0 |

La exigencia y preservación de las 381 identidades anteriores se incorpora según los resultados verificados proporcionados. No constituye una consulta nueva a MySQL ni una reconstrucción independiente de sus imágenes históricas.

### Manifiesto resumido disponible

| Estado | Tabla / alcance | Identidades disponibles | Filas |
|---|---|---|---:|
| `COMMITTED_BASE` | Preparación del fixture | Las 14 PK no fueron proporcionadas para esta actualización | 14 |
| `COMMITTED_OPERATION` | `movimiento_donacion` | PK **52** | 1 |
| **Total confirmado de DonationQuery** | | | **15** |

| Transacción | Resultado | Cantidad |
|---|---|---:|
| Preparación | `COMMITTED` | 1 |
| Escenario | `COMMITTED` | 1 |
| **Total** | **2 commits; 0 rollbacks** | **2** |

Este resumen no es una transcripción del manifiesto original completo. No se proporcionaron duración, UUID de correlación, identificadores de transacción, las 14 PK base ni hashes de los artefactos originales de DonationQuery. No se inventan ni se reconstruyen a partir de secuencias. Tampoco se añaden métricas de diferencias ledger/captura o estados internos no desglosados en los datos aportados.

Las advertencias de **Mockito/JDK** sobre el agente dinámico y de **Spring Security** sobre la configuración de autenticación se registran como **no bloqueantes**: la ejecución terminó con la prueba aprobada y `BUILD SUCCESS`.

## 9. Ejecución controlada de DonationRegistration del 13/11/2026

### Evidencias y resultado auditado

- [Resumen Surefire](../backend/target/surefire-reports/pe.redsolidaria.modules.donations.DonationRegistrationControlledMySqlIntegrationTest-registration-controlled.txt).
- [XML Surefire, manifiestos y salidas](../backend/target/surefire-reports/TEST-pe.redsolidaria.modules.donations.DonationRegistrationControlledMySqlIntegrationTest-registration-controlled.xml).

Estas rutas corresponden a `backend/target/surefire-reports/`. La auditoría posterior a la ejecución obtuvo **APROBADO**: una prueba aprobada, cero fallos, cero errores y cero omitidas. `BUILD SUCCESS` fue comunicado como resultado de la ejecución; no figura como línea literal en los dos artefactos Surefire examinados.

| Dato registrado | Resultado |
|---|---|
| Base | `red_solidaria_test` |
| Cuenta declarada en el manifiesto | `rs_test@127.0.0.1` |
| Servidor | MySQL 8.0.46 |
| Correlación | `4d35a6a4-5b17-4b70-a0f4-3f3625e1def3` |
| Inventario anterior exigido | 381 identidades únicas, coincidentes con 191 de PhaseOne y 190 de Model B |
| Etapas registradas | `preparation-confirmed` e `integrity-verified` |
| Integridad | `existingRowsUnchanged=true` |
| Administrador Maestro | `masterHealthy=true` |
| Estados finales ASSIGNED, ACTIVE o UNKNOWN | Ninguno |
| Errores registrados de IntegrityGate o callbacks | Ninguno |

### Manifiesto de las cuatro operaciones confirmadas

| Tabla | PK observada | Relaciones y significado | Estado |
|---|---:|---|---|
| `donacion` | 30 | Donante 28, centro de acopio 26, registrador 51 | `COMMITTED_OPERATION` |
| `detalle_donacion` | 31 | Donación 30, recurso 31 | `COMMITTED_OPERATION` |
| `movimiento_donacion` | 53 | Donación 30, responsable 51, evento `DONACION_PRESENTADA` | `COMMITTED_OPERATION` |
| `registro_auditoria` | 99 | Actor 51, entidad `DONACION`, registro 30, acción `REGISTRO_DONACION` | `COMMITTED_OPERATION` |

Las cuatro operaciones comparten la transacción **`44a29d28-e527-4a70-ad72-f47223eca1b4`**, con nombre `pe.redsolidaria.modules.donations.application.DonationRegistrationService.register` y resultado **`COMMITTED`**. Coinciden exactamente con las cuatro entradas de `observedCommitted`.

### Preparación y cantidades

Las seis filas `COMMITTED_BASE` son: `rol/30`, `usuario/51`, `donante/28`, `centro_acopio/26`, `unidad_medida/26` y `recurso/31`. El usuario referencia al rol 30 y el recurso a la unidad 26. Comparten la transacción de preparación **`bc85fb2a-bbb5-4211-aa17-2143cd94d145`**, con resultado **`COMMITTED`**.

Resultado de la suite: **6 filas base + 4 operaciones = 10 registros nuevos**, **2 commits y 0 rollbacks**. Las diez identidades únicas del ledger coinciden con `confirmedRecords`; ninguna se solapa con las 381 identidades exigidas.

### Preservación, advertencias y límites

La auditoría contrastó las 381 identidades de `requiredExistingRows` con los manifiestos finales originales de PhaseOne y Model B: coincidencia exacta. Su preservación durante esta ejecución está respaldada por `existingRowsUnchanged=true` y `masterHealthy=true`. Esto no amplía el inventario nominal a 396 identidades ni constituye una consulta posterior al estado actual de MySQL.

Las advertencias de **Mockito/Byte Buddy** sobre carga dinámica del agente y de **Spring Security** sobre el AuthenticationProvider son **no bloqueantes**. Los valores Hibernate `undefined/unknown` del pool y autocommit no son estados transaccionales `UNKNOWN`. No se encontraron inconsistencias en la auditoría de esta ejecución.

Los informes no incluyen todas las imágenes originales de columnas necesarias para recalcular independientemente la preservación fuera del auditor. No se inventan hashes ni se atribuye a esta actualización una nueva ejecución o conexión.

## 10. Ejecución controlada de DonationRegistrationAtomicity del 13/11/2026

### Evidencias y resultado

- [Resumen Surefire](../backend/target/surefire-reports/pe.redsolidaria.modules.donations.DonationRegistrationAtomicityControlledMySqlIntegrationTest-registration-atomicity-controlled.txt).
- [XML Surefire con manifiestos, certificados y salidas](../backend/target/surefire-reports/TEST-pe.redsolidaria.modules.donations.DonationRegistrationAtomicityControlledMySqlIntegrationTest-registration-atomicity-controlled.xml).

Los archivos están en `backend/target/surefire-reports/`. El XML confirma **dos pruebas aprobadas, cero fallos, cero errores y cero omitidas**, con duración de suite de **25,505 s**. La auditoría posterior obtuvo **APROBADO**. La línea literal `BUILD SUCCESS` fue comunicada como resultado de la ejecución; no se atribuye su presencia a estos artefactos Surefire.

Destino registrado: **MySQL 8.0.46**, URL `jdbc:mysql://127.0.0.1:3306/red_solidaria_test`, base `red_solidaria_test` y cuenta declarada en los manifiestos `rs_test@127.0.0.1`.

### Identidades observadas y revertidas

| Escenario | Tabla | PK | Estado |
|---|---|---:|---|
| Fallo en auditoría | `donacion` | 32 | `ROLLED_BACK` |
| Fallo en auditoría | `detalle_donacion` | 33 | `ROLLED_BACK` |
| Fallo en auditoría | `movimiento_donacion` | 55 | `ROLLED_BACK` |
| Fallo en auditoría | `registro_auditoria` | 100 | `ROLLED_BACK` |
| Fallo en movimiento | `donacion` | 31 | `ROLLED_BACK` |
| Fallo en movimiento | `detalle_donacion` | 32 | `ROLLED_BACK` |
| Fallo en movimiento | `movimiento_donacion` | 54 | `ROLLED_BACK` |

| Identificación | Fallo en auditoría | Fallo en movimiento |
|---|---|---|
| Transacción de operación | `d57e35cf-8c21-4818-b646-81e3d3ae4f55` | `5eb54d81-0618-4dbd-ad28-c8dd5e433ae4` |
| Correlación | `482400c0-b0ad-443c-82f8-d83d3ba9e24e` | `44ecee2a-4eee-4635-8ad3-0e3da46556c1` |
| Transacción de preparación | `904f6340-f699-4c78-91bc-f1a5b76b49b7` | `aca6f9cf-199f-4944-be17-5ba0ec0c500d` |

En cada escenario, las identidades del certificado `CONTROLLED_REGISTRATION_ROLLBACK` coinciden exactamente con las entradas `ROLLED_BACK` del ledger y comparten la transacción y correlación indicadas. La transacción de operación tiene nombre `pe.redsolidaria.modules.donations.application.DonationRegistrationService.register`.

### Fixtures confirmados e integridad

| Concepto | Fallo en auditoría | Fallo en movimiento | Total de la suite |
|---|---|---|---:|
| Fixtures `COMMITTED_BASE` | `rol/32`, `usuario/53`, `donante/30`, `centro_acopio/28`, `unidad_medida/28`, `recurso/33` | `rol/31`, `usuario/52`, `donante/29`, `centro_acopio/27`, `unidad_medida/27`, `recurso/32` | 12 |
| `COMMITTED_OPERATION` | 0 | 0 | 0 |
| Commits de preparación | 1 | 1 | 2 |
| Rollbacks de operación | 1 | 1 | 2 |
| Identidades revertidas | 4 | 3 | 7 |

Ambos escenarios registran `outcome=ROLLED_BACK`, `absenceVerified=true`, `existingRowsUnchanged=true` y `masterHealthy=true`, con cierre `integrity-verified`. No hay estados finales `ASSIGNED`, `ACTIVE` o `UNKNOWN`, ni fallos registrados de callbacks o IntegrityGate. `observedCommitted` está vacío y `confirmedRecords` coincide únicamente con los seis fixtures base de cada escenario.

Cada manifiesto final exige **381 identidades únicas** mediante `requiredExistingRows`, contrastadas con las 191 identidades originales de PhaseOne y las 190 de Model B. La preservación durante ambos escenarios está respaldada por los indicadores de integridad. No se reportan filas parciales ni huérfanas; en el escenario de movimiento no aparece ninguna identidad de auditoría observada o confirmada y la ausencia asociada queda respaldada por la certificación.

### Advertencias y límite de la evidencia

Las advertencias de **Mockito/Byte Buddy** sobre carga dinámica de agentes y de **Spring Security** sobre la configuración de autenticación son **no bloqueantes**. No equivalen a errores transaccionales ni invalidan los resultados de Surefire.

**Limitación explícita:** los informes contienen certificados de rollback y ausencia, pero **no exportan todas las imágenes iniciales, trazas individuales de INSERT ni invocaciones literales de `afterRollback`**. La observación de las inserciones y la terminación del rollback están acreditadas por los controles que emitieron los certificados y por los resultados de las pruebas; no se reconstruyen independientemente desde una traza SQL o de callbacks. Tampoco se consultó el estado actual de MySQL ni se inventaron PK o hashes.

Las siete PK revertidas se conservan como evidencia de intentos observados, no como filas confirmadas. No se infiere continuidad de autoincrementos ni se recuperan o reinician sus valores.

## 11. Estado general de validación de integración de la Fase 1

**VALIDACIÓN SATISFACTORIA del alcance cubierto por estas cinco suites MySQL.**

La evidencia histórica y los resultados verificados incorporados acreditan **22 escenarios aprobados**, **418 filas nuevas confirmadas** —**286 base y 132 operaciones**—, **73 commits** y **16 rollbacks**. Atomicity añade **siete identidades observadas y revertidas**, excluidas de los confirmados. PhaseOne y Model B conservan sus **18 cierres de integridad satisfactorios**, sin resultados transaccionales inciertos documentados. DonationQuery, DonationRegistration y ambos escenarios de Atomicity añaden resultados satisfactorios de `existingRowsUnchanged=true` y `masterHealthy=true`. Model B certificó la preservación de las **191 identidades de PhaseOne**; las tres suites siguientes exigieron y preservaron las **381 identidades de PhaseOne y Model B**.

Los **418 registros confirmados corresponden exclusivamente a estas cinco suites**, no al total de registros de `red_solidaria_test`.

No se identifica en la evidencia consolidada un bloqueo de integridad o un fallo técnico que invalide las cinco ejecuciones. Este estado corresponde al alcance probado de la Fase 1; no constituye una certificación completa de todos los módulos ni una autorización de despliegue.
