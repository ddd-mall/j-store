plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "j-store"
include("j-store-boot")
include("j-store-common-core")
include("j-store-common-spring")
include("j-store-order")
include("j-store-order-infrastructure")
include("j-store-goods")
include("j-store-goods-infrastructure")
include("j-store-user")
include("j-store-user-infrastructure")