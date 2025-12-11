# 推荐功能API接口文档

## 1. 获取推荐列表接口

### 接口地址
```
GET https://api.bilibili.com/pgc/season/index/result
```

### 请求参数说明

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| st | int | 否 | 1 | 起始位置，固定为1 |
| order | int | 否 | 3 | 排序方式：0-默认，1-播放量，2-评分，3-更新时间 |
| season_version | int | 否 | -1 | 季版本：-1-全部，其他值对应具体版本 |
| spoken_language_type | int | 否 | -1 | 语言类型：-1-全部，其他值对应具体语言 |
| area | int | 否 | -1 | 地区：-1-全部，1-国产，2-日本，3-韩国，4-美国，5-英国，6-法国，7-其他 |
| is_finish | int | 否 | -1 | 是否完结：-1-全部，0-未完结，1-已完结 |
| copyright | int | 否 | -1 | 版权：-1-全部，其他值对应具体版权类型 |
| season_status | int | 否 | -1 | 季状态：-1-全部，其他值对应具体状态 |
| season_month | int | 否 | -1 | 月份：-1-全部，1-12对应具体月份 |
| year | int | 否 | -1 | 年份：-1-全部，其他值为具体年份 |
| style_id | int | 否 | -1 | 风格ID：-1-全部，其他值对应具体风格 |
| sort | int | 否 | 0 | 排序：固定为0 |
| season_type | int | 否 | 1 | 季类型：1-番剧，2-电影，3-纪录片，4-国创，5-电视剧，7:综艺 |
| pagesize | int | 否 | 20 | 每页数量，建议20 |
| type | int | 否 | 1 | 类型：固定为1 |
| page | int | 是 | 1 | 页码，从1开始 |
| index_type | int | 否 | null | 索引类型：102-影视，null-番剧 |

### 请求示例
```bash
GET https://api.bilibili.com/pgc/season/index/result?st=1&order=3&season_version=-1&spoken_language_type=-1&area=-1&is_finish=-1&copyright=-1&season_status=-1&season_month=-1&year=-1&style_id=-1&sort=0&season_type=1&pagesize=20&type=1&page=1
```

### 返回参数说明

#### 响应结构
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "total": 0,
    "num": 0,
    "size": 0,
    "page": 0
  }
}
```

#### data字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| list | array | 推荐内容列表 |
| total | int | 总数量 |
| num | int | 当前页数量 |
| size | int | 每页大小 |
| page | int | 当前页码 |

#### list数组中每个对象的字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| badge | string | 徽章文本，如"独播"、"完结"等 |
| badge_info | object | 徽章详细信息 |
| badge_type | int | 徽章类型：1-独播，2-完结，3-付费，4-其他 |
| cover | string | 封面图片URL |
| first_ep | object | 第一集信息 |
| index_show | string | 索引显示文本，如"全12话"、"更新至第8话"等 |
| is_finish | int | 是否完结：0-未完结，1-已完结 |
| link | string | 链接地址 |
| media_id | int | 媒体ID |
| order | string | 排序标识，如"第1名"、"新作"等 |
| order_type | string | 排序类型 |
| score | string | 评分，如"9.8分" |
| season_id | int | 季ID，用于跳转详情页 |
| season_status | int | 季状态 |
| season_type | int | 季类型：1-番剧，2-电影，3-纪录片，4-国创，5-电视剧，7-综艺 |
| subTitle | string | 副标题 |
| title | string | 标题 |
| title_icon | string | 标题图标URL |

#### badge_info对象字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| text | string | 徽章文本 |
| text_color | string | 文本颜色，十六进制格式 |
| bg_color | string | 背景颜色，十六进制格式 |
| border_color | string | 边框颜色，十六进制格式 |
| bg_style | int | 背景样式 |

#### first_ep对象字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| cover | string | 第一集封面URL |
| title | string | 第一集标题 |
| ep_id | int | 第一集ID |

### 返回示例
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "badge": "独播",
        "badge_info": {
          "text": "独播",
          "text_color": "#FFFFFF",
          "bg_color": "#FB7299",
          "border_color": "#FB7299",
          "bg_style": 1
        },
        "badge_type": 1,
        "cover": "http://i0.hdslb.com/bfs/bangumi/123456.jpg",
        "first_ep": {
          "cover": "http://i0.hdslb.com/bfs/bangumi/123456_ep1.jpg",
          "title": "第1话",
          "ep_id": 123456
        },
        "index_show": "全12话",
        "is_finish": 1,
        "link": "https://www.bilibili.com/bangumi/play/ss12345",
        "media_id": 123456,
        "order": "第1名",
        "order_type": "hot",
        "score": "9.8分",
        "season_id": 12345,
        "season_status": 1,
        "season_type": 1,
        "subTitle": "副标题内容",
        "title": "番剧标题",
        "title_icon": "http://i0.hdslb.com/bfs/bangumi/icon.png"
      }
    ],
    "total": 100,
    "num": 20,
    "size": 20,
    "page": 1
  }
}
```

## 2. 获取筛选条件接口

