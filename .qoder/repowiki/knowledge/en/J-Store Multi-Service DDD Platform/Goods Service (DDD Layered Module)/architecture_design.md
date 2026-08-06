The module follows a strict layered DDD split:
- `j-store-goods-domain` owns the commodity (SPU/SKU/GoodsStyle) and inventory aggregates, their repositories, factories, snapshots and events.
- `j-store-goods-application` composes use cases (`CommodityUseCase`, `InventoryUseCase`) and event handlers that translate external events into domain operations.
- `j-store-goods-infrastructure` implements the domain repository interfaces via Spring Data JPA POs and converters.
- `j-store-goods-boot` is the Spring `@Configuration` entry point: it wires factories (`SpuFactory`, `GoodsStyleFactory`, `SpuSnapshotFactory`), domain services, and exposes `TransactionalCommodityUseCase` / `TransactionalInventoryUseCase` as `@Primary` beans so application-layer use cases are invoked inside transactions.
- `j-store-goods-api` publishes only the read-side snapshot query interface (`GoodsSnapshotQueryService`) used by other services.
Cross-child communication is strictly one-way: application depends on domain interfaces, infrastructure depends on domain interfaces, boot wires them together; the api module is a pure contract consumed externally.