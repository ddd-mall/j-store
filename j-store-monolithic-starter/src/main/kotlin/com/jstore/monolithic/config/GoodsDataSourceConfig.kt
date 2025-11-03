package com.jstore.monolithic.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource

/**
 * 商品模块数据源配置
 *
 * 配置说明：
 * 1. 不使用 @Primary 注解，作为辅助数据源
 * 2. entityManagerFactoryRef: 指定EntityManagerFactory的Bean名称
 * 3. transactionManagerRef: 指定事务管理器的Bean名称
 * 4. basePackages: 指定Repository接口所在的包路径
 * 5. 需要在application.yml中配置对应的数据源属性
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = [
        "com.jstore.goods.domain.inventory.persistence",
        "com.jstore.goods.domain.commodity.persistence"
    ],
    entityManagerFactoryRef = "goodsEntityManagerFactory",
    transactionManagerRef = "goodsTransactionManager"
)
class GoodsDataSourceConfig {

    /**
     * 商品数据源属性配置
     * 从 spring.datasource.goods 读取配置
     */
    @Bean(name = ["goodsDataSourceProperties"])
    @ConfigurationProperties("spring.datasource.goods")
    fun goodsDataSourceProperties(): DataSourceProperties {
        return DataSourceProperties()
    }

    /**
     * 商品数据源
     */
    @Bean(name = ["goodsDataSource"])
    fun goodsDataSource(
        @Qualifier("goodsDataSourceProperties") dataSourceProperties: DataSourceProperties
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
            poolName = "GoodsHikariPool"
        }

        return hikariDataSource
    }

    /**
     * 商品EntityManagerFactory
     *
     * @param dataSource 商品数据源
     */
    @Bean(name = ["goodsEntityManagerFactory"])
    fun goodsEntityManagerFactory(
        @Qualifier("goodsDataSource") dataSource: DataSource
    ): LocalContainerEntityManagerFactoryBean {
        val factory = LocalContainerEntityManagerFactoryBean()
        factory.dataSource = dataSource
        factory.setPackagesToScan(
            "com.jstore.goods.domain.inventory.persistence",
            "com.jstore.goods.domain.commodity.persistence"
        )
        factory.persistenceUnitName = "goodsPU"

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
     * 商品事务管理器
     */
    @Bean(name = ["goodsTransactionManager"])
    fun goodsTransactionManager(
        @Qualifier("goodsEntityManagerFactory") entityManagerFactory: EntityManagerFactory
    ): PlatformTransactionManager {
        return JpaTransactionManager(entityManagerFactory)
    }
}
