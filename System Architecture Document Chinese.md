# **專案代號：Project "Monarch" - Monarch-Inspired Time Series Database (Java Spring Boot)**

**版本：** 1.0
**日期：** 2025年7月2日
**作者：** Gemini (資深工程師)

-----

### **1. 總覽 (Overview)**

本文件旨在指導開發團隊使用 Java Spring Boot 技術棧，建構一個分散式、多租戶、記憶體內的時間序列資料庫 (Time Series Database, TSDB)，其核心架構與設計理念源自 Google 的 Monarch 系統。系統目標是提供高可用性、高擴展性及強大的查詢能力，主要用於應用程式與基礎設施的監控、告警和分析。

我們將遵循 Monarch 的核心設計原則：

  * **高可用性優先於一致性**：為了及時偵測和緩解潛在故障，系統願意在必要時回傳部分資料，並容忍分區。
  * **記憶體內儲存**：為了降低延遲和減少對底層持久化儲存的依賴，核心資料將儲存在記憶體中。
  * **區域化結合全域管理**：資料在地理區域 (Zone) 內進行儲存和處理以提高可靠性，同時透過全域層提供統一的查詢和設定視圖。

-----

### **2. 系統架構 (System Architecture)**

系統將採用微服務架構，每個核心元件都是一個獨立的 Spring Boot 應用程式。元件間的通訊建議初期使用 RESTful API (搭配 OpenFeign) 進行開發，追求極致性能的場景可評估更換為 gRPC。

整體架構分為 **全域 (Global)** 和 **區域 (Zone)** 兩個層級，並依照功能劃分為三類元件：狀態持久化 (藍色)、查詢執行 (綠色)、資料寫入 (紅色)。

*(圖片來源:)*

#### **2.1. 核心元件職責**

1.  **資料寫入路徑 (Ingestion Path)**

      * `Ingestion Router`：接收客戶端寫入請求，根據時間序列的 `location` 欄位，將資料路由到對應的 `Zone`。
      * `Leaf Router`：接收來自 `Ingestion Router` 的資料，根據 `target string` 的詞典順序分片 (Lexicographic Sharding)，將資料轉發到 Zone 內正確的 `Leaf` 節點。
      * `Range Assigner`：管理 `target` 範圍到 `Leaf` 節點的分配，以平衡 Zone 內的負載。

2.  **狀態與儲存 (State & Storage)**

      * `Leaf`：核心儲存單元。在記憶體中儲存時間序列資料，並寫入復原日誌 (Recovery Logs) 到持久化儲存。
      * `Recovery Components`：(初期可簡化) 負責將 `Leaf` 寫入的日誌持久化。可使用 Kafka 或 Pulsar 等訊息佇列系統來實現，提供日誌的持久化和高可用性。
      * `Configuration Server / Mirror`：(Global/Zone) 儲存系統的所有設定，如 Schema、告警規則等。使用關聯式資料庫 (如 PostgreSQL) 搭配 Spring Data JPA 進行管理。

3.  **查詢執行路徑 (Query Execution Path)**

      * `Mixer` (Root & Zone)：查詢執行的協調者。將查詢分解成子查詢，發送到下層元件 (Zone Mixers 或 Leaves)，並合併回傳的結果。
      * `Index Server` (Root & Zone)：儲存欄位提示索引 (Field Hints Index, FHI)，用於在查詢時快速過濾掉不相關的子節點 (Zones 或 Leaves)，大幅減少查詢分流 (fanout)。
      * `Evaluator` (Root & Zone)：週期性地執行「常態查詢 (Standing Queries)」，例如告警規則，並將結果寫回 Monarch。

-----

### **3. 資料模型 (Data Model)**

資料模型是系統的基石，我們將嚴格遵循 Monarch 的 Schematized Table 設計。

#### **3.1. Java POJO 定義**

使用 Lombok 簡化程式碼。

