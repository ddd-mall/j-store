package com.jstore.common.geo

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

/** 国家地址提供者接口 每个支持的国家实现此接口，负责地址查询、编码验证、层级配置和格式化 */
interface CountryAddressProvider {
    /** 该 Provider 支持的国家编码 */
    fun supportedCountryCode(): CountryCode

    /** 根据地址编码查询地址 */
    fun getByCode(addressCode: String): Result<I18nGeoAddress, BusinessError>

    /** 验证地址编码格式是否合法 */
    fun validateCode(addressCode: String): Result<Unit, BusinessError>

    /** 获取该国家的行政区划层级配置 */
    fun getDivisionLevelConfig(): DivisionLevelConfig

    /** 获取该国家的地址格式化模板 */
    fun getAddressTemplate(): AddressTemplate
}
