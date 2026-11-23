package pe.redsolidaria.test;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.boot.context.properties.source.*;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.hibernate.jpa.boot.spi.IntegratorProvider;

/** Explicit test datasource: no Boot DataSourceProperties/Hikari binding and no eager pool startup. */
@TestConfiguration(proxyBeanMethods=false)
public class ControlledMySqlConfiguration {
    @Bean(destroyMethod="close")
    GuardedDataSource dataSource(ConfigurableEnvironment environment) {
        return create(environment,System.getenv());
    }
    static GuardedDataSource create(ConfigurableEnvironment environment,Map<String,String> variables) {
        var settings=ControlledTestMySqlCondition.settings(variables);
        validate(environment,settings);
        HikariDataSource pool=new HikariDataSource(); // The no-argument constructor starts no threads/connections.
        pool.setJdbcUrl(settings.url()); pool.setUsername(settings.username()); pool.setPassword(settings.password());
        pool.setDriverClassName("com.mysql.cj.jdbc.Driver");
        pool.setConnectionTimeout(5000); pool.setValidationTimeout(5000);
        pool.setMaximumPoolSize(8); pool.setMinimumIdle(0);
        pool.addDataSourceProperty("socketTimeout","10000");
        return new GuardedDataSource(pool,environment,settings);
    }
    static void validate(ConfigurableEnvironment environment,ControlledTestMySqlCondition.Settings settings) {
        Map<String,String> expected=Map.of("url",settings.url(),"username",settings.username(),"password",settings.password());
        // Effective standard properties must agree with the dynamic TEST_DB_* properties.
        expected.forEach((key,value) -> {
            if (!value.equals(environment.getProperty("spring.datasource."+key)))
                throw new IllegalStateException("Blocked: conflicting or absent spring.datasource."+key);
        });
        // Inspect every source, including shadowed profile, JSON, system and relaxed environment values.
        // Hikari options are not bound at all; reject alternative connection/initialization mechanisms too.
        for (ConfigurationPropertySource source:ConfigurationPropertySources.get(environment)) {
            for (String key:List.of("jdbc-url","username","password","data-source-class-name","data-source-jndi",
                    "connection-init-sql","schema","catalog","driver-class-name","data-source-properties.user",
                    "data-source-properties.username","data-source-properties.password","data-source-properties.url")) {
                var property=source.getConfigurationProperty(ConfigurationPropertyName.of("spring.datasource.hikari."+key));
                if (property==null) continue;
                String wanted=switch(key) { case "jdbc-url" -> settings.url(); case "username" -> settings.username();
                    case "password" -> settings.password(); default -> null; };
                if (wanted==null || !wanted.equals(environment.resolveRequiredPlaceholders(property.getValue().toString())))
                    throw new IllegalStateException("Blocked: incompatible spring.datasource.hikari."+key);
            }
        }
    }
    static final class GuardedDataSource extends AbstractDataSource implements AutoCloseable {
        private final HikariDataSource pool;
        private final ConfigurableEnvironment environment;
        private final ControlledTestMySqlCondition.Settings settings;
        GuardedDataSource(HikariDataSource pool,ConfigurableEnvironment environment,ControlledTestMySqlCondition.Settings settings) {
            this.pool=pool; this.environment=environment; this.settings=settings;
        }
        @Override public Connection getConnection() throws SQLException {
            validate(environment,settings);
            if (!settings.url().equals(pool.getJdbcUrl()) || !settings.username().equals(pool.getUsername())
                    || !settings.password().equals(pool.getPassword()) || pool.getDataSource()!=null
                    || pool.getDataSourceClassName()!=null || pool.getDataSourceJNDI()!=null
                    || pool.getConnectionInitSql()!=null || pool.getSchema()!=null || pool.getCatalog()!=null
                    || !"com.mysql.cj.jdbc.Driver".equals(pool.getDriverClassName())
                    || !pool.getDataSourceProperties().equals(socketProperties()))
                throw new IllegalStateException("Blocked: physical pool configuration changed before connection");
            return pool.getConnection();
        }
        private static Properties socketProperties() { Properties p=new Properties(); p.setProperty("socketTimeout","10000"); return p; }
        @Override public Connection getConnection(String user,String password) { throw new IllegalStateException("Explicit credentials forbidden"); }
        @Override public <T> T unwrap(Class<T> type) throws SQLException { throw new SQLException("Pool unwrapping forbidden"); }
        @Override public boolean isWrapperFor(Class<?> type) { return false; }
        @Override public void close() { pool.close(); }
    }
    @Bean HibernatePropertiesCustomizer controlledInsertProvenance() {
        return properties -> {
            validateHibernate(properties);
            if (properties.containsKey("hibernate.integrator_provider"))
                throw new IllegalStateException("An alternate Hibernate integrator is not allowed in the controlled test");
            properties.put("hibernate.integrator_provider",(IntegratorProvider) () -> List.of(new ControlledInsertObserver()));
        };
    }
    static void validateHibernate(Map<String,Object> properties) {
        for (String key:properties.keySet()) if (key.startsWith("hibernate.connection.")
                || key.startsWith("hibernate.hikari.") || key.startsWith("hibernate.c3p0.")
                || key.startsWith("jakarta.persistence.jdbc.") || key.startsWith("javax.persistence.jdbc.") || key.endsWith("DataSource")
                || key.endsWith("schema-generation-connection") || key.endsWith("schema-generation.connection")
                || key.equals("hibernate.multi_tenant_connection_provider"))
            throw new IllegalStateException("Alternative Hibernate connection settings forbidden: "+key);
        properties.put("hibernate.hbm2ddl.auto","validate");
        // JPA's database.action takes precedence over hbm2ddl.auto, even when its value is "none".
        properties.remove("jakarta.persistence.schema-generation.database.action");
        properties.remove("javax.persistence.schema-generation.database.action");
        properties.put("jakarta.persistence.schema-generation.scripts.action","none");
    }
}
