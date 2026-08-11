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
package com.jstore.goods.acl

/**
 * 对象存储服务 ACL 接口。
 *
 * 根据图片标识（ImageKey）生成可访问的 URL。 本次仅定义接口，具体实现由基础设施层在后续迭代中提供。
 */
interface OssService {

    /**
     * 根据单个图片标识生成可访问的 URL。
     *
     * @param imageKey 图片资源标识，对应 OSS 存储中的对象 key
     * @return 可访问的图片 URL
     */
    fun generateUrl(imageKey: String): String

    /**
     * 根据图片标识列表批量生成可访问的 URL。
     *
     * @param imageKeys 图片资源标识列表
     * @return 与输入顺序一致的可访问图片 URL 列表
     */
    fun generateUrls(imageKeys: List<String>): List<String>
}
