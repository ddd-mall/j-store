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
