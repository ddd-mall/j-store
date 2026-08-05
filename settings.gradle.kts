plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "j-store"

include("j-store-boot")

include("j-store-common-core")

include("j-store-common-spring")

include("j-store-integration-contracts")

include("j-store-order")

include("j-store-order-infrastructure")

include("j-store-goods-api")

include("j-store-goods")

include("j-store-goods-infrastructure")

include("j-store-user")

include("j-store-user-infrastructure")

include("j-store-shop")

include("j-store-authentication-spring-sdk")

include("j-store-accounting")

include("j-store-shop-infrastructure")

include("j-store-accounting-infrastructure")

include("j-store-payment")

include("j-store-payment-infrastructure")

include("j-store-fulfillment")

include("j-store-fulfillment-infrastructure")

include("j-store-warehouse")

include("j-store-warehouse-infrastructure")

include("j-store-admin-boot")
