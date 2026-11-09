# 前后端目录分离

2026-11-09 用户要求：先提交当前改动并创建目标为 develop 的 PR，再重整工程，分离后端和后台前端。前置候选为 PR #72，目录重整在独立分支实施，不自动合并。

## 目标与验收

1. backend/ 独立拥有 Gradle wrapper、构建/版本配置、原有全部 j-store-* JVM 模块和后端本地 Docker 配置；模块名与包名不变。
2. frontend/ 独立拥有 package.json、pnpm workspace/锁文件及 frontend/apps/admin-web；依赖版本、空白页面行为和构建结果不因搬迁改变。
3. 根目录保留共享文档、许可证、AGENTS/skills、脚本、部署与 CI 控制面，不再作为 Gradle 或 pnpm 项目根。
4. scripts/backend-gradle.sh 从任意工作目录定位 backend/ 构建；质量门禁、Git hooks、Windows helper、CI、镜像构建与报告路径使用新布局。
5. 当前文档与自动化契约同步更新。前端可冻结锁文件安装，通过检查、构建和 Chromium 测试；后端通过相关构建、实际应用运行测试及完整质量门禁。
6. 不增加业务功能、认证变化、依赖升级、旧目录兼容副本或数据迁移；恢复使用 Git 提交，不复制两套源码。

## 验证与风险

先建立目录/入口失败测试，再迁移。移动会影响相对路径、共享 LICENSE/Spotless 资源、hook 中的 Git 相对路径、Docker context 和测试夹具；逐项保留可执行证据。历史规格保留原业务意图，当前路径引用按新布局收敛。
