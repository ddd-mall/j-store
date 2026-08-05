package com.jstore.common.geo

// Feature: geo-address-i18n, Property 10: JSON 序列化往返
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.Locale

/**
 * Property 10: JSON 序列化往返
 *
 * For any legal I18nGeoAddress, serializing to JSON then deserializing back should produce an
 * object equal to the original.
 *
 * **Validates: Requirements 9.1, 9.2, 9.3, 9.4**
 */
class I18nGeoAddressSerializationPropertyTest :
    FunSpec({
        val objectMapper =
            ObjectMapper().apply {
                registerModule(KotlinModule.Builder().build())
            }

        // Use Locales that round-trip correctly through toLanguageTag() / forLanguageTag()
        val supportedLocales =
            listOf(
                Locale.SIMPLIFIED_CHINESE, // zh-CN
                Locale.US, // en-US
                Locale.JAPAN, // ja-JP
                Locale.KOREA, // ko-KR
                Locale.FRANCE, // fr-FR
                Locale.GERMANY, // de-DE
                Locale.UK, // en-GB
                Locale.ITALY, // it-IT
                Locale.CANADA, // en-CA
                Locale.TRADITIONAL_CHINESE, // zh-TW
            )

        val validCountryCodes =
            listOf(CountryCode.CN, CountryCode.US, CountryCode.JP, CountryCode.SG)

        fun Arb.Companion.addressComponent(): Arb<AddressComponent> {
            return Arb.int(1..5).flatMap { nameCount ->
                val count = nameCount.coerceAtMost(supportedLocales.size)
                Arb.list(Arb.element(supportedLocales), count..count)
                    .map { it.distinct() }
                    .filter { it.isNotEmpty() }
                    .flatMap { locales ->
                        Arb.list(Arb.string(1..20), locales.size..locales.size).flatMap { names ->
                            val namesMap = locales.zip(names).toMap()
                            Arb.element(locales).flatMap { defaultLocale ->
                                Arb.string(1..10)
                                    .filter { it.isNotBlank() }
                                    .flatMap { code ->
                                        Arb.int(0..5).map { depth ->
                                            AddressComponent(
                                                code = code,
                                                level = DivisionLevel(depth, "Level$depth"),
                                                names = namesMap,
                                                defaultLocale = defaultLocale,
                                            )
                                        }
                                    }
                            }
                        }
                    }
            }
        }

        fun Arb.Companion.i18nGeoAddress(): Arb<I18nGeoAddress> {
            return Arb.element(validCountryCodes).flatMap { countryCode ->
                Arb.list(Arb.addressComponent(), 1..4).map { components ->
                    I18nGeoAddress(
                        countryCode = countryCode,
                        components = components,
                    )
                }
            }
        }

        // Feature: geo-address-i18n, Property 10: JSON 序列化往返
        test("JSON serialization round-trip preserves equality") {
            // **Validates: Requirements 9.1, 9.2, 9.3, 9.4**
            checkAll(100, Arb.i18nGeoAddress()) { original ->
                val json = objectMapper.writeValueAsString(original)
                val deserialized = objectMapper.readValue<I18nGeoAddress>(json)
                deserialized shouldBe original
            }
        }

        // Feature: geo-address-i18n, Property 11: JSON 反序列化缺失字段错误处理
        test("JSON deserialization with missing required fields produces descriptive error") {
            // **Validates: Requirements 9.5**
            val fieldsToRemove = listOf("countryCode", "components")

            checkAll(100, Arb.i18nGeoAddress(), Arb.element(fieldsToRemove)) {
                address,
                fieldToRemove ->
                val fullJson = objectMapper.writeValueAsString(address)
                val jsonTree =
                    objectMapper.readTree(fullJson)
                        as com.fasterxml.jackson.databind.node.ObjectNode
                jsonTree.remove(fieldToRemove)
                val incompleteJson = objectMapper.writeValueAsString(jsonTree)

                val exception =
                    io.kotest.assertions.throwables.shouldThrow<Exception> {
                        objectMapper.readValue<I18nGeoAddress>(incompleteJson)
                    }
                exception.message.shouldNotBeNull()
                exception.message!!.isNotBlank() shouldBe true
            }
        }
    })
