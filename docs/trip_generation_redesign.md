# AI 行程生成工作流重构设计

## 目标

把现有“AI 直接生成行程 + 前端兜底渲染”的模式，重构为正式的“后端编排事实与合同、AI 做规划判断、前端纯展示”的行程生成系统。

本设计面向演示项目的正式功能，不做临时 MVP，不做前端补点、补时间、补坐标，也不让 AI 凭空编造地点。

## 核心原则

1. 前端只负责提交需求和展示后端返回的最终行程合同。
2. 后端负责完整工作流编排、路线事实补充、顺路排序、合同校验。
3. AI 负责路线骨架规划、路线审稿、点位选择、时间安排。
4. 高德负责真实 POI、区域坐标、距离、驾车耗时、最终路线复核。
5. AI 可以判断“路线是否自然”，但不能跳过后端事实与合同校验。
6. 用户不能拖动改变节点顺序；修改必须重新走后端局部调整流程。

## 工作流总览

```text
用户需求
-> RequirementNormalizeNode
-> CandidatePoolBuildNode
-> AiMacroRoutePlanNode
-> AmapMacroRouteFactNode
-> AiRouteCriticNode
-> MacroRouteContractValidateNode
-> AiDailySelectionNode
-> MealAreaPlanNode
-> RouteMatrixBuildNode
-> RouteOrderOptimizeNode
-> AiTimelineScheduleNode
-> TimelineContractValidateNode
-> FinalRouteReviewNode
-> TripContractBuildNode
-> 前端展示
```

## 数据来源

### 高德数据

用于事实数据，不用于主观规划。

- POI 搜索：景点、商圈、餐饮区域、住宿区域、夜游区域、取车点。
- 地理编码：用户输入地点或区域名转坐标。
- 距离测量：构建点到点驾驶成本矩阵。
- 驾车路径规划：最终顺序复核、总里程、总车程。

### AI 数据

AI 只在候选数据和高德事实基础上做判断。

- 多日路线骨架。
- 路线审稿与修正建议。
- 每日点位选择。
- 时间安排、推荐理由、展示文案。

### 后端数据

后端维护会话、候选池、路线矩阵缓存、最终合同。

短期不做人工城市骨架库。热门城市深度数据可以后续逐步缓存，但当前版本先通过高德候选池 + AI 规划 + 后端事实校验落地。

## 关键模型

### AreaAnchor

区域锚点，不是具体店或酒店，但必须有地图坐标。

```text
id
name
type: AREA_ANCHOR
city
region
address
lng
lat
roles: LUNCH_AREA / DINNER_AREA / STAY_AREA / NIGHT_AREA / SCENIC_CLUSTER
tags
source: AMAP / GENERATED_FROM_AMAP
```

午餐、晚餐、住宿都应使用 `AreaAnchor`。它们永远是区域点，不假装成具体餐厅或酒店。

### PlaceCandidate

具体 POI 候选。

```text
id
name
type: SCENIC / PICKUP / OTHER
city
region
address
lng
lat
amapPoiId
tags
suggestedDurationMinutes
source: AMAP
```

### CandidatePool

一次生成会话的真实候选池。

```text
scenicCandidates[]
mealAreaCandidates[]
dinnerAreaCandidates[]
stayAreaCandidates[]
nightAreaCandidates[]
pickupAnchor
```

AI 只能选择池内 ID。如果用户指定新景点，后端先用高德查入候选池，再进入修改流程。

### MacroRoutePlan

多日路线骨架。

```text
routeShape: LOOP / ONE_WAY / HUB_AND_SPOKE
days[]
  day
  startAreaId
  focusAreaIds[]
  endAreaId
  stayAreaId
  theme
  reason
warnings[]
```

### RouteFact

后端用高德为路线骨架补充的事实。

```text
fromAnchorId
toAnchorId
distanceMeters
durationSeconds
source: AMAP
```

### TimelineNode

前端展示合同的最小单位。

```text
nodeId
order
type
title
subtitle
description
startTime
endTime
durationMinutes
city
region
address
lng
lat
tags
compact
sourceId
sourceType
routeLegFromPrevious
warnings[]
```

节点类型：

```text
RENTAL_PICKUP
DAY_START
SCENIC
LUNCH_AREA
DINNER_AREA
STAY_AREA
TRANSFER
INTERCITY_TRANSFER
CAR_RETURN_SERVICE
```

## 详细节点设计

### 1. RequirementNormalizeNode

输入用户自然语言和表单字段，输出标准化需求。

