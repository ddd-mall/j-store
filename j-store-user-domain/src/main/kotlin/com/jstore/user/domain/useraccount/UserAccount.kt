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
package com.jstore.user.domain.useraccount

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.RecordsDomainEvents
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import java.time.LocalDateTime

/**
 * UserAccount 聚合根接口 封装用户账号的生命周期行为：昵称修改、密码修改、状态管理
 *
 * TODO: 补充最后登陆时间,最后登陆地点,最后登陆设备等信息
 */
interface UserAccount : AggregateRoot<UserId>, RecordsDomainEvents {
    override val id: UserId
    val phoneNumber: PhoneNumber
    val nickname: Nickname
    val passwordHash: Password
    val status: UserAccountStatus
    val createTime: LocalDateTime
    val updateTime: LocalDateTime

    /** 修改昵称 */
    fun changeNickname(newNickname: Nickname): Result<Unit, BusinessError>

    /** 修改密码（需传入新的哈希密文） */
    fun changePassword(newPasswordHash: Password): Result<Unit, BusinessError>

    /** 禁用账号 */
    fun disable(): Result<Unit, BusinessError>

    /** 启用账号 */
    fun enable(): Result<Unit, BusinessError>
}
