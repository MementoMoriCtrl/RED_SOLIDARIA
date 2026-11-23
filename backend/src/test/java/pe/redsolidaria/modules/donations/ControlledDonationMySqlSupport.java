package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.redsolidaria.test.*;

/** Shared lifecycle and fixture factory. This is support code, never an integration test entry point. */
@Import(ControlledMySqlConfiguration.class)
@ExtendWith(ControlledTestMySqlCondition.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("red_solidaria_test")
@Isolated("Controlled MySQL snapshots must not overlap another JUnit test class")
@WithMockUser(authorities="ACCESO_ADMINISTRAR")
abstract class ControlledDonationMySqlSupport {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        ControlledTestMySqlCondition.requireVerifiedEnvironment();
        registry.add("spring.datasource.url",() -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username",() -> System.getenv("TEST_DB_USERNAME"));
        registry.add("spring.datasource.password",() -> System.getenv("TEST_DB_PASSWORD"));
        registry.add("spring.flyway.enabled",() -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto",() -> "validate");
        registry.add("spring.sql.init.mode",() -> "never");
        registry.add("spring.transaction.default-timeout",() -> "10s");
        registry.add("spring.jdbc.template.query-timeout",() -> "10s");
        registry.add("spring.jpa.properties.jakarta.persistence.query.timeout",() -> "10000");
        registry.add("spring.datasource.hikari.connection-timeout",() -> "5000");
        registry.add("spring.datasource.hikari.data-source-properties.socketTimeout",() -> "10000");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tx;
    long donationId,lineId,resourceId,emergencyId,need1,need2,distributionId;
    long actorId,registrarId,unitId,collectionId;
    long registrationDonorId;
    String email;
    protected ControlledFixtureAudit fixtureAudit;
    protected final ControlledConcurrency concurrency=new ControlledConcurrency();
    private String correlationId,scenario;
    private final Map<Long,Long> additionalLines=new LinkedHashMap<>();

    @BeforeEach final void prepareProtectedScenario(TestInfo info) {
        ControlledFixtureAudit.initializeScenario(() -> {
            concurrency.assertQuiescent();
            correlationId=UUID.randomUUID().toString();
            scenario=info.getTestClass().orElseThrow().getSimpleName()+"."+info.getTestMethod().orElseThrow().getName();
            fixtureAudit=new ControlledFixtureAudit(jdbc,tx,correlationId,scenario);
            fixtureAudit.requireExistingRows(requiredExistingRows());
            observeJdbcRepositories();
            fixturesInDedicatedDatabaseOnly();
            fixtureAudit.announce();
            prepareOperationFixtures();
            fixtureAudit.requireReadyForScenario();
            return fixtureAudit;
        });
    }
    @AfterEach final void verifyProtectedScenario() {
        try {
            concurrency.assertQuiescent();
            if (fixtureAudit!=null) fixtureAudit.finish();
        } catch (RuntimeException | Error failure) {
            ControlledFixtureAudit.blockFurtherScenarios("scenario-finalization",failure); throw failure;
        } finally { if (fixtureAudit!=null) fixtureAudit.close(); }
    }
    protected Map<String,Set<String>> requiredExistingRows() { return Map.of(); }
    protected void observeJdbcRepositories() {}
    protected void prepareAdditionalBaseFixtures(String scenario) {}
    protected void prepareOperationFixtures() {}
    protected boolean registrationFixturesOnly() { return false; }
    final void prepareRegistrationFixtures(String token) {
        long role=insert("insert into rol(codigo,nombre) values (?,?)","IT-"+token,"Registration fixture");
        email=token+"@integration.test";
        actorId=insert("insert into usuario(id_rol,nombre,apellido,correo,password_hash,estado) values (?,?,?,?,?,?)",
            role,"Registration","Fixture",email,"unused-no-login","ACTIVO");
        registrationDonorId=insert("insert into donante(nombre) values (?)","IT-"+token);
        collectionId=insert("insert into centro_acopio(nombre) values (?)","IT-acopio-"+token);
        unitId=insert("insert into unidad_medida(codigo,nombre) values (?,?)",token.substring(0,30),"Registration unit");
        resourceId=insert("insert into recurso(id_unidad_medida,nombre,categoria,estado) values (?,?,?,?)",
            unitId,"IT-"+token,"IT","ACTIVO");
        fixtureAudit.authorizeRegistration(new RegistrationScenarioRelations(role,actorId,registrationDonorId,collectionId,unitId,resourceId));
    }
    protected final void authorizeAdditionalLine(long detail,long resource) {
        if (additionalLines.putIfAbsent(detail,resource)!=null) throw new AssertionError("Duplicate additional fixture line");
    }
    private void fixturesInDedicatedDatabaseOnly() {
        Objects.requireNonNull(fixtureAudit,"Controlled audit must exist before fixture preparation");
        concurrency.assertQuiescent(); ControlledFixtureAudit.assertSafeToStart();
        ControlledTestMySqlCondition.verifyDataSource(jdbc.getDataSource());
        assertEquals("red_solidaria_test",jdbc.queryForObject("select database()",String.class));
        assertEquals(5L,jdbc.queryForObject("select count(*) from flyway_schema_history where version in ('1','2','3','4','5') and success = 1",Long.class));
        TransactionTemplate preparation=new TransactionTemplate(tx); preparation.setName("controlled-fixture.preparation/"+scenario);
        preparation.executeWithoutResult(status -> {
            String token=Objects.requireNonNull(correlationId,"Scenario correlation required");
            if (registrationFixturesOnly()) { prepareRegistrationFixtures(token); return; }
            email=token+"@integration.test";
            long role=insert("insert into rol(codigo,nombre) values (?,?)","IT-"+token,"Integration fixture");
            actorId=insert("insert into usuario(id_rol,nombre,apellido,correo,password_hash,estado) values (?,?,?,?,?,?)",
                role,"Integration","Fixture",email,"unused-no-login","ACTIVO");
            registrarId=insert("insert into usuario(id_rol,nombre,apellido,correo,password_hash,estado) values (?,?,?,?,?,?)",
                role,"Registrador","Fixture",token+"-registrar@integration.test","unused-no-login","ACTIVO");
            long donor=insert("insert into donante(nombre) values (?)","IT-"+token);
            unitId=insert("insert into unidad_medida(codigo,nombre) values (?,?)",token.substring(0,30),"Integration unit");
            resourceId=insert("insert into recurso(id_unidad_medida,nombre,categoria,estado) values (?,?,?,?)",unitId,"IT-"+token,"IT","ACTIVO");
            emergencyId=insert("insert into emergencia(nombre,tipo,fecha_inicio,estado) values (?,?,?,?)",
                "IT-"+token,"TEST",java.sql.Date.valueOf("2026-01-01"),"ACTIVA");
            long zone=insert("insert into zona_afectada(id_emergencia,nombre) values (?,?)",emergencyId,"Integration zone");
            collectionId=insert("insert into centro_acopio(nombre) values (?)","IT-acopio-"+token);
            distributionId=insert("insert into centro_distribucion(nombre) values (?)","IT-distribucion-"+token);
            donationId=insert("insert into donacion(id_donante,id_emergencia,id_centro_acopio,id_usuario_registro,codigo_trazabilidad,estado) values (?,?,?,?,?,?)",
                donor,emergencyId,collectionId,registrarId,"IT-"+token,"REGISTRADA");
            lineId=insert("insert into detalle_donacion(id_donacion,id_recurso,cantidad_donada) values (?,?,?)",donationId,resourceId,new BigDecimal("10.000"));
            need1=insert("insert into necesidad(id_zona_afectada,id_recurso,cantidad_requerida,prioridad,estado) values (?,?,?,?,?)",zone,resourceId,8,"ALTA","PENDIENTE");
            need2=insert("insert into necesidad(id_zona_afectada,id_recurso,cantidad_requerida,prioridad,estado) values (?,?,?,?,?)",zone,resourceId,8,"ALTA","PENDIENTE");
            prepareAdditionalBaseFixtures(scenario);
            fixtureAudit.authorizeRelations(new ScenarioRelations(donationId,actorId,lineId,resourceId,
                unitId,collectionId,distributionId,Set.of(need1,need2),additionalLines,registrarId));
        });
    }
    final long insert(String sql,Object... args) {
        return Objects.requireNonNull(fixtureAudit,"Controlled audit required").insertJdbcBase(sql,args);
    }

    final BigDecimal received() { return jdbc.queryForObject("select cantidad_recibida_acopio from detalle_donacion where id_detalle_donacion=?",BigDecimal.class,lineId); }
    final long operationCount() { return jdbc.queryForObject("select count(*) from recepcion_operacion where id_donacion=?",Long.class,donationId); }
    final List<Object> parallel(Callable<?> first,Callable<?> second) { return concurrency.run(first,second); }
}
