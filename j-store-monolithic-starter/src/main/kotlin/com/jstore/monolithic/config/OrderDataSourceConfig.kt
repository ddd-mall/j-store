package com.jstore.monolithic.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * 订单模块数据源配置
 *
 * 配置说明：
 * 1. 使用 @Primary 注解标记为主数据源
 * 2. entityManagerFactoryRef: 指定EntityManagerFactory的Bean名称
 * 3. transactionManagerRef: 指定事务管理器的Bean名称
 * 4. basePackages: 指定Repository接口所在的包路径
 * 5. 需要在application.yml中配置对应的数据源属性
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = [
        "com.jstore.order.domain.order.persistence",
        "com.jstore.order.domain.inventory.persistent"
    ],
    entityManagerFactoryRef = "orderEntityManagerFactory",
    transactionManagerRef = "orderTransactionManager"
)
class OrderDataSourceConfig {

    /**
     * 订单数据源属性配置
     * 从 spring.datasource.order 读取配置
     */
    @Primary
    @Bean(name = ["orderDataSourceProperties"])
    @ConfigurationProperties("spring.datasource.order")
    fun orderDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    /**
     * 订单数据源
     */
    @Primary
    @Bean(name = ["orderDataSource"])
    fun orderDataSource(
        @Qualifier("orderDataSourceProperties") dataSourceProperties: DataSourceProperties
    ): DataSource {
        val hikariDataSource = dataSourceProperties.initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build() as HikariDataSource

        // 配置HikariCP连接池参数
        hikariDataSource.apply {
            maximumPoolSize = 10
            minimumIdle = 5
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            poolName = "OrderHikariPool"
        }

        return hikariDataSource
    }

    /**
     * 订单EntityManagerFactory
     *
     * @param dataSource 订单数据源
     */
    @Primary
    @Bean(name = ["orderEntityManagerFactory"])
    fun orderEntityManagerFactory(
        @Qualifier("orderDataSource") dataSource: DataSource
    ): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = dataSource
        factory.setPackagesToScan(
            "com.jstore.order.domain.order.persistence",
            "com.jstore.order.domain.inventory.persistent"
        )
        factory.persistenceUnitName = "orderPU"

        // 设置JPA厂商适配器
        val vendorAdapter = org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter()
        vendorAdapter.setGenerateDdl(true)
        vendorAdapter.setShowSql(true)
        factory.jpaVendorAdapter = vendorAdapter

        // 设置JPA属性
        val properties = HashMap<String, Any>()
        properties["hibernate.hbm2ddl.auto"] = "update"
        properties["hibernate.dialect"] = "org.hibernate.dialect.PostgreSQLDialect"
        properties["hibernate.show_sql"] = "true"
        properties["hibernate.format_sql"] = "true"
        factory.setJpaPropertyMap(properties)

        return factory
    }

    /**
     * 订单事务管理器
     */
    @Primary
    @Bean(name = ["orderTransactionManager"])
    fun orderTransactionManager(
        @Qualifier("orderEntityManagerFactory") entityManagerFactory: EntityManagerFactory
    ): PlatformTransactionManager {
        return JpaTransactionManager(entityManagerFactory)
    }
}
