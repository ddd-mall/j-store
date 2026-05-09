package com.jstore.common.geo

// Feature: geo-address-i18n, Property 3: Locale 名称解析与回退
import io.kotest.core.spec.style.FunSpec
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
 * Property 3: Locale 名称解析与回退
 *
 * For any AddressComponent and any Locale, if that Locale exists in the names map,
 * getName(locale) returns the corresponding name; if it does not exist,
 * getName(locale) falls back to the defaultLocale name.
 *
 * **Validates: Requirements 3.2, 3.3**
 */
class AddressComponentPropertyTest : FunSpec({

    val supportedLocales = listOf(
        Locale.SIMPLIFIED_CHINESE,
        Locale.US,
        Locale.JAPAN,
        Locale.KOREA,
        Locale.FRANCE,
        Locale.GERMANY,
        Locale.UK,
        Locale.ITALY,
        Locale.CANADA,
        Locale.TRADITIONAL_CHINESE
    )

    /** Custom Arb that generates an AddressComponent with random multi-locale names */
    fun Arb.Companion.addressComponent(): Arb<AddressComponent> {
        // Pick 1..5 distinct locales for the names map
        return Arb.int(1..5).flatMap { nameCount ->
            val count = nameCount.coerceAtMost(supportedLocales.size)
            Arb.list(Arb.element(supportedLocales), count..count).map { it.distinct() }
                .filter { it.isNotEmpty() }
                .flatMap { locales ->
                    // Generate a name string for each locale
                    Arb.list(Arb.string(1..20), locales.size..locales.size).flatMap { names ->
                        val namesMap = locales.zip(names).toMap()
                        // defaultLocale is one of the locales in the map
                        Arb.element(locales).flatMap { defaultLocale ->
                            // code and level
                            Arb.string(1..10).filter { it.isNotBlank() }.flatMap { code ->
                                Arb.int(0..5).map { depth ->
                                    AddressComponent(
                                        code = code,
                                        level = DivisionLevel(depth, "Level$depth"),
                                        names = namesMap,
                                        defaultLocale = defaultLocale
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }

    test("getName returns the corresponding name when locale exists in names map") {
        checkAll(100, Arb.addressComponent()) { component ->
            for ((locale, expectedName) in component.names) {
                component.getName(locale) shouldBe expectedName
            }
        }
    }

    test("getName falls back to defaultLocale name when locale is NOT in names map") {
        // Pick a locale that is guaranteed not in the component's names map
        checkAll(100, Arb.addressComponent()) { component ->
            val missingLocales = supportedLocales.filter { it !in component.names }
            if (missingLocales.isNotEmpty()) {
                val missingLocale = missingLocales.first()
                component.getName(missingLocale) shouldBe component.names.getValue(component.defaultLocale)
            }
        }
    }
})
