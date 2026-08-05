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