### 接口地址
```
GET https://api.bilibili.com/pgc/season/index/condition
```

### 请求参数说明

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| season_type | int | 否 | 1 | 季类型：1-番剧，2-电影，3-纪录片，4-国创，5-电视剧，7-综艺 |
| index_type | int | 否 | null | 索引类型：102-影视，null-番剧 |

### 请求示例
```bash
GET https://api.bilibili.com/pgc/season/index/condition?season_type=1
```

### 返回参数说明

#### 响应结构
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "areas": [],
    "years": [],
    "styles": [],
    "seasons": [],
    "status": [],
    "orders": [],
    "prices": [],
    "versions": []
  }
}
```

#### data字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| areas | array | 地区列表 |
| years | array | 年份列表 |
| styles | array | 风格列表 |
| seasons | array | 季列表 |
| status | array | 状态列表 |
| orders | array | 排序列表 |
| prices | array | 价格列表 |
| versions | array | 版本列表 |

#### 各数组中每个对象的字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | int | 选项ID，用于筛选请求 |
| name | string | 选项名称，用于显示 |
| count | int | 该选项下的内容数量 |

### 返回示例
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "areas": [
      {
        "id": -1,
        "name": "全部",
        "count": 100
      },
      {
        "id": 1,
        "name": "国产",
        "count": 30
      },
      {
        "id": 2,
        "name": "日本",
        "count": 50
      }
    ],
    "years": [
      {
        "id": -1,
        "name": "全部",
        "count": 100
      },
      {
        "id": 2024,
        "name": "2024",
        "count": 40
      },
      {
        "id": 2023,
        "name": "2023",
        "count": 35
      }
    ],
    "styles": [
      {
        "id": -1,
        "name": "全部",
        "count": 100
      },
      {
        "id": 1,
        "name": "战斗",
        "count": 20
      },
      {
        "id": 2,
        "name": "热血",
        "count": 25
      }
    ],
    "seasons": [],
    "status": [
      {
        "id": -1,
        "name": "全部",
        "count": 100
      },
      {
        "id": 0,
        "name": "连载中",
        "count": 60
      },
      {
        "id": 1,
        "name": "已完结",
        "count": 40
      }
    ],
    "orders": [
      {
        "id": 0,
        "name": "默认",
        "count": 0
      },
      {
        "id": 1,
        "name": "播放量",
        "count": 0
      },
      {
        "id": 2,
        "name": "评分",
        "count": 0
      },
      {
        "id": 3,
        "name": "更新时间",
        "count": 0
      }
    ],
    "prices": [],
    "versions": []
  }
}
```

## 3. 搜索接口

### 接口地址
```
GET https://api.bilibili.com/search/season
```

### 请求参数说明

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| keyword | string | 是 | - | 搜索关键词 |
| page | int | 否 | 1 | 页码，从1开始 |
| pagesize | int | 否 | 20 | 每页数量 |

### 请求示例
```bash
GET https://api.bilibili.com/search/season?keyword=进击的巨人&page=1&pagesize=20
```

### 返回参数说明

#### 响应结构
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "result": [],
    "seid": "1234567890"
  }
}
```

#### data字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| result | array | 搜索结果列表 |
| seid | string | 搜索会话ID |

#### result数组中每个对象的字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| media_id | int | 媒体ID |
| season_id | int | 季ID |
| title | string | 标题 |
| subtitle | string | 副标题 |
| description | string | 描述 |
| cover | string | 封面图片URL |
| url | string | 链接地址 |
| hit_columns | string | 高亮显示的列 |
| score | string | 评分 |
| eps | int | 集数 |
| season_type_name | string | 季类型名称 |
| area | string | 地区 |
| pub_date | string | 发布日期 |
| styles | string | 风格标签 |
| cv | string | 声优 |
| staff | string | 制作人员 |

### 返回示例
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "result": [
      {
        "media_id": 123456,
        "season_id": 12345,
        "title": "进击的巨人",
        "subtitle": "最终季",
        "description": "人类与巨人的战斗故事",
        "cover": "http://i0.hdslb.com/bfs/bangumi/123456.jpg",
        "url": "https://www.bilibili.com/bangumi/play/ss12345",
        "hit_columns": "title",
        "score": "9.8分",
        "eps": 12,
        "season_type_name": "番剧",
        "area": "日本",
        "pub_date": "2023-01-01",
        "styles": "战斗,热血,奇幻",
        "cv": "声优信息",
        "staff": "制作人员信息"
      }
    ],
    "seid": "1234567890"
  }
}
```

## 4. 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 资源不存在 |
| -500 | 服务器内部错误 |
| 10001 | 参数错误 |
| 10002 | 签名错误 |
| 10003 | 权限不足 |

## 5. 注意事项

1. 所有接口都需要正确的User-Agent和Referer头
2. 建议添加适当的请求间隔，避免频繁请求
3. 图片URL可能需要添加防盗链参数
4. 部分接口可能需要登录状态
5. 建议实现适当的缓存机制，减少重复请求
6. 注意处理网络异常和超时情况