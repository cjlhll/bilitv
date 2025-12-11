# 番剧索引筛选接口文档

## 接口概述

本文档描述了 PiliPlus 项目中番剧索引页面的筛选功能相关API接口，包括获取筛选条件配置和获取筛选结果两个核心接口。

---

## 1. 获取筛选条件配置

### 基本信息

- **接口地址**: `/pgc/season/index/condition`
- **请求方法**: `GET`
- **接口说明**: 获取番剧索引页的筛选条件配置，包括排序方式和各类筛选项（类型、地区、状态等）

### 请求参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| season_type | int | 否 | - | 内容类型：<br>1 = 番剧<br>3 = 电影<br>4 = 国创 |
| type | int | 否 | 0 | 类型参数 |
| index_type | int | 否 | - | 索引类型 |

### 请求示例

```http
GET /pgc/season/index/condition?season_type=1&type=0
```

### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "filter": [
      {
        "field": "style_id",
        "name": "类型",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "搞笑"
          },
          {
            "keyword": "2",
            "name": "运动"
          },
          {
            "keyword": "3",
            "name": "热血"
          }
        ]
      },
      {
        "field": "area",
        "name": "地区",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "日本"
          },
          {
            "keyword": "2",
            "name": "美国"
          },
          {
            "keyword": "3",
            "name": "中国"
          }
        ]
      },
      {
        "field": "is_finish",
        "name": "状态",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "0",
            "name": "连载"
          },
          {
            "keyword": "1",
            "name": "完结"
          }
        ]
      }
    ],
    "order": [
      {
        "field": "3",
        "name": "追番人数",
        "sort": ""
      },
      {
        "field": "0",
        "name": "更新时间",
        "sort": ""
      },
      {
        "field": "2",
        "name": "评分",
        "sort": ""
      }
    ]
  }
}
```

### 响应字段说明

#### data 对象

| 字段名 | 类型 | 说明 |
|--------|------|------|
| filter | array | 筛选条件列表，包含多个筛选维度 |
| order | array | 排序方式列表 |

#### filter 数组元素

| 字段名 | 类型 | 说明 |
|--------|------|------|
| field | string | 筛选字段标识，用于请求结果接口时作为参数名 |
| name | string | 筛选条件的显示名称（中文） |
| values | array | 该筛选条件的所有可选值 |

#### values 数组元素

| 字段名 | 类型 | 说明 |
|--------|------|------|
| keyword | string | 筛选值的关键字，用于请求结果接口时作为参数值 |
| name | string | 筛选值的显示名称（中文） |

#### order 数组元素

| 字段名 | 类型 | 说明 |
|--------|------|------|
| field | string | 排序字段标识 |
| name | string | 排序方式的显示名称 |
| sort | string | 排序方式（预留字段，当前为空） |

### 常见筛选字段说明

| field 字段值 | 中文名称 | 说明 | 常见 keyword 值 |
|-------------|---------|------|----------------|
| style_id | 类型/风格 | 番剧的题材类型 | -1=全部, 1=搞笑, 2=运动, 3=热血, 4=恋爱, 5=科幻 等 |
| area | 地区 | 番剧制作地区 | -1=全部, 1=日本, 2=美国, 3=中国, 4=韩国 等 |
| is_finish | 完结状态 | 番剧是否完结 | -1=全部, 0=连载, 1=完结 |
| copyright | 版权 | 版权类型 | -1=全部, 1=独家, 2=其他 |
| season_version | 版本 | 番剧版本类型 | -1=全部, 1=正片, 2=电影, 3=其他 |
| spoken_language_type | 配音 | 音频语言 | -1=全部, 1=原声, 2=中配 |
| season_status | 付费状态 | 观看权限 | -1=全部, 1=免费, 2=付费, 3=会员专享 |
| season_month | 月份 | 上线月份 | -1=全部, 1-12=具体月份 |
| year | 年份 | 上线年份 | -1=全部, 2020, 2021, 2022... |

---

## 2. 获取筛选结果

### 基本信息

- **接口地址**: `/pgc/season/index/result`
- **请求方法**: `GET`
- **接口说明**: 根据筛选条件获取番剧列表结果，支持分页

### 请求参数

#### 基础参数

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| season_type | int | 否 | - | 内容类型：1=番剧, 3=电影, 4=国创 |
| type | int | 否 | 0 | 类型参数 |
| index_type | int | 否 | - | 索引类型 |
| page | int | 是 | - | 页码，从 1 开始 |
| pagesize | int | 是 | - | 每页数量，建议 20-21 |

#### 排序参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| order | string | 否 | 排序字段，值来自条件接口的 `order[].field` |

#### 筛选参数（动态）

根据条件接口返回的 `filter[].field` 动态添加，常见参数如下：

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| style_id | string | 否 | 类型筛选，值来自条件接口对应 filter 的 `values[].keyword` |
| area | string | 否 | 地区筛选 |
| is_finish | string | 否 | 完结状态筛选 |
| copyright | string | 否 | 版权筛选 |
| season_version | string | 否 | 版本筛选 |
| spoken_language_type | string | 否 | 配音筛选 |
| season_status | string | 否 | 付费状态筛选 |
| season_month | string | 否 | 月份筛选 |
| year | string | 否 | 年份筛选 |

> **注意**: 所有筛选参数的值必须使用条件接口返回的 `keyword` 字段，不可自定义

### 请求示例

```http
GET /pgc/season/index/result?season_type=1&type=0&page=1&pagesize=21&order=3&style_id=-1&area=1&is_finish=-1&copyright=-1&season_version=-1&spoken_language_type=-1&season_status=-1&season_month=-1&year=-1
```

### 响应示例

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "has_next": 1,
    "list": [
      {
        "badge": "会员专享",
        "badge_info": {
          "bg_color": "#FB7299",
          "bg_color_night": "#BB5B76",
          "text": "会员专享"
        },
        "badge_type": 0,
        "cover": "http://i0.hdslb.com/bfs/bangumi/image/xxx.jpg",
        "first_ep": {
          "cover": "http://i0.hdslb.com/bfs/archive/xxx.jpg",
          "id": 123456,
          "title": "第1话"
        },
        "index_show": "更新至第12话",
        "is_finish": 0,
        "link": "https://www.bilibili.com/bangumi/play/ss12345",
        "media_id": 12345,
        "order": "9.8",
        "order_type": "score",
        "score": "9.8",
        "season_id": 12345,
        "season_status": 2,
        "season_type": 1,
        "sub_title": "全12话",
        "title": "番剧标题",
        "title_icon": ""
      }
    ],
    "num": 1,
    "size": 21,
    "total": 1234
  }
}
```

