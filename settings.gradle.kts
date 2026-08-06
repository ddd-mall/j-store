pluginManagement {
    plugins {
        kotlin("plugin.lombok") version "2.3.0"
        kotlin("kapt") version "2.3.0"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "j-store"

include("j-store-boot")

include("j-store-common-core")

include("j-store-common-spring")

include("j-store-integration-contracts")

include("j-store-order-domain")

include("j-store-order-application")

include("j-store-order-infrastructure")

include("j-store-order-boot")

include("j-store-goods-api")

include("j-store-goods-domain")

include("j-store-goods-application")

include("j-store-goods-infrastructure")

include("j-store-goods-boot")

include("j-store-user-domain")

include("j-store-user-application")

include("j-store-user-infrastructure")

include("j-store-user-boot")

include("j-store-shop-domain")

include("j-store-shop-api")

include("j-store-shop-application")

include("j-store-shop-infrastructure")

include("j-store-shop-boot")

include("j-store-authentication-spring-sdk")

include("j-store-accounting-domain")

include("j-store-accounting-application")

include("j-store-accounting-infrastructure")

include("j-store-accounting-boot")

include("j-store-payment-domain")

include("j-store-payment-application")

include("j-store-payment-infrastructure")

include("j-store-payment-boot")

include("j-store-fulfillment-domain")

include("j-store-fulfillment-application")

include("j-store-fulfillment-infrastructure")

include("j-store-fulfillment-boot")

include("j-store-inventory-domain")

include("j-store-inventory-application")

include("j-store-inventory-infrastructure")

include("j-store-inventory-boot")

include("j-store-warehouse-domain")

include("j-store-warehouse-application")

include("j-store-warehouse-infrastructure")

include("j-store-warehouse-boot")

include("j-store-admin-boot")