```java
// 代表一個被監控的實體，例如一個服務實例或VM
@Data
@Builder
public class Target {
    private String schemaName; // e.g., "ComputeTask"
    private Map<String, Object> fields; // e.g., {user: "monarch", job: "mixer.zone1", cluster: "us-central1", task_num: 183}
}

// 代表一個監控指標的定義
@Data
@Builder
public class Metric {
    private String schemaName; // e.g., "/rpc/server/latency"
    private Map<String, Object> fields; // e.g., {service: "MonarchService", command: "Query"}
}

// 時間序列中的一個資料點
@Data
@Builder
public class TimeSeriesPoint {
    private long timestamp; // UTC milliseconds
    private Object value;     // Can be Boolean, Long, Double, String, or DistributionValue

    // For CUMULATIVE metrics
    private Long startTime;
}

// 代表一個完整的時間序列
@Data
@Builder
public class TimeSeries {
    private Target target;
    private Metric metric;
    // 使用有順序且執行緒安全的List
    private List<TimeSeriesPoint> points;
}

// Distribution (直方圖) 實作
// 可考慮引入 HdrHistogram 等高效能函式庫
@Data
@Builder
public class DistributionValue {
    private long count;
    private double sum;
    private double sumOfSquares; // For standard deviation
    private Map<Bucket, BucketStats> buckets;
    private Exemplar exemplar; //
}

@Data
@Builder
public class Exemplar {
    private double value;
    private long timestamp;
    private String traceId; // e.g., Dapper RPC trace
    private Map<String, Object> fields; // Originating target and metric fields
}
```

#### **3.2. 核心概念**

  * **Target (目標)**：被監控的實體。每個 `Target` 遵循一個 `TargetSchema`，其中一個欄位必須被標註為 `location`，用於決定資料儲存的 `Zone`。
  * **Metric (指標)**：對 `Target` 的某個面向的測量。
  * **Time Series Key**：由 `Target` 的所有欄位和 `Metric` 的所有欄位共同組成，唯一識別一條時間序列。
  * **Target String**：將 `Target` 的 Schema 名稱和欄位值依序串接而成，用於在 Zone 內進行詞典順序分片。
  * **Metric Type**：分為 `GAUGE` (瞬時值) 和 `CUMULATIVE` (累積值)。`CUMULATIVE` 類型對於遺失資料點有更好的容忍度。

-----

### **4. 核心元件技術細節 (Spring Boot Implementation)**

#### **4.1. Leaf Service**

這是系統中最核心、狀態最重的服務。

  * **技術棧**: Spring Boot, Spring WebFlux (for non-blocking IO), In-memory data structure (e.g., `ConcurrentHashMap`), Kafka Producer.
  * **主要職責**:
    1.  **資料儲存**: 提供 REST 端點接收 `Leaf Router` 寫入的資料。將時間序列保存在一個 `ConcurrentHashMap<String, TimeSeries>` 中，其中 Key 是 `Target String`。
    2.  **記憶體優化**: (進階) 實作時間戳共享 和值壓縮 (delta, run-length encoding) 以節省記憶體。
    3.  **復原日誌**: 每次寫入記憶體的同時，將資料點非同步發送到 Kafka 的特定 topic 中作為復原日誌。這避免了對檔案系統的直接依賴，並提高了可用性。
    4.  **查詢處理**: 執行 `Mixer` 下推的子查詢，直接在記憶體資料上進行過濾、聚合等操作。
    5.  **範圍管理**: 響應 `Range Assigner` 的指令，執行範圍的轉移 (move)、分割 (split)、合併 (merge)。
    6.  **索引更新**: 將新增的 `target` 和 `metric` 欄位值所產生的 `Field Hints` 推送給 `Zone Index Server`。

#### **4.2. Ingestion & Leaf Routers**

這兩個元件是無狀態的路由服務。

  * **技術棧**: Spring Boot, Spring WebFlux, Spring Cloud Gateway (可選).
  * **`Ingestion Router`**:
      * API: `POST /v1/write`
      * 邏輯：從請求中解析 `Target` 的 `location` 欄位，查詢一個可動態更新的 `location -> zone` 映射表 (可存在 `application.yml` 或 `Config Server` 中)，然後使用 `WebClient` 將請求轉發到目標 Zone 的 `Leaf Router`。
  * **`Leaf Router`**:
      * API: `POST /zone/v1/write`
      * 邏輯：從 `Target` 產生 `target string`。維護一份從 `Leaves` 拉取的 `target range -> leaf replicas` 的映射表。根據 `target string` 找到對應的 `Leaf` 節點組，並將資料轉發過去。

