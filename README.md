# 城市图书馆跨馆借还调拨服务（PLL Service）

读者搜索图书时可看到**本馆库存、可跨馆调拨库存、预约队列、预计到馆时间和电子资源替代**；
预约后系统**锁定具体馆藏条码**并生成馆际调拨任务，物流员取书记录出库馆、目的馆、包裹编号与预计到达；
到馆后通知读者取书，**取书超时自动释放给下一位预约者**。借阅期内计算应还日期、续借资格、排队冲突与逾期罚款；
还书时馆员核验条码、馆藏状态、污损、缺页与附件。对**物流丢包、读者取消、到馆未取、声称已还、图书损坏、临时闭馆**
等异常，在库存、预约、借阅、罚款、赔偿之间保持状态一致，并为管理端提供调拨效率、热门预约、逾期与丢损统计。

技术栈：Java 17 · Spring Boot 3.3 · Spring Data JPA（PostgreSQL 行锁）· Flyway · springdoc OpenAPI 3 · Maven · Docker。

---

## 一、一键启动（Docker Compose）

```bash
docker compose up --build
```

- 服务： http://localhost:8080
- Swagger UI： http://localhost:8080/swagger-ui.html
- OpenAPI JSON： http://localhost:8080/v3/api-docs
- 规则手册： http://localhost:8080/api/rules
- PostgreSQL：localhost:5432 （库/用户/密码均为 `library`）

首次启动 Flyway 自动建表并写入演示数据（4 个馆、4 种书、15 个条码、6 位读者）。

## 二、本地开发启动

前置：JDK 17、Maven 3.8+、PostgreSQL 14+。

```bash
createdb library
# 可选：建账号（或改环境变量指向自己的库）
# createuser -l library && psql -c "ALTER USER library PASSWORD 'library';"

mvn spring-boot:run
# 或
mvn clean package
java -jar target/pll-service-1.0.0.jar
```

可用环境变量覆盖数据源：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`。
业务规则参数（借阅周期、续借次数、罚款单价、留存比例、SLA、热门书阈值、停借金额）集中在
`src/main/resources/application.yml` 的 `app.rules`，可直接调整，`GET /api/rules` 会实时反映。

## 三、测试账号与演示数据

本服务无独立登录系统，**读者账号即读者证号**（放请求体 `cardNo`）；**馆员/物流员工号**通过请求头
`X-Operator` 传入（默认 STAFF），所有人工操作均审计留痕。

| 读者证号 | 姓名 |
|---|---|
| P0001 ~ P0006 | 张伟、李娜、王芳、陈杰、赵敏、孙强 |

馆代码：`CENTRAL`（市中心馆）、`EAST`（城东）、`WEST`（城西）、`NORTH`（远郊北峰分馆，远端馆）。

示例条码（《三体》）：`BC-1001`（中心馆孤本）、`BC-1002~1005`（城东 4 本）、
`BC-1006~1007`（城西 2 本）、`BC-1008`（北峰孤本，受远端馆保护）。

## 四、验证方式

### 1. 自动化测试（推荐，无需安装 PostgreSQL）

```bash
mvn clean test
```

测试使用 **zonky 嵌入式 PostgreSQL（真实数据库）**，每个用例前重建 schema，共 **27 个用例全部通过**，覆盖：

- 搜索聚合：本馆/跨馆库存、ETA、电子书、远端馆孤本保护、留存比例（`CatalogSearchTest`）
- 预约全链路：锁码→调拨→取书出库→到馆通知→取书→超时释放给下一位→改派本地副本、重复预约拦截、取消退单（`HoldFlowTest`）
- 续借与罚款：续借上限、排队冲突、热门书、逾期不可续、罚款按日累计与封顶（`LoanRenewalFineTest`）
- 还书核验：污损/缺页/附件赔偿、重复入库防护、跨馆还书补给排队（`ReturnCheckTest`）
- 物流丢包：馆方承担、读者免责、自动重新调拨（`TransferLostTest`）
- 声称已还：馆内找到按**申报日**核销（争议期不滚罚款）、确认遗失按定价赔偿（`ReturnClaimTest`）
- 临时闭馆：拒绝新预约、取书期限顺延、闭馆馆禁出库（`ClosureTest`）
- 欠款停借、馆员减免/调整（只能下调、必须填原因、审计留痕）（`FineBlockAndAdjustTest`）
- 管理端四视角统计、物流准点率与耗时（`AdminStatsTest`）
- REST + Swagger JSON 真实 HTTP 冒烟（`RestApiSmokeTest`）

### 2. 手工 curl 冒烟（服务启动后）

```bash
# 搜索：本馆/跨馆库存、队列、ETA、电子书
curl 'http://localhost:8080/api/catalog/search?q=三体&pickup=CENTRAL'

# P0001 中心馆预约（本馆有书 -> 立即 READY）
curl -X POST localhost:8080/api/books/1/holds -H 'Content-Type: application/json' \
  -d '{"cardNo":"P0001","pickupLibrary":"CENTRAL"}'

