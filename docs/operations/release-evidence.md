# 发布证据操作手册

## 边界

本流程只生成可审查的发布候选和证据，不创建 GitHub Release、不推送容器、不部署生产环境。正式发布必须由人工确认版本、兼容性、迁移和回滚条件。

## 创建候选

1. 只在 `master` 的已合并提交上准备版本，确认 required checks 全部通过。
2. 使用签名 annotated tag，例如：

   ```bash
   git tag -s v1.0.0 -m "j-store v1.0.0"
   git verify-tag v1.0.0
   git push origin v1.0.0
   ```

3. 标签推送触发 `Release Evidence` workflow。workflow 不发布，只上传证据 artifact，并为源码归档和构建产物生成 GitHub provenance attestation。
4. 本地复现时，在干净工作区运行：

   ```bash
   ./scripts/create-release-evidence.sh v1.0.0
   sha256sum --check build/release-evidence/v1.0.0/SHA256SUMS
   ```

## 证据内容

- 标签、commit、tree、提交时间、工具链和标签签名状态。
- 完整质量门禁日志。
- 当前提交的源码归档和 `j-store-boot` JAR。
- CycloneDX SBOM、Licensee 依赖许可证报告和文件归属报告。
- 根 Apache-2.0 许可证、第三方说明和 SHA-256 清单。
- GitHub Artifact Attestation 记录的构建身份与输入提交。

## 人工发布检查

- 核对标签指向预期 `master` commit，且签名有效。
- 核对 SBOM 与许可证报告不存在未知或未批准项。
- 核对 JAR 中存在 `META-INF/LICENSE` 和 `META-INF/THIRD_PARTY.md`。
- 使用 `SHA256SUMS` 验证下载后的全部证据。
- 单独审批数据库迁移、配置变更、密钥、生产写入和回滚计划。

任一检查失败时停止发布，修复后从新的提交和标签重新生成，不覆盖旧证据。