#### **4.3. Mixer Service (Zone & Root)**

負責查詢的分解與聚合。

  * **技術棧**: Spring Boot, Spring WebFlux, `CompletableFuture` / Project Reactor.
  * **主要職責**:
    1.  **查詢接收**: 提供 REST 端點接收使用者或 `Evaluator` 的查詢請求。查詢格式可設計為 JSON。
    2.  **查詢下推分析 (Query Pushdown)**: 這是關鍵優化。分析查詢操作 (如 `join`, `group by`)，判斷哪些部分可以在 `Leaf` 層級完成，哪些必須在 `Zone` 層級完成。例如，針對同一個 `target` 的 `join` 操作可以完全下推到 `Leaf`。
    3.  **分流 (Fan-out)**:
          * 向 `Index Server` 查詢，根據查詢中的 `filter` 條件獲取相關的子節點列表 (Zones or Leaves)。
          * 使用 `WebClient` 和 `Flux.merge()` 或 `CompletableFuture.allOf()` 並行向下層發送子查詢。
    4.  **結果合併**: 合併從下層收到的部分結果。例如，執行 `group by` 的最終聚合步驟。
    5.  **副本解析 (Replica Resolution)**: 在 Zone 層級，`Zone Mixer` 需要向相關的 `Leaf` 副本發送預查詢，根據回傳的資料品質 (完整性、時間範圍等) 為每個 `target range` 選擇一個最佳的 `Leaf` 執行查詢。

#### **4.4. Index Server & FHI (Field Hints Index)**

查詢優化的核心。

  * **技術棧**: Spring Boot, In-memory data structure.
  * **FHI 概念**: FHI 是一個從「欄位提示」到「包含該提示的子節點 ID 列表」的多重對應。最常用的提示是 trigrams (三個字母的子字串)。
  * **實現**:
    1.  `Leaf` 服務啟動時，掃描自身儲存的所有 `target/metric` 欄位值，產生 `trigrams`，並將 `(trigram_fingerprint, leaf_id)` 的對應關係註冊到 `Zone Index Server`。
    2.  `Index Server` 在記憶體中維護一個 `Map<Long, Set<Integer>>`，即 `fingerprint -> Set<ChildID>`。
    3.  `Mixer` 在分發查詢前，從 `filter` 條件中提取 `trigrams`，向 `Index Server` 查詢這些 `trigrams` 對應的子節點 ID 的交集，從而只向可能包含資料的子節點發送查詢。
    4.  論文指出 FHI 可將 Zone 層級的 fanout 降低約 99.5%，這是實現擴展性的關鍵。

-----

### **5. 查詢語言與執行 (Query Language & Execution)**

我們不實現完整的自訂語言，而是透過 JSON 格式定義查詢管道。

#### **5.1. JSON 查詢範例**

對應論文 Figure 6 的查詢：

```json
{
  "operations": [
    {
      "type": "join",
      "inputs": [
        {
          "operations": [
            { "type": "fetch", "targetSchema": "ComputeTask", "metricSchema": "/rpc/server/latency" },
            { "type": "filter", "predicate": "user == 'monarch'" },
            { "type": "align", "function": "delta", "window": "1h" }
          ]
        },
        {
          "operations": [
            { "type": "fetch", "targetSchema": "ComputeTask", "metricSchema": "/build/label" },
            { "type": "filter", "predicate": "user == 'monarch' && job =~ 'mixer.*'" }
          ]
        }
      ]
    },
    {
      "type": "groupBy",
      "keys": ["label"],
      "aggregator": { "field": "latency", "function": "aggregate" }
    }
  ]
}
```

#### **5.2. 查詢執行流程**

