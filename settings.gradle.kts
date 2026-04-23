plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "j-store"
include("j-store-common-core")
include("j-store-common-spring-starter")
include("j-store-order")
include("j-store-order-boot")
include("j-store-goods")
include("j-store-order-infrastructure")
include("j-store-goods-infrastructure")
