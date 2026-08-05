package com.jstore.common.framework.messaging

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton

class BrokerTransportModeGuard(
    private val properties: MessagingProperties,
    private val transportProvider: ObjectProvider<BrokerIntegrationMessageTransport>,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        if (
            properties.mode == IntegrationMessagingMode.BROKER ||
                properties.mode == IntegrationMessagingMode.HYBRID
        ) {
            check(transportProvider.getIfAvailable() != null) {
                "jstore.messaging.mode=${properties.mode.name.lowercase()} requires exactly one " +
                    "BrokerIntegrationMessageTransport bean"
            }
            check(transportProvider.orderedStream().limit(2).count() == 1L) {
                "jstore.messaging.mode=${properties.mode.name.lowercase()} requires exactly one " +
                    "BrokerIntegrationMessageTransport bean"
            }
        }
    }
}
