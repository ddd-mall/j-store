package com.jstore.user

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.Nickname
import com.jstore.user.domain.useraccount.Password
import com.jstore.user.domain.useraccount.UserAccountImpl
import com.jstore.user.domain.useraccount.UserAccountRepositoryImpl
import com.jstore.user.domain.useraccount.UserAccountStatus
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.domain.useraccount.persistence.UserAccountPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class UserAccountRepositoryPostgresTest {
    @Test
    fun `application assigned user id survives a PostgreSQL round trip`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            val factoryBean =
                LocalContainerEntityManagerFactoryBean().apply {
                    dataSource = postgres.postgresDatabase
                    jpaVendorAdapter = HibernateJpaVendorAdapter()
                    setPackagesToScan("com.jstore.user.domain.useraccount.persistence")
                    setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
                    afterPropertiesSet()
                }
            val factory = requireNotNull(factoryBean.`object`)
            val entityManager = factory.createEntityManager()
            try {
                entityManager.transaction.begin()
                val jpaRepository =
                    JpaRepositoryFactory(entityManager)
                        .getRepository(UserAccountPOJpaRepository::class.java)
                val repository = UserAccountRepositoryImpl(jpaRepository)
                val userId = UserId(987654321L)

                repository.add(
                    UserAccountImpl(
                        id = userId,
                        phoneNumber = PhoneNumber("+8613800138000"),
                        nickname = Nickname("postgres-user"),
                        passwordHash = Password("hash"),
                        status = UserAccountStatus.ACTIVE,
                    )
                )
                entityManager.flush()
                entityManager.clear()

                val restored = assertNotNull(repository.findById(userId))
                assertEquals(userId, restored.id)
                assertEquals("+8613800138000", restored.phoneNumber.value)
                entityManager.transaction.commit()
            } finally {
                if (entityManager.transaction.isActive) entityManager.transaction.rollback()
                entityManager.close()
                factoryBean.destroy()
            }
        }
    }
}