1.  使用者/Evaluator 將 JSON 查詢發送到 Root Mixer。
2.  Root Mixer 執行安全檢查，並進行查詢下推分析。
3.  Root Mixer 從查詢 `filter` 中提取 `field hints`，向 Root Index Server 查詢相關的 Zone 列表。
4.  Root Mixer 將部分重寫後的子查詢並行發送到相關的 Zone Mixer。
5.  Zone Mixer 重複此過程，向 Zone Index Server 查詢，找到相關的 Leaf 列表。
6.  Zone Mixer 執行副本解析，為每個 target range 選擇最佳 Leaf。
7.  Zone Mixer 將最終的子查詢發送到選定的 Leaves。
8.  Leaves 在記憶體中執行查詢片段（過濾、部分聚合），並將結果串流回 Zone Mixer。
9.  Zone Mixer 合併來自 Leaves 的結果，完成 Zone 層級的聚合，並串流回 Root Mixer。
10. Root Mixer 完成最終的聚合，回傳結果給使用者。

-----

### **6. 開發路線圖 (Development Roadmap)**

建議分階段進行開發：

1.  **Phase 1: 核心資料模型與單 Zone 實作**

      * 實現 Java POJO 資料模型。
      * 開發 `Leaf` 服務的核心記憶體儲存和 API。
      * 開發 `Leaf Router` 的基本分片邏輯 (初期可為靜態設定)。
      * 開發 `Zone Mixer` 的基本查詢功能 (不含 FHI)。
      * 目標：能夠在一個 Zone 內完成資料的寫入和查詢。

2.  **Phase 2: FHI 與查詢優化**

      * 開發 `Zone Index Server` 和 FHI 機制。
      * 在 `Leaf` 中加入向 `Index Server` 註冊 hints 的邏輯。
      * 在 `Zone Mixer` 中整合 FHI 查詢，實現 fanout 縮減。
      * 實現更複雜的查詢下推邏輯。

3.  **Phase 3: 全域層級與多 Zone**

      * 開發 `Root Mixer`, `Root Index Server`, `Ingestion Router`。
      * 實現跨 Zone 的查詢聚合。
      * 實現 `Configuration Server` 和設定分發機制。

4.  **Phase 4: 高可用性與擴展性**

      * 開發 `Range Assigner`，實現動態負載平衡。
      * 完善 `Leaf` 的副本恢復機制。
      * 實現 `Evaluator` 以支援常態查詢和告警。
      * 引入 collection aggregation 等進階功能。

-----

### **7. 結論**

這份文件為使用 Java Spring Boot 開發一個 Monarch-like 系統提供了高階設計和技術藍圖。開發的核心挑戰在於實現 Monarch 的擴展性優化，特別是 **查詢下推 (Query Pushdown)** 和 **欄位提示索引 (Field Hints Index)**。透過遵循本文件提出的分階段路線圖，團隊可以逐步建構出一個強大且可擴展的時間序列監控系統。


-----

### **技術工具與版本選型 (Tooling and Versioning)**

#### **核心開發 (Core Development)**

| 工具/函式庫 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **Java** | `21` (LTS) | 主要程式語言。選擇最新的長期支援 (LTS) 版本以獲得最佳性能、語言特性和長期的安全性更新。Spring Boot 3.x 要求 Java 17+。 |
| **Spring Boot** | `3.3.x` | 核心開發框架。提供快速、獨立的微服務應用建構能力。此版本與 Java 21 完全相容，並包含最新的依賴項管理。 |
| **Spring WebFlux**| (內建於 Spring Boot) | 用於建構非阻塞、反應式的 Web 應用。非常適合 `Router` 和 `Mixer` 這類需要處理大量併發 I/O 的網路密集型服務。 |
| **Spring Data JPA** | (內建於 Spring Boot) | 用於 `Configuration Server`，簡化與 PostgreSQL 資料庫的互動，快速實現設定的持久化。 |
| **Spring for Apache Kafka**| (內建於 Spring Boot) | [cite\_start]整合 Apache Kafka，用於 `Leaf` 節點向 Kafka 非同步寫入復原日誌 (Recovery Logs) [cite: 170, 284]。 |
| **Lombok** | `1.18.32` | 減少 Java POJO (如 `Target`, `Metric`, `TimeSeriesPoint` 等資料模型) 的樣板程式碼，提高開發效率。 |

