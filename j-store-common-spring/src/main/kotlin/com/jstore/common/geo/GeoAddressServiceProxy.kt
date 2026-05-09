package com.jstore.common.geo

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import org.springframework.stereotype.Service

@Service
class GeoAddressServiceProxy(
    providers: List<CountryAddressProvider>
) : GeoAddressService {

    private val providerMap: Map<CountryCode, CountryAddressProvider>

    init {
        val grouped = providers.groupBy { it.supportedCountryCode() }
        grouped.forEach { (code, list) ->
            require(list.size == 1) {
                "Duplicate CountryAddressProvider for $code: ${list.map { it::class.simpleName }}"
            }
        }
        providerMap = grouped.mapValues { it.value.single() }
    }

    override fun getByCode(countryCode: String, addressCode: String): Result<I18nGeoAddress, BusinessError> {
        val code = try {
            CountryCode(countryCode)
        } catch (e: IllegalArgumentException) {
            return Failure(AddressErrors.UnsupportedCountry.msg("非法国家编码: $countryCode"))
        }
        val provider = providerMap[code]
            ?: return Failure(AddressErrors.UnsupportedCountry.msg("不支持的国家: $countryCode"))
        return provider.getByCode(addressCode)
    }
}
