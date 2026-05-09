package com.jstore.common.geo

// Feature: geo-address-i18n, Property 4: 国家特定地址格式化顺序
import com.jstore.common.geo.chinese.ChinaAddressTemplate
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.shuffle
import io.kotest.property.arbitrary.subsequence
import io.kotest.property.checkAll
import java.util.Locale

/**
 * Property 4: 国家特定地址格式化顺序
 *
 * For any valid Chinese I18nGeoAddress, formatting with ChinaAddressTemplate produces
 * a string where administrative divisions appear in depth-ascending order (省→市→区/县).
 * To verify order: for each pair of consecutive components sorted by depth, the name of
 * the lower-depth component appears before the higher-depth component in the formatted string.
 *
 * **Validates: Requirements 4.2, 4.3, 4.4**
 */
class AddressFormatterPropertyTest : FunSpec({

    val template = ChinaAddressTemplate()
    val zhCN = Locale.SIMPLIFIED_CHINESE

    // Pools of distinct Chinese names per level to ensure unique names in generated addresses
    val provinceNames = listOf(
        "北京市", "上海市", "广东省", "浙江省", "江苏省", "四川省",
        "湖北省", "湖南省", "河北省", "河南省", "山东省", "福建省"
    )
    val cityNames = listOf(
        "杭州市", "深圳市", "成都市", "武汉市", "南京市", "苏州市",
        "长沙市", "青岛市", "厦门市", "宁波市", "无锡市", "合肥市"
    )
    val countyNames = listOf(
        "西湖区", "南山区", "武侯区", "洪山区", "鼓楼区", "姑苏区",
        "岳麓区", "市南区", "思明区", "海曙区", "梁溪区", "蜀山区"
    )

    /**
     * Generates a Chinese I18nGeoAddress with 1-3 components at distinct depth levels (1, 2, 3),
     * each with a unique name drawn from level-specific pools. Components are shuffled to ensure
     * the formatter (not the input order) determines the output order.
     */
    fun arbChineseAddress(): Arb<I18nGeoAddress> = arbitrary {
        // Decide which levels to include (at least 1, up to 3)
        val allLevels = listOf(1, 2, 3)
        val levelCount = Arb.int(1..3).bind()
        val selectedLevels = Arb.shuffle(allLevels).bind().take(levelCount).sorted()

        val components = selectedLevels.map { depth ->
            val name = when (depth) {
                1 -> Arb.element(provinceNames).bind()
                2 -> Arb.element(cityNames).bind()
                3 -> Arb.element(countyNames).bind()
                else -> error("Unexpected depth: $depth")
            }
            val code = "%06d".format(depth * 100000 + Arb.int(1..99999).bind())
            AddressComponent(
                code = code,
                level = DivisionLevel(depth, when (depth) {
                    1 -> "省"
                    2 -> "市"
                    3 -> "区/县"
                    else -> "Level$depth"
                }),
                names = mapOf(zhCN to name),
                defaultLocale = zhCN
            )
        }

        // Shuffle components so input order is random — the template must sort them
        val shuffled = Arb.shuffle(components).bind()

        I18nGeoAddress(
            countryCode = CountryCode.CN,
            components = shuffled,
        )
    }

    test("Property 4: Chinese address formatting produces depth-ascending order (省→市→区/县)") {
        // Feature: geo-address-i18n, Property 4: 国家特定地址格式化顺序
        checkAll(100, arbChineseAddress()) { address ->
            val formatted = AddressFormatter.format(address, template, zhCN)

            // Components sorted by depth (the expected order in the output)
            val sortedComponents = address.components.sortedBy { it.level.depth }

            // For each consecutive pair, verify the lower-depth name appears before the higher-depth name
            sortedComponents.zipWithNext().forEach { (lower, higher) ->
                val lowerName = lower.getName(zhCN)
                val higherName = higher.getName(zhCN)
                val lowerIndex = formatted.indexOf(lowerName)
                val higherIndex = formatted.indexOf(higherName)

                // Both names must be present in the formatted string
                (lowerIndex >= 0) shouldBe true
                (higherIndex >= 0) shouldBe true

                // Lower-depth component name must appear before higher-depth component name
                (lowerIndex < higherIndex) shouldBe true
            }
        }
    }

    // Feature: geo-address-i18n, Property 5: 格式化使用指定 Locale 的名称
    // **Validates: Requirements 4.5**

    val enUS = Locale.US

    // Chinese names per level
    val zhProvinceNames = listOf("北京市", "上海市", "广东省", "浙江省", "江苏省", "四川省")
    val zhCityNames = listOf("杭州市", "深圳市", "成都市", "武汉市", "南京市", "苏州市")
    val zhCountyNames = listOf("西湖区", "南山区", "武侯区", "洪山区", "鼓楼区", "姑苏区")

    // English names per level
    val enProvinceNames = listOf("Beijing", "Shanghai", "Guangdong", "Zhejiang", "Jiangsu", "Sichuan")
    val enCityNames = listOf("Hangzhou", "Shenzhen", "Chengdu", "Wuhan", "Nanjing", "Suzhou")
    val enCountyNames = listOf("Xihu", "Nanshan", "Wuhou", "Hongshan", "Gulou", "Gusu")

    /**
     * Generates a Chinese I18nGeoAddress with multi-locale names (zh-CN and en-US).
     * Each component has names in both locales, with zh-CN as the default.
     */
    fun arbMultiLocaleAddress(): Arb<I18nGeoAddress> = arbitrary {
        val allLevels = listOf(1, 2, 3)
        val levelCount = Arb.int(1..3).bind()
        val selectedLevels = Arb.shuffle(allLevels).bind().take(levelCount).sorted()

        val components = selectedLevels.map { depth ->
            val index = Arb.int(0..5).bind()
            val (zhName, enName) = when (depth) {
                1 -> zhProvinceNames[index] to enProvinceNames[index]
                2 -> zhCityNames[index] to enCityNames[index]
                3 -> zhCountyNames[index] to enCountyNames[index]
                else -> error("Unexpected depth: $depth")
            }
            val code = "%06d".format(depth * 100000 + Arb.int(1..99999).bind())
            AddressComponent(
                code = code,
                level = DivisionLevel(depth, when (depth) {
                    1 -> "省"; 2 -> "市"; 3 -> "区/县"; else -> "Level$depth"
                }),
                names = mapOf(zhCN to zhName, enUS to enName),
                defaultLocale = zhCN
            )
        }

        I18nGeoAddress(
            countryCode = CountryCode.CN,
            components = components,
        )
    }

    /** Arb that picks either zh-CN or en-US locale */
    fun arbTargetLocale(): Arb<Locale> = Arb.of(zhCN, enUS)

    test("Property 5: formatted string contains each component name in the specified Locale (or fallback)") {
        // Feature: geo-address-i18n, Property 5: 格式化使用指定 Locale 的名称
        checkAll(100, arbMultiLocaleAddress(), arbTargetLocale()) { address, targetLocale ->
            val formatted = AddressFormatter.format(address, template, targetLocale)

            // Every component's name for the target locale (or fallback) must appear in the result
            address.components.forEach { component ->
                val expectedName = component.getName(targetLocale)
                (formatted.contains(expectedName)) shouldBe true
            }
        }
    }

    test("Property 5: fallback to defaultLocale when target Locale is absent from names") {
        // Feature: geo-address-i18n, Property 5: 格式化使用指定 Locale 的名称
        val jaJP = Locale.JAPAN // not present in generated addresses

        checkAll(100, arbMultiLocaleAddress()) { address ->
            val formatted = AddressFormatter.format(address, template, jaJP)

            // Since ja-JP is not in any component's names, fallback to defaultLocale (zh-CN)
            address.components.forEach { component ->
                val fallbackName = component.getDefaultName()
                (formatted.contains(fallbackName)) shouldBe true
            }
        }
    }
})
