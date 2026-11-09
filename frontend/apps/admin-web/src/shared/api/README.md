# API 契约接入

目前没有后台权威 OpenAPI 文件，因此只安装工具，不创建占位接口或生成客户端。

后端契约就绪后，从仓库根目录执行（将路径替换为权威契约的绝对路径）：

```bash
pnpm --filter @j-store/admin-web api:generate /absolute/path/to/admin-openapi.yaml
```

类型生成到本目录的 `schema.d.ts`。后续请求模块可以通过 `openapi-fetch` 的 `createClient<paths>` 使用这些类型；baseUrl、CSRF、错误响应及会话过期处理必须依据真实 BFF 契约实现。生成类型不替代运行时校验，也不代表服务端已实施授权。
