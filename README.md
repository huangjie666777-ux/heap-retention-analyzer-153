# heap-retention-analyzer

纯后端服务：上传 HotSpot HPROF 堆转储，定位 Java 堆中保留大块内存的对象。
Java 17 + Javalin 6.5.0 + NetBeans profiler 库（RELEASE230）。

## 分析口径

- **解析**：NetBeans profiler 库读取 HPROF（支持 4/8 字节 ID、分段堆转储），
  只取对象身份、类名与浅堆字节；图构建、支配与保留量全部由本服务自行计算，
  不采用库的任何保留量结果。
- **节点**：普通实例与数组（含基本类型数组）；不含 `java.lang.Class` 对象。
- **根**：GC 根（线程、JNI、栈帧等）以及类静态引用的目标对象。
- **边**：继承而来的实例引用字段（标签为 `声明类.字段名`）与对象数组元素
  （标签为 `[下标]`）。排除 `java.lang.ref.Reference` 声明的 `referent`，
  忽略 null，不模拟类加载器。
- **支配**：加入虚拟根（连接所有根），按 Cooper-Harvey-Kennedy 迭代算法求
  立即支配者；支配 = 所有根路径必经。正确处理环、共享子图与多根。
- **保留字节**：支配子树的浅堆总和（非可达总和）。不可达对象单独标记，
  不参与保留排行。
- **根路径**：对任一对象按真实图强引用做多源 BFS，给出到任一根的最短路径，
  逐边附字段或下标；不使用支配树边冒充原引用。
- **对象 ID**：64 位 ID 以 `0x` 前缀十六进制字符串无损传递。

## 限制

文件 ≤ 100 MiB，对象 ≤ 50,000，边 ≤ 200,000。损坏或超限一律拒绝（400/413），
分析完整构建后才发布，不存在半份结果。每次上传得到独立 `analysisId`，
并发上传与查询互不污染；DELETE 后资源即释放，不做持久化。

## 构建与测试

```bash
mvn test          # 12 个测试：支配语义、路径、ID 编解码、真实转储端到端
mvn compile
```

## 启动

```bash
mvn compile exec:java -Dexec.mainClass=dev.heap.Main   # 默认 7070 端口
# 或指定端口： java -Dport=8080 -cp ... dev.heap.Main
```

## 生成真实堆转储并演示

```bash
# 生成一个含已知对象图的真实 HPROF（HotSpotDiagnosticMXBean）
java -Xmx24m -cp target/classes:$(cat target/cp.txt) dev.heap.DumpGenerator /tmp/demo.hprof

# 1. 上传
curl -s -X POST -F file=@/tmp/demo.hprof http://localhost:7070/api/analyses
# => {"analysisId":"...","objects":25209,"edges":38068,"roots":1185,"unreachable":105}

# 2. 保留排行（降序，含类、浅堆、保留量、立即支配者）
curl -s "http://localhost:7070/api/analyses/<id>/retained?limit=5"

# 3. 对象详情
curl -s "http://localhost:7070/api/analyses/<id>/objects/0xfeb1b5f0"

# 4. 到任一根的最短强引用路径（逐边字段/下标）
curl -s "http://localhost:7070/api/analyses/<id>/objects/0xfeb44768/path"

# 5. 删除并释放资源
curl -s -X DELETE http://localhost:7070/api/analyses/<id>
```

## API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/analyses` | multipart 字段 `file` 上传 HPROF，返回 `analysisId` 与统计 |
| GET | `/api/analyses/{id}` | 分析概要（对象/边/根/不可达数） |
| GET | `/api/analyses/{id}/retained?limit=N` | 保留字节降序排行（仅可达对象） |
| GET | `/api/analyses/{id}/objects/{hexId}` | 单对象：类、浅堆、保留量、立即支配者 |
| GET | `/api/analyses/{id}/objects/{hexId}/path` | 到根的最短强引用路径 |
| DELETE | `/api/analyses/{id}` | 删除分析并释放资源 |

## 代码结构

- `Main.java` — Javalin HTTP 层（路由、上传、错误映射）
- `AnalysisService.java` — 分析注册表，并发隔离，原子发布
- `HprofGraphBuilder.java` — NetBeans 库解析 HPROF，构图并强制限制
- `Graph.java` — CSR 图索引（节点/边/标签）
- `DominatorAnalysis.java` — 虚拟根 + CHK 立即支配者 + 保留量
- `PathFinder.java` — 多源 BFS 最短强引用路径
- `Analysis.java` / `HexIds.java` — 查询视图与十六进制 ID 编解码
- `DumpGenerator.java` — 生成真实堆转储（测试与演示共用）
