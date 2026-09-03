# Code Formatting Guidelines - j-store

## 唯一格式来源

- Java、Kotlin 和 Gradle Kotlin DSL 文件只接受根项目 Spotless 配置产生的格式。
- Kotlin 和 Gradle Kotlin DSL 由 Spotless 集成的 ktfmt Kotlin 风格进行格式化。
- 仓库不声明额外的人工排版规则，不根据开发者、IDE、AI 工具或提示词对 Spotless/ktfmt 的输出进行二次调整。
- 如需改变格式，必须修改可执行的 Spotless/ktfmt 配置并统一应用；不得只修改提示词或文档示例。

## 开发流程

1. 新克隆或新 worktree 中执行 `./gradlew installSpotlessGitHooks`。
2. 开发中执行 `./gradlew spotlessApply`，交付前执行 `./gradlew spotlessCheck`。
3. Windows JDK 因临时目录的 UNIX Domain Socket 路径无法创建 NIO Selector 时，使用 `.\scripts\gradlew-windows.ps1 <tasks>`。
4. `pre-commit` 只格式化已暂存的目标源码并重新暂存结果；目标文件同时有未暂存改动时停止提交。
5. `pre-push` 检查待推送提交中的目标源码；发现格式问题时应用 Spotless，开发者提交结果后重新推送。
6. Git hook 是本地反馈；CI 和 `scripts/quality-gate.sh` 中的 `spotlessCheck` 是最终格式门禁。
