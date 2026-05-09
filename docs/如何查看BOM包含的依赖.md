# 如何查看 BOM 中包含的依赖

## 方法1：使用 Gradle 依赖树命令 ⭐ 推荐

### 查看特定模块的依赖
```bash
# Windows CMD
gradlew :模块名:dependencies --configuration runtimeClasspath

# 示例
gradlew :j-store-order-boot:dependencies --configuration runtimeClasspath
```

### 查看所有模块的依赖
```bash
gradlew dependencies
```

### 依赖树中的 BOM 标识

在输出中，你会看到类似这样的内容：

```
+--- org.springframework.boot:spring-boot-dependencies:3.3.10
    +--- org.jetbrains.kotlin:kotlin-stdlib:1.9.25 -> 2.1.21 (c)
    +--- org.springframework.data:spring-data-commons:3.3.10 (c)
    +--- org.springframework.boot:spring-boot-starter-web:3.3.10 (c)
    +--- com.fasterxml.jackson.core:jackson-core:2.17.3 -> 2.18.2 (c)
```

**关键标识：**
- `(c)` - 表示这是一个依赖约束（constraint），由 BOM 管理版本
- `->` - 表示版本被覆盖（从左边版本覆盖到右边版本）

## 方法2：查看 BOM 的官方文档

### Spring Boot BOM
访问：https://docs.spring.io/spring-boot/docs/3.3.10/reference/htmlsingle/#appendix.dependency-versions

**当前项目使用的 Spring Boot BOM (v3.3.10) 管理的主要依赖：**

#### Spring 相关
- `spring-boot-starter-*` - 所有 Spring Boot Starter
- `spring-data-*` - Spring Data 系列（JPA, Redis, Commons等）
- `spring-*` - 所有 Spring Framework 核心模块

#### 数据库
- `postgresql` → 42.7.5
- `HikariCP` → 5.1.0
- `hibernate-core` → 6.5.3.Final

#### JSON
- `jackson-databind`
- `jackson-core`
- `jackson-annotations`
- `jackson-datatype-jdk8`
- `jackson-datatype-jsr310`
- `jackson-module-parameter-names`

#### 日志
- `slf4j-api` → 2.0.17
- `logback-classic` → 1.5.18
- `log4j-api` → 2.23.1

#### Web/网络
- `tomcat-embed-*` → 10.1.39
- `netty-*` → 4.1.119.Final
- `reactor-core` → 3.6.15
- `lettuce-core` → 6.3.2.RELEASE

#### 工具类
- `lombok` → 1.18.36
- `commons-lang3` → 3.14.0 (在你的项目中被覆盖为 3.19.0)
- `guava` - 不在 Spring Boot BOM 中，需要独立指定
- `caffeine` → 3.1.8

#### 测试
- `spring-boot-starter-test`
- `mockito-core`
- `junit-jupiter`

### Spring Cloud BOM
访问：https://spring.io/projects/spring-cloud

**当前项目使用的 Spring Cloud BOM (v2023.0.5) 管理的依赖：**
- `spring-cloud-starter-*`
- `spring-cloud-loadbalancer`
- `spring-cloud-function-*`
- `spring-cloud-starter-netflix-*`

### Jackson BOM
访问：https://github.com/FasterXML/jackson-bom

**当前项目使用的 Jackson BOM (v2.18.2) 管理的依赖：**
- `jackson-core`
- `jackson-databind`
- `jackson-annotations`
- `jackson-datatype-*`
- `jackson-module-kotlin`

## 方法3：直接查看 BOM 的 POM 文件

### 从 Maven Central 查看
访问 Maven Central，搜索 BOM 的 artifactId，查看其 POM 文件：

#### Spring Boot Dependencies
https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.3.10/spring-boot-dependencies-3.3.10.pom

#### Jackson BOM
https://repo1.maven.org/maven2/com/fasterxml/jackson/jackson-bom/2.18.2/jackson-bom-2.18.2.pom

### 使用 Gradle 下载 POM
```bash
# 会自动下载到本地 Gradle 缓存
# Windows 位置：%USERPROFILE%\.gradle\caches\modules-2\files-2.1\
```

## 方法4：生成可视化依赖报告