# P0002 再约 -> 自动锁城东条码并生成 CREATED 调拨
curl -X POST localhost:8080/api/books/1/holds -H 'Content-Type: application/json' \
  -d '{"cardNo":"P0002","pickupLibrary":"CENTRAL"}'

# 物流员取书出库（登记包裹号）→ 到馆
curl -X POST localhost:8080/api/transfers/1/pickup -H 'Content-Type: application/json' -d '{"packageNo":"PKG-001"}'
curl -X POST localhost:8080/api/transfers/1/arrive

# 馆员为预约取书借出
curl -X POST localhost:8080/api/holds/1/checkout -H 'Content-Type: application/json' -d '{"barcode":"BC-1001"}'

# 还书核验（缺页 -> 严重污损赔偿）
curl -X POST localhost:8080/api/returns -H 'Content-Type: application/json' \
  -d '{"barcode":"BC-1001","returnLibrary":"CENTRAL","condition":"GOOD","missingPages":true}'

# 管理端统计 / 审计 / 手动补跑批处理
curl localhost:8080/api/admin/stats
curl -X POST localhost:8080/api/admin/jobs/expire-holds
curl -X POST localhost:8080/api/admin/jobs/scan-overdue
```

> 本沙箱环境无 Docker 守护进程，未实际执行 `docker compose up`；已实际执行的验证为：
> `mvn clean test`（27/27 通过，嵌入式 PostgreSQL + Flyway 真实建表）、`mvn package` 成功生成可执行 JAR。
> Dockerfile 为多阶段构建（Maven 构建 → JRE 运行），compose 含 PostgreSQL 健康检查与依赖顺序。

## 五、公平性与规则设计（规则手册见 `GET /api/rules`）

| 编号 | 规则要点 |
|---|---|
| R-HOLD-01 | 同一读者同一书目仅一条有效预约，防止占多个排队名额 |
| R-HOLD-02 | 到馆保留 3 天，超时自动释放给下一位 |
| R-HOLD-04 | 活跃排队 ≥5 人为热门书，**热门书不可续借** |
| R-TRANSFER-01 | 普通馆须留存 25% 本地副本，调出后不得低于该比例 |
| R-TRANSFER-03 | 远端分馆留存 50%，且普通馆有书时**绝不从远端馆调**，避免抽空偏远库存 |
| R-TRANSFER-04 | 临时闭馆：禁新预约/出库，取书期限自复馆日起算，在途包裹到馆回架 |
| R-LOAN-02/03 | 每书最多续借 1 次；书后有排队不可续借；逾期不可续 |
| R-FINE-01/02 | 逾期 0.5 元/日，单笔封顶 25 元 |
| R-FINE-03 | 污损 10 元；缺页/附件遗失等严重污损 30 元且图书下架 |
| R-FINE-04/05 | 读者遗失按书目定价赔偿；**物流丢包馆方承担、读者免责**并自动重分配 |
| R-FINE-06/07 | 人工调整只能减免且必须填原因留痕；待缴 ≥25 元暂停借阅/预约，结清恢复 |

其他一致性设计：

- **严格 FIFO 排队**：队首无书可分则后面不跨位分配；本馆回架副本优先**改派**给已有跨馆调拨但尚未出库的队首，取消调拨以节省物流。
- **状态机 + 行锁**：预约/借阅/调拨变更对书目行与条码行加悲观锁，杜绝一条码分给两人。
- **异常联动**：丢包→副本 LOST + 异常单 + 读者通知 + 排队重分配；到馆时预约已失效→回架并补给队列；
  声称已还→争议期间不滚罚款；损坏→下架、赔偿、不参与分配。
- **审计**：馆员每一次人工调整都记录操作人、规则编号（ruleRef）与影响，可按实体回看轨迹。

## 六、主要 API

| 模块 | 端点 |
|---|---|
| 目录 | `GET /api/catalog/search`、`GET /api/catalog/{bookId}`、`GET /api/rules` |
| 预约 | `POST /api/books/{id}/holds`、`POST /api/holds/{id}/cancel`、`GET /api/holds/{id}`、`GET /api/patrons/{cardNo}/holds` |
| 调拨 | `GET /api/transfers`、`POST /api/transfers/{id}/pickup`、`/arrive`、`/lost` |
| 借阅 | `POST /api/holds/{id}/checkout`、`POST /api/loans/checkout-direct`、`POST /api/loans/{id}/renew`、`GET /api/patrons/{cardNo}/loans` |
| 还书 | `POST /api/returns`（condition / missingPages / attachmentsMissing） |
| 罚款 | `GET /api/fines/{id}`、`/pay`、`/waive`、`/adjust` |
| 异常 | `POST /api/incidents/loans/{loanId}/return-claim`、`/incidents/{id}/resolve-found|resolve-lost`、`/loans/{loanId}/reader-lost` |
| 通知 | `GET /api/patrons/{cardNo}/notices` |
| 管理 | `GET /api/admin/stats`、`/audit`、`POST /admin/jobs/expire-holds`、`/scan-overdue`、`/libraries/{code}/close|reopen` |
