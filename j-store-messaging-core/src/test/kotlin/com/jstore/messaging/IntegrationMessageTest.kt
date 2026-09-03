/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.messaging

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class IntegrationMessageTest {
    private val occurredAt = Instant.parse("2026-08-12T08:00:00Z")

    @Test
    fun `command deadline cannot precede the fact that caused it`() {
        assertFailsWith<IllegalArgumentException> {
            IntegrationMessageMetadata(
                messageId = "message-1",
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                occurredAt = occurredAt,
                partitionKey = "order-42",
                correlationId = "checkout-42",
                acceptBefore = occurredAt.minusMillis(1),
            )
        }
    }

    @Test
    fun `merchant and deployment scopes are independent optional metadata`() {
        val metadata =
            IntegrationMessageMetadata(
                messageId = "message-1",
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                occurredAt = occurredAt,
                partitionKey = "order-42",
                correlationId = "checkout-42",
                merchantScopeId = "merchant-7",
                deploymentScopeId = "site-jp",
            )

        assertEquals("merchant-7", metadata.merchantScopeId)
        assertEquals("site-jp", metadata.deploymentScopeId)
    }

    @Test
    fun `blank merchant or deployment scope is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            validMetadata(merchantScopeId = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            validMetadata(deploymentScopeId = " ")
        }
    }

    @Test
    fun `source aware message id is stable across retries and unique across sources`() {
        val first =
            stableIntegrationMessageId(
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                sourceMessageId = "order-authorized-42",
                businessKey = "order-42",
            )

        assertEquals(
            first,
            stableIntegrationMessageId(
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                sourceMessageId = "order-authorized-42",
                businessKey = "order-42",
            ),
        )
        assertNotEquals(
            first,
            stableIntegrationMessageId(
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                sourceMessageId = "another-order-authorized-42",
                businessKey = "order-42",
            ),
        )
    }

    @Test
    fun `message id canonicalization keeps delimited source and business keys distinct`() {
        assertNotEquals(
            stableIntegrationMessageId(
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                sourceMessageId = "source|segment",
                businessKey = "order",
            ),
            stableIntegrationMessageId(
                messageName = "inventory.reserve.requested",
                messageVersion = 1,
                sourceMessageId = "source",
                businessKey = "segment|order",
            ),
        )
    }

    private fun validMetadata(
        merchantScopeId: String? = null,
        deploymentScopeId: String? = null,
    ) =
        IntegrationMessageMetadata(
            messageId = "message-1",
            messageName = "inventory.reserve.requested",
            messageVersion = 1,
            occurredAt = occurredAt,
            partitionKey = "order-42",
            correlationId = "checkout-42",
            merchantScopeId = merchantScopeId,
            deploymentScopeId = deploymentScopeId,
        )
}