### 响应字段说明

#### data 对象

| 字段名 | 类型 | 说明 |
|--------|------|------|
| has_next | int | 是否有下一页：0=无, 1=有 |
| list | array | 番剧列表数据 |
| num | int | 当前页码 |
| size | int | 每页数量 |
| total | int | 符合条件的总结果数 |

#### list 数组元素

| 字段名 | 类型 | 可空 | 说明 |
|--------|------|------|------|
| badge | string | 是 | 角标文字，如 "会员专享"、"独家"、"限免" |
| badge_info | object | 是 | 角标样式信息，包含背景色和文本 |
| badge_type | int | 是 | 角标类型标识 |
| cover | string | 否 | 番剧封面图URL |
| first_ep | object | 是 | 第一集信息 |
| index_show | string | 是 | 进度显示文本，如 "更新至第12话"、"全12话" |
| is_finish | int | 否 | 完结状态：0=连载中, 1=已完结 |
| link | string | 否 | 番剧详情页链接 |
| media_id | int | 否 | 媒体ID |
| order | string | 是 | 排序值，根据排序类型显示评分或追番数等 |
| order_type | string | 是 | 排序类型，如 "score"、"follow" |
| score | string | 是 | 评分（0-10分） |
| season_id | int | 否 | 番剧季度ID（ssid） |
| season_status | int | 否 | 番剧状态 |
| season_type | int | 否 | 内容类型：1=番剧, 3=电影, 4=国创 |
| sub_title | string | 是 | 副标题 |
| title | string | 否 | 番剧标题 |
| title_icon | string | 是 | 标题图标URL |

#### badge_info 对象

| 字段名 | 类型 | 说明 |
|--------|------|------|
| bg_color | string | 日间模式背景色（十六进制颜色代码） |
| bg_color_night | string | 夜间模式背景色（十六进制颜色代码） |
| text | string | 角标显示文本 |

#### first_ep 对象

| 字段名 | 类型 | 说明 |
|--------|------|------|
| cover | string | 第一集封面图URL |
| id | int | 剧集ID（epid） |
| title | string | 剧集标题，如 "第1话"、"第1集" |

---

## 使用流程

### 完整流程图

```
1. 进入番剧索引页
   ↓
2. 调用条件接口获取筛选配置
   ↓
3. 使用默认筛选条件调用结果接口
   ↓
4. 展示番剧列表（第1页）
   ↓
5. 用户选择筛选条件
   ↓
6. 调用结果接口（重置为第1页）
   ↓
7. 展示筛选后的列表
   ↓
8. 用户下拉加载更多
   ↓
9. page+1 继续调用结果接口
   ↓
10. 追加数据到列表
    ↓
11. 判断 has_next，决定是否可继续加载
```

### 初始化流程

