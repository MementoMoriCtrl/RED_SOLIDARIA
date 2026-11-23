package pe.redsolidaria.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class ControlledMySqlConfigurationTest {
    Map<String,String> variables() { return Map.of("TEST_DB_URL",ControlledTestMySqlCondition.URL,"TEST_DB_USERNAME","rs_test","TEST_DB_PASSWORD","unit-only-secret"); }
    MockEnvironment environment() {
        return new MockEnvironment().withProperty("spring.datasource.url",ControlledTestMySqlCondition.URL)
            .withProperty("spring.datasource.username","rs_test").withProperty("spring.datasource.password","unit-only-secret");
    }
    @ParameterizedTest @ValueSource(strings={"jdbc-url","username","password","data-source-class-name","data-source-jndi",
        "connection-init-sql","schema","catalog","driver-class-name","data-source-properties.user","data-source-properties.username",
        "data-source-properties.password","data-source-properties.url"})
    void incompatibleHikariSettingsFailBeforePoolConstruction(String key) {
        var env=environment().withProperty("spring.datasource.hikari."+key,"incompatible");
        var problem=assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.create(env,variables()));
        assertFalse(problem.toString().contains("unit-only-secret"));
    }
    @ParameterizedTest @ValueSource(strings={"url","username","password"})
    void incompatibleEffectiveStandardPropertiesAreRejected(String key) {
        var env=environment().withProperty("spring.datasource."+key,"root-or-production");
        assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.create(env,variables()));
    }
    @Test void shadowedProfileAndRelaxedSystemEnvironmentCannotHideContradictions() {
        var env=environment().withProperty("spring.datasource.hikari.jdbc-url",ControlledTestMySqlCondition.URL);
        env.getPropertySources().addLast(new MapPropertySource("external-profile",Map.of("spring.datasource.hikari.jdbc-url","jdbc:mysql://localhost/red_solidaria")));
        assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.create(env,variables()));
        var system=environment();
        system.getPropertySources().addLast(new SystemEnvironmentPropertySource("systemEnvironment",Map.of("SPRING_DATASOURCE_HIKARI_USERNAME","root")));
        assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.create(system,variables()));
    }
    @ParameterizedTest @ValueSource(strings={"TEST_DB_URL","TEST_DB_USERNAME","TEST_DB_PASSWORD"})
    void absentOrBlankCredentialsCannotFallBackToProductionVariables(String key) {
        var vars=new HashMap<>(variables()); vars.remove(key);
        vars.putAll(Map.of("DB_URL",ControlledTestMySqlCondition.URL,"DB_USERNAME","root","DB_PASSWORD","ignored"));
        assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.create(environment(),vars));
        vars.put(key," "); assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.create(environment(),vars));
    }
    @Test void creatingTheRealDatasourceDoesNotStartItsPoolAndItCannotBeUnwrapped() throws Exception {
        try (var source=ControlledMySqlConfiguration.create(environment(),variables())) {
            assertFalse(source.isWrapperFor(HikariDataSource.class));
            assertThrows(java.sql.SQLException.class,() -> source.unwrap(HikariDataSource.class));
            assertThrows(IllegalStateException.class,() -> source.getConnection("root","ignored"));
        }
    }
    @Test void lateExternalSubstitutionFailsWithoutAskingThePoolForAConnection() throws Exception {
        var env=environment(); var pool=mock(HikariDataSource.class);
        var source=new ControlledMySqlConfiguration.GuardedDataSource(pool,env,ControlledTestMySqlCondition.settings(variables()));
        env.withProperty("spring.datasource.hikari.username","root");
        assertThrows(IllegalStateException.class,source::getConnection);
        verifyNoInteractions(pool);
    }
    @Test void physicalPoolSubstitutionFailsBeforeGetConnection() throws Exception {
        var pool=mock(HikariDataSource.class); when(pool.getJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1/red_solidaria");
        var source=new ControlledMySqlConfiguration.GuardedDataSource(pool,environment(),ControlledTestMySqlCondition.settings(variables()));
        assertThrows(IllegalStateException.class,source::getConnection);
        verify(pool,never()).getConnection();
    }
    @ParameterizedTest @ValueSource(strings={"hibernate.connection.url","hibernate.connection.username","hibernate.connection.provider_class",
        "jakarta.persistence.jdbc.url","jakarta.persistence.nonJtaDataSource","hibernate.multi_tenant_connection_provider"})
    void hibernateCannotSelectAnotherConnectionProvider(String key) {
        assertThrows(IllegalStateException.class,() -> ControlledMySqlConfiguration.validateHibernate(new HashMap<>(Map.of(key,"external"))));
    }
    @Test void schemaGenerationCannotBeEnabledByVendorProperties() {
        Map<String,Object> properties=new HashMap<>(Map.of("hibernate.hbm2ddl.auto","create-drop"));
        new ControlledMySqlConfiguration().controlledInsertProvenance().customize(properties);
        assertEquals("validate",properties.get("hibernate.hbm2ddl.auto"));
        assertFalse(properties.containsKey("jakarta.persistence.schema-generation.database.action"));
        assertEquals("none",properties.get("jakarta.persistence.schema-generation.scripts.action"));
        assertInstanceOf(org.hibernate.jpa.boot.spi.IntegratorProvider.class,properties.get("hibernate.integrator_provider"));
    }
}
