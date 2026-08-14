# 如何查看 BOM 中包含的依赖

本文只说明查询方法，不维护容易过期的依赖版本副本。当前项目声明和最终解析结果分别以
`gradle/libs.versions.toml`、`j-store-dependencies-platform` 与 Gradle 输出为准。

## 查看最终解析版本

优先使用 `dependencyInsight`。它同时展示最终版本、原始请求版本、冲突选择原因和约束来源：

```bash
./gradlew :j-store-boot:dependencyInsight \
  --dependency log4j-api \
  --configuration runtimeClasspath
```

查看模块的完整生产运行时依赖树：

```bash
./gradlew :j-store-boot:dependencies --configuration runtimeClasspath
```

输出中的常见标识：

- `(c)`：依赖约束，通常来自 BOM 或 Gradle Platform。
- `->`：原始请求版本被最终解析版本替换。
- `(*)`：该依赖子树已在其它位置展开。

## 查看项目采用的 BOM

1. 在 `gradle/libs.versions.toml` 的 `[libraries]` 中查找 `*-bom` alias。
2. 在 `j-store-dependencies-platform/build.gradle.kts` 中确认该 BOM 已由统一 Platform 导入。
3. 使用 `dependencyInsight` 验证消费模块的最终解析结果；不要只根据 catalog 文本推断。

Spring Boot 管理的坐标和版本可在
[Spring Boot 依赖版本附录](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)
查询。项目可能因安全公告通过统一 Platform 使用获批的最小安全版本覆盖 Boot 默认值；此类
例外必须同时具有规格、兼容性验证和自动化回归契约。

## 验证全仓安全基线

仓库质量门禁会执行最终依赖解析验证、许可证审计和全量回归：

```bash
./scripts/quality-gate.sh
```

也可以单独运行最终解析契约：

```bash
./gradlew verifyDependencyResolution
```

该任务遍历所有可解析的生产 `runtimeClasspath`，验证获批依赖家族没有版本混装，并确认主
启动模块包含预期的运行时适配器。新增安全版本例外时，应同步扩展该任务，而不是增加一份
静态版本清单。

## 生成生产 SBOM 与漏洞扫描

生成主启动模块的 CycloneDX SBOM：

```bash
./gradlew :j-store-boot:cyclonedxDirectBom --no-daemon
```

随后使用 `.github/workflows/security.yml` 固定并校验过的 OSV Scanner 版本扫描生成的
`j-store-boot/build/reports/cyclonedx-direct/bom.json`。本地扫描结果与 CI 不一致时，以 CI
固定版本和上游公告复核。

## 常见误区

- 不要把 BOM 当成“已下载依赖清单”；BOM 提供约束，只有消费模块实际引用的组件才进入运行时。
- 不要把 `libs.versions.toml` 中的版本当成最终解析结果；Gradle 冲突选择可能改变结果。
- 不要在消费模块使用 `force`、额外 `enforcedPlatform` 或硬编码版本绕过统一 Platform。
- 不要在说明文档复制“当前版本大全”；版本升级后这类列表会立即成为漂移来源。
