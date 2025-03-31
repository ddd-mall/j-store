plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "j-store"
include("j-store-common")
include("j-store-order")
include("j-store-order-boot")
include("j-store-goods")
include("j-store-goods-boot")
include("j-store-service-center")
include("j-store-common-spring")