职责：

- 统一目的地、天数、人数、节奏、偏好。
- 统一租车上下文、取车时间、取车位置。
- 记录用户特殊要求。

不做：

- 不生成行程。
- 不查询高德。

### 2. CandidatePoolBuildNode

根据目的地和用户偏好从高德获取候选池。

候选类型：

- 城市核心景点。
- 周边景点。
- 商圈/餐饮区域。
- 夜游区域。
- 住宿区域。
- 取车点。

输出必须满足：

- 所有可上地图候选都有坐标。
- 候选带稳定 ID。
- 高德失败时，不生成假坐标。

### 3. AiMacroRoutePlanNode

AI 生成 2-3 个多日路线骨架方案。

输入：

- 用户需求。
- 高德候选区域和景点概览。
- 租车上下文。

输出：

- 多个 `MacroRoutePlan`。

约束：

- 只能引用候选 ID。
- 多日路线尽量沿一个方向推进。
- 除最后回城收尾外，避免离开远郊后再次回到同一远郊。
- 住宿区域服务第二天出发。
- 不输出具体时间。

### 4. AmapMacroRouteFactNode

后端为每个路线骨架补事实。

计算：

- 每天 start -> focus -> stay 的大致驾驶耗时。
- Day N stay -> Day N+1 start 的衔接距离。
- 总宏观驾驶耗时。
- 可能的折返信号。

这里不主观裁决，只提供事实。

### 5. AiRouteCriticNode

AI 审核多个路线骨架，选择或修正最佳方案。

输入：

- 用户需求。
- 候选路线骨架。
- 高德路线事实。

输出：

```text
selectedPlanId
revisedPlan
score
warnings[]
reason
```

AI 判断：

- 哪个方案整体更自然。
- 是否有明显折返。
- 跨区域安排是否适合节奏。
- 住宿区域是否服务第二天。
- 午餐/晚餐大概应倾向出发前、路上、到达后。

### 6. MacroRouteContractValidateNode

后端硬校验，不做主观旅行判断。

校验：

- JSON 合法。
- 所有 ID 存在于候选池。
- 所有地图点有坐标。
- 每天有 start/focus/end/stay。
- Day N 的 stay 能成为 Day N+1 的 start。
- 第一日租车时有取车起点。
- 最后一日还车服务不是地图点。

失败时，把错误传回 AI 重试。

### 7. AiDailySelectionNode

AI 在确定的每日区域内选择当天点位。

输入：

- 当前 Day 的 start/focus/stay。
- 用户偏好。
- 路线审稿意见。
- 候选 POI 和 AreaAnchor。

输出：

```text
selectedScenicIds[]
lunchAreaId
dinnerAreaId
stayAreaId
recommendedStayMinutesByScenic
selectionReason
```

约束：

- 只能选候选 ID。
- 午餐、晚餐、住宿都选区域锚点。
- 不能创建新地点。

### 8. MealAreaPlanNode

后端根据当天路线和预计时间，为餐饮区域做合理性处理。

午餐模式：

```text
BEFORE_DEPARTURE
ON_ROUTE
AFTER_ARRIVAL
```

判断依据：

- 起点时间。
- 到第一重点区域的驾驶时间。
- 上午景点停留时长。
- 12 点附近预计所在区域。

输出：

- 午餐区域候选优先级。
- 晚餐区域候选优先级。
- 给 AI 时间规划使用的 meal hint。

### 9. RouteMatrixBuildNode

后端构建当天点位驾驶成本矩阵。

参与点：

- 当天起点：取车点或昨日住宿区域。
- 景点。
- 午餐区域。
- 晚餐区域。
- 住宿区域。

实现：

- 使用高德距离测量批量请求。
- N 个地图点约 N 次请求。
- 缓存两点距离和耗时。

缓存 key：

```text
route:drive:{fromAnchorId}:{toAnchorId}
```

无 anchorId 时使用坐标归一化 key。

### 10. RouteOrderOptimizeNode

后端确定当天最终点位顺序。

规则：

- 起点固定。
- 住宿区域固定最后。
- 第一天租车时第一个节点为取车。
- 午餐、晚餐按 meal hint 和相对时间窗口约束。
- 景点顺序按高德驾驶耗时尽量少绕。
- 不自动裁剪点位。
- 过长路线生成 warning。

输出：

```text
orderedNodes[]
routeLegs[]
dailyDrivingMinutes
dailyDistanceKm
warnings[]
```

### 11. AiTimelineScheduleNode