### 生成 HTML 依赖报告
```bash
gradlew :j-store-order-boot:dependencies --configuration runtimeClasspath --scan
```

这会生成一个可以在浏览器中查看的交互式依赖树。

### 使用 IntelliJ IDEA
1. 打开 `build.gradle.kts` 文件
2. 点击右侧的 Gradle 工具窗口
3. 找到你的模块 → Tasks → help → dependencies
4. 双击运行，在 Run 窗口查看依赖树

## 实践：当前项目的 BOM 覆盖范围

### ✅ 由 spring-boot-dependencies BOM 管理（不需要指定版本）
```toml
# 这些依赖的版本由 BOM 统一管理
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-data-commons = { module = "org.springframework.data:spring-data-commons" }
spring-data-redis = { module = "org.springframework.data:spring-data-redis" }
postgresql = { module = "org.postgresql:postgresql" }  # BOM 指定 42.7.5
```

### ✅ 由 jackson-bom 管理（不需要指定版本）
```toml
jackson-core = { module = "com.fasterxml.jackson.core:jackson-core" }
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind" }
jackson-annotations = { module = "com.fasterxml.jackson.core:jackson-annotations" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin" }
```

### ❌ 不由 BOM 管理（需要显式指定版本）
```toml
# 这些需要自己指定版本
guava = { module = "com.google.guava:guava", version.ref = "guava" }
redisson-spring-boot-starter = { module = "org.redisson:redisson-spring-boot-starter", version.ref = "redisson" }
seata-all = { module = "org.apache.seata:seata-all", version.ref = "seata" }
fastexcel = { module = "cn.idev.excel:fastexcel", version.ref = "fastexcel" }
```

### ⚠️ 版本覆盖（显式指定会覆盖 BOM 的版本）
```toml
# 你的项目中覆盖了 Spring Boot BOM 的版本
commons-lang3 = { module = "org.apache.commons:commons-lang3", version = "3.19.0" }
# Spring Boot BOM 默认是 3.14.0，但你使用了更新的 3.19.0

# 如果你想使用 BOM 的版本，应该这样写：
commons-lang3 = { module = "org.apache.commons:commons-lang3" }
```

## 快速检查清单

### 判断一个依赖是否由 BOM 管理
1. 运行 `gradlew :模块名:dependencies --configuration runtimeClasspath`
2. 在输出中搜索你的依赖
3. 如果依赖旁边有 `(c)` 标记，说明它由 BOM 管理
4. 如果有版本号后跟 `->` 箭头，说明 BOM 指定的版本被覆盖了

### 示例：检查 jackson-databind
```
com.fasterxml.jackson.core:jackson-databind:2.17.3 -> 2.18.2
```
- `2.17.3` 是 Spring Boot BOM 指定的版本
- `2.18.2` 是 Jackson BOM 指定的版本（覆盖了 Spring Boot 的）
- 你的项目同时引入了两个 BOM，Jackson BOM 的优先级更高

## 最佳实践建议

1. **优先使用 BOM 管理的版本**
   - 减少版本冲突
   - 经过测试的版本组合
   - 简化依赖管理

2. **仅在必要时覆盖版本**
   - 需要新特性
   - 安全漏洞修复
   - BOM 版本太旧

3. **定期查看依赖树**
   ```bash
   gradlew dependencies > dependencies.txt
   ```
   检查是否有意外的版本冲突

4. **使用 `dependencyInsight` 查看特定依赖**
   ```bash
   gradlew :j-store-order-boot:dependencyInsight --dependency jackson-databind
   ```
   这会显示为什么选择了特定版本

## 常用命令速查

```bash
# 查看所有依赖
gradlew dependencies

# 查看特定模块的运行时依赖
gradlew :模块名:dependencies --configuration runtimeClasspath

# 查看特定依赖的详情
gradlew :模块名:dependencyInsight --dependency 依赖名

# 生成可搜索的 HTML 报告
gradlew :模块名:dependencies --scan

# 查看编译时依赖
gradlew :模块名:dependencies --configuration compileClasspath

# 只查看直接依赖（不展开传递依赖）
gradlew :模块名:dependencies --configuration runtimeClasspath | findstr "^+---"
```