#### **建構與依賴管理 (Build & Dependency Management)**

| 工具 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **Gradle** | `8.8.x` | (建議) 專案建構和依賴管理工具。對於複雜的多模組 (multi-module) 微服務專案，Gradle 提供更靈活和高效的建構腳本。 |
| **Maven** | `3.9.x` | (備選) 另一主流專案建構工具。若團隊對 Maven 更熟悉，亦是完全可行的選擇。 |

#### **資料庫與儲存 (Database & Storage)**

| 工具 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **PostgreSQL** | `16.x` | [cite\_start]用於 `Configuration Server` 的後端關聯式資料庫。Monarch 使用 Spanner [cite: 172, 535]，我們選擇功能強大且開源的 PostgreSQL 作為替代方案。 |
| **Apache Kafka** | `3.7.x` | [cite\_start]作為 `Leaf` 節點的復原日誌 (Recovery Logs) [cite: 170] 系統。提供高吞吐量、持久化和高可用性的日誌流，解耦了 `Leaf` 與底層檔案系統。 |
| **In-Memory Storage** | N/A | [cite\_start]`Leaf` 節點的核心資料將使用 Java 內建的執行緒安全資料結構 (如 `ConcurrentHashMap`) 存放於記憶體中 [cite: 169, 285][cite\_start]，這是 Monarch 的核心設計之一 [cite: 5, 94]。 |

#### **通訊 (Communication)**

| 工具 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **RESTful API (JSON)** | (HTTP/1.1) | 初期服務間通訊的主要方式，易於開發、偵錯和理解。 |
| **gRPC** | `1.64.x` | [cite\_start](https://www.google.com/search?q=%E9%80%B2%E9%9A%8E/%E6%80%A7%E8%83%BD%E5%84%AA%E5%8C%96) 高效能的 RPC 框架。Monarch 內部使用 streaming RPC [cite: 501]，gRPC 是其現代開源對應。適用於 Mixer 與 Leaf 之間這類需要高性能、低延遲的內部資料交換場景。 |

#### **容器化與部署 (Containerization & Deployment)**

| 工具 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **Docker** | `26.x` | 將各個 Spring Boot 微服務打包成標準化的容器，以實現環境一致性。 |
| **Kubernetes** | `1.30.x` | [cite\_start]容器編排平台。Monarch 運行在 Google 內部的 Borg 系統上 [cite: 192, 265]，Kubernetes 作為 Borg 的開源繼承者，是部署和管理我們分散式系統的最佳選擇。 |

#### **監控與可觀測性 (Monitoring & Observability)**

| 工具 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **Micrometer** | (內建於 Spring Boot) | 應用程式指標門面 (facade)，與 Spring Boot Actuator 深度整合，讓我們可以輕鬆地從應用程式中導出指標。 |
| **Prometheus** | `2.52.x` | [cite\_start]時間序列資料庫與監控系統。Prometheus 本身受 Monarch 的前身 Borgmon [cite: 27, 28, 699] 啟發，用它來監控我們自己開發的 TSDB 系統在主題上非常契合。 |
| **Grafana** | `11.x` | [cite\_start]指標視覺化工具。用於建立儀表板，將從 Prometheus 收集的系統運行狀態指標 (如 QPS、延遲、記憶體使用率) 視覺化 [cite: 25]。 |

#### **輔助函式庫 (Auxiliary Libraries)**

| 函式庫 | 建議版本 | 用途說明 |
| :--- | :--- | :--- |
| **HdrHistogram** | `2.2.x` | [cite\_start]一個高效能的直方圖實作。用於在 Java 中高效地實現 Monarch 的 `distribution` 值類型 [cite: 230, 231]，以進行延遲百分位數等統計計算。 |
| **Google Guava** | `33.x` | 提供額外的 Java 核心函式庫功能，如更強大的集合類型、快取、I/O 輔助工具等，非常實用。 |