AI 根据固定顺序规划时间。

输入：

- 已确定的 orderedNodes。
- 每段高德车程。
- 景点建议停留时长。
- 用户节奏。
- 取车时间。
- 午餐/晚餐窗口。
- 长距离 warning。

输出：

- 完整 timeline 时间。

约束：

- 不能改变顺序。
- 不能新增/删除地点。
- 时间必须动态规划。
- 午餐/晚餐自然。
- 长距离当天要解释。

### 12. TimelineContractValidateNode

后端校验最终 timeline。

校验：

- 节点顺序没变。
- 时间递增。
- 节点完整。
- 坐标完整，除非是 `TRANSFER` 或 `CAR_RETURN_SERVICE`。
- 午餐、晚餐、住宿存在。
- 住宿/结束区域在最后。
- Day2 起点来自 Day1 住宿区域。
- 最后一日追加 `CAR_RETURN_SERVICE`。

失败时重试 AI 时间规划。

### 13. FinalRouteReviewNode

后端按最终顺序调用高德驾车路线复核。

输出：

- totalDistanceKm
- totalDrivingMinutes
- routeSummary
- routeWarnings

### 14. TripContractBuildNode

构建前端最终合同。

前端不再接收半成品，不再自己插入取车、午餐、住宿、还车节点。

## 修改/微调工作流

用户不能拖动节点，但可以发修改请求。

### 支持类型

```text
REGENERATE_DAY
REMOVE_PLACE
REPLACE_PLACE
FORCE_INCLUDE_PLACE
AVOID_TAG
CHANGE_PACE
```

### ReviseDayWorkflow

```text
RevisionIntentParseNode
-> RevisionCandidateResolveNode
-> RevisionFeasibilityReviewNode
-> DailySelectionPatchNode
-> RouteMatrixBuildNode
-> RouteOrderOptimizeNode
-> AiTimelineScheduleNode
-> TimelineContractValidateNode
-> RevisionImpactAnalyzeNode
```

### 修改规则

- 前一天已确认内容默认锁定。
- 当前天起点默认锁定。
- 取车节点锁定。
- 用户明确保留的节点锁定。
- 住宿区域变化时，提示会影响下一天起点。
- 指定景点先高德查坐标，再判断是否可加入。
- 不合理时返回冲突，不强行生成烂路线。

冲突响应示例：

```json
{
  "status": "CONFLICT",
  "message": "三星堆距离今日路线较远，加入后当天总驾驶约增加 2.5 小时，与轻松游冲突。",
  "suggestions": ["改到 Day 2", "确认接受长距离", "替换今日两个景点"]
}
```

## 跨域策略

正式初稿：

```text
单段 <= 60 分钟：普通路程
60-90 分钟：显示路程信息
90-150 分钟：TRANSFER
>150 分钟：INTERCITY_TRANSFER
当天总驾驶 >240 分钟：提示自驾偏长
当天总驾驶 >360 分钟：强警告
```

不自动裁剪用户行程。路线长就明确展示风险。

## 前端职责

前端只做展示和交互入口。

必须删除或禁用：

- 拖动排序。
- 前端补取车。
- 前端补午餐/晚餐。
- 前端补住宿。
- 前端补还车。
- 前端补坐标。
- 前端补时间。

保留：

- 按 timeline 渲染。
- 地图渲染有坐标节点。
- 按节点类型显示颜色。
- 显示 route warnings。
- 重新生成当天。
- 对节点发修改请求。
- 指定加入景点。
- 展示冲突和影响提示。

## 验收样例

第一阶段使用成都 4 日自驾作为主验收。

应满足：

- Day1 租车时第一个节点是取车。
- Day2 起点来自 Day1 住宿区域。
- 午餐/晚餐/住宿都是区域点并上地图。
- 最后一日是上门取车文案节点，不上地图。
- 每天路线没有明显往返乱跳。
- 长距离有 warning。
- 用户说“不想去某景点”后能局部调整。
- 用户指定合理景点能加入，不合理时返回冲突。

## 实施顺序

1. 定义 DTO 和前端合同。
2. 实现高德候选池构建。
3. 实现路线矩阵与缓存。
4. 实现 RouteOrderOptimizeNode。
5. 实现 AI 多日骨架生成与审稿。
6. 实现每日点位选择。
7. 实现 AI 时间规划与后端校验。
8. 实现修改/微调工作流。
9. 前端改为纯展示合同，禁用拖动。
10. 用成都 4 日跑通并调 prompt。

