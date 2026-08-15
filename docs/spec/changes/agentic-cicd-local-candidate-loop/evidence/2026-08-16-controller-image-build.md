# Supervisor不可变镜像构建证据

## 输入身份

- j-store controller revision：`3a537df4dac461e40b12fcda46597b959ef24f52`
- Symphony revision：`8001b52e3062495a16e520e4ceaf8f9de868c4d0`
- phase bridge patch SHA-256：`bbaad0e4ad04377b5b64238f7fabbfd383915cf60692f321493dd5f3372bcb8a`
- phase routing patch SHA-256：`b60be30500e95f7fd8d61ea4f73cab4b618e646f541ede6f67e8e0f3eac27535`
- dependency lock SHA-256：`9e22b8a3a5cb3ff49fb14899e224a0ac8dc08523e75b7835724071f00593890a`
- WORKFLOW SHA-256：`a8c18b98d5fbeb32dba03f522b4fb909c42f7cc7534a8d5607bb8d90e620fa5f`
- Codex：`0.146.0`
- Elixir基础镜像：`hexpm/elixir:1.19.5-erlang-28.3-debian-bookworm-20260202-slim@sha256:09279250196a9ad971ebe4673ec2df47bc760c0409a055df8ea283954ac6a099`
- Node基础镜像：`node:22-bookworm-slim@sha256:d649c27dae7ba0137b3cef5dd75baa422c08dc3d9e3fc0c23dfb172dc3cc6436`

构建脚本从两个洁净Git revision分别生成受控上下文；Symphony named context来自固定commit的`git archive`，不读取checkout中ignored文件。三个补丁/依赖输入在构建前按锁文件重新散列，任何漂移均fail-closed。

## 输出身份

- runtime manifest digest：`sha256:305a2b8af0cdc38510b663436e17d6f47eba4b02a9c015010e64a3aa0084d1a9`
- Docker archive SHA-256：`e5c1cd552ea1016454299de2cbc79d28fa3b2e0973eaa88a0ca72f46c2a753e4`
- SPDX statement SHA-256：`9975c00056a9483ae69bc78965a6a081c08c40de2211055fb2d2a7cf841bd227`
- SLSA provenance statement SHA-256：`64f523e2c8ae23da71c434bcf3daeee65e5f6a3a5e1cbce7a9adee02e39b8b32`
- source record SHA-256：`ea029b00633bd9778fb6a98af8597d736fcf280d068486813c86e02f2d5029ab`
- Buildx：`github.com/docker/buildx v0.36.1 1d8dde89b8aba914e05e45366770736fea1fd690`

运行时构建和attested OCI构建得到相同manifest digest。提取器要求恰好一个SPDX statement和一个SLSA provenance statement，且两者subject均包含上述runtime digest，否则构建失败。加载后的镜像labels逐项精确核对全部输入身份；部署入口只接受`repository@sha256:<64>`，不使用该构建tag作为运行身份。

完整Docker archive、SPDX、SLSA和source record保存在开发主机制品目录；它们不提交Git。下一步在Kubernetes节点导入archive、建立canonical digest alias并核对实际Pod `imageID`。

Level 0能力合同没有变化；本次构建未修改集群或远程GitHub状态。
