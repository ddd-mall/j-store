# J-Store Kubernetes 开发部署任务

- [x] KAPP-T1：增加部署契约测试，覆盖 Secret、Java 25、安全上下文、PVC、ServiceMonitor、Grafana dashboard 与网络暴露边界。
- [x] KAPP-T2：实现 namespace、应用、Redis、Service、ServiceMonitor、dashboard 与 NetworkPolicy 清单。
- [x] KAPP-T3：实现显式 context 的开发部署脚本，包括数据库 role/database、运行 Secret、JAR PVC loader 与 rollout。
- [x] KAPP-T4：在目标集群顺序部署并验证 PostgreSQL、Redis、Flyway、应用健康和 Prometheus target。
- [x] KAPP-T5：确认现有 Grafana 自动加载 dashboard，记录访问方式、证据、回滚与残余风险。