```javascript
// 1. 获取筛选条件配置
const conditionResponse = await fetch('/pgc/season/index/condition?season_type=1&type=0');
const conditionData = conditionResponse.data;

// 2. 构建默认参数（选择每个filter的第一个值）
const params = {
  season_type: 1,
  type: 0,
  page: 1,
  pagesize: 21
};

// 添加默认排序
if (conditionData.order && conditionData.order.length > 0) {
  params.order = conditionData.order[0].field;
}

// 添加默认筛选
conditionData.filter.forEach(item => {
  if (item.values && item.values.length > 0) {
    params[item.field] = item.values[0].keyword;
  }
});

// 3. 获取第一页数据
const resultResponse = await fetch('/pgc/season/index/result?' + new URLSearchParams(params));
```

### 更新筛选条件

```javascript
// 用户点击筛选项
function onFilterChange(field, keyword) {
  // 更新参数
  params[field] = keyword;
  // 重置页码
  params.page = 1;
  // 重新请求
  fetchResult(params);
}
```

### 分页加载

```javascript
// 加载下一页
function loadMore() {
  if (has_next === 1) {
    params.page += 1;
    fetchResult(params, true); // true表示追加数据
  }
}
```

---

## 错误处理

### 错误响应格式

```json
{
  "code": -400,
  "message": "请求错误",
  "data": null
}
```

### 常见错误码

| 错误码 | 说明 | 可能原因 |
|--------|------|---------|
| 0 | 成功 | - |
| -400 | 请求错误 | 参数格式错误、缺少必填参数 |
| -403 | 访问权限不足 | 未登录或权限不够 |
| -404 | 资源不存在 | 请求的资源不存在 |
| -500 | 服务器错误 | 服务器内部错误 |

### 错误处理建议

1. **参数验证**: 确保所有参数值来自条件接口，避免传入非法值
2. **网络重试**: 对于网络错误，实现重试机制
3. **用户提示**: 向用户展示友好的错误信息
4. **降级处理**: 筛选条件获取失败时，可使用默认配置

---

## 数据模型

### TypeScript 类型定义

```typescript
// 筛选条件配置响应
interface ConditionResponse {
  code: number;
  message: string;
  data: {
    filter: FilterItem[];
    order: OrderItem[];
  };
}

interface FilterItem {
  field: string;      // 筛选字段标识
  name: string;       // 显示名称
  values: FilterValue[];
}

interface FilterValue {
  keyword: string;    // 参数值
  name: string;       // 显示名称
}

interface OrderItem {
  field: string;      // 排序字段
  name: string;       // 显示名称
  sort: string;       // 排序方式
}

// 筛选结果响应
interface ResultResponse {
  code: number;
  message: string;
  data: {
    has_next: number;
    list: PgcIndexItem[];
    num: number;
    size: number;
    total: number;
  };
}

interface PgcIndexItem {
  badge?: string;
  badge_info?: {
    bg_color: string;
    bg_color_night: string;
    text: string;
  };
  badge_type?: number;
  cover: string;
  first_ep?: {
    cover: string;
    id: number;
    title: string;
  };
  index_show?: string;
  is_finish: number;
  link: string;
  media_id: number;
  order?: string;
  order_type?: string;
  score?: string;
  season_id: number;
  season_status: number;
  season_type: number;
  sub_title?: string;
  title: string;
  title_icon?: string;
}
```

---

## 注意事项

1. **参数值规范**: 所有筛选参数的值必须使用条件接口返回的 `keyword`，不能自定义
2. **分页处理**: 
   - 切换筛选条件时需要重置 `page` 为 1
   - 根据 `has_next` 判断是否还有更多数据
3. **性能优化**:
   - 条件配置数据可缓存，不需要每次都请求
   - 使用虚拟滚动处理大量列表数据
4. **UI展示**:
   - 建议支持展开/收起功能（超过5行筛选项时）
   - 高亮显示当前选中的筛选值
5. **兼容性**: 不同 `season_type` 可能返回不同的筛选字段

---

## 版本历史

- **v1.0** (2024): 初始版本
- 接口路径: `/pgc/season/index/condition` 和 `/pgc/season/index/result`

---

## 相关接口

- 番剧详情: `/pgc/view/web/season`
- 番剧时间表: `/pgc/web/timeline`
- 追番/追剧: `/pgc/web/follow/add`

---

## 附录

### 代码位置

- **Controller**: `lib/pages/pgc_index/controller.dart`
- **View**: `lib/pages/pgc_index/view.dart`
- **HTTP请求**: `lib/http/pgc.dart`
- **数据模型**: `lib/models_new/pgc/pgc_index_condition/`

### 相关文档

- [BiliBili API 收集](https://github.com/SocialSisterYi/bilibili-API-collect)
- [Flutter GetX 文档](https://pub.dev/packages/get)
