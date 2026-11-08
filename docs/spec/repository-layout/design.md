# 目录分离设计与实施边界

采用 backend/ 与 frontend/ 两个独立构建根；不为旧根目录保留 Gradle/pnpm 代理工程或源码副本。根目录保留 docs、scripts、config、deploy、observability、许可证及 .env.example。

- 后端移入全部 64 个 j-store-* 模块、Gradle wrapper/catalog/配置，以及本地 Docker/Compose；Gradle 项目标识保持不变。
- 前端移入 package.json、锁文件、workspace 与 apps/admin-web。相对工作区结构和依赖解析不变。
- scripts/backend-gradle.sh 固定使用绝对 backend 项目路径，因此不依赖调用者 cwd。Windows helper 同样传入 -p。
- Gradle 通过父目录读取共享许可证、格式头和 hooks。Git 相对的 Spotless 目标清单按仓库根解析；格式示例目标调整为 backend/examples，当前 docs 没有独立 .kt 文件。迁移暴露的既有格式问题仅由当前 Spotless 统一收敛。
- CI、OCI 构建、发布证据、治理测试与 PowerShell 路径同步调整。Qodana 保留整个仓库作为上下文，使用 onlyDirectory: backend，避免丢失共享资源和 Git 元数据；依据 [官方 monorepo 指南](https://www.jetbrains.com/help/qodana/monorepo-project.html)。
- .gitleaksignore 中历史提交的假阳性指纹及其测试保持原样：这些路径描述历史提交，不能因目录迁移修改。

前置 OAuth/前端候选已通过 PR #72 保存。此分支不自动合并任何 PR，不升级依赖，不部署环境。
