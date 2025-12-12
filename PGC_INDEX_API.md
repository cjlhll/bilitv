# 筛选条件API接口文档

## 接口地址
```
GET https://api.bilibili.com/pgc/season/index/condition
```

## 请求参数说明

| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| season_type | int | 否 | 1 | 季类型：1-番剧，2-电影，3-纪录片，4-国创，5-电视剧，7-综艺 |
| type | int | 否 | 0 | 类型：固定为0 |
| index_type | int | 否 | null | 索引类型：102-影视，null-番剧 |

## 请求示例

### 番剧筛选条件
```bash
GET https://api.bilibili.com/pgc/season/index/condition?season_type=1&type=0
```

### 影视筛选条件
```bash
GET https://api.bilibili.com/pgc/season/index/condition?season_type=1&type=0&index_type=102
```

## 返回参数说明

### 响应结构
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "filter": [],
    "order": []
  }
}
```

### data字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| filter | array | 筛选条件列表（地区、年份、风格等） |
| order | array | 排序选项列表 |

## filter数组结构说明

### filter对象结构
```json
{
  "field": "area",
  "name": "地区",
  "values": []
}
```

| 字段名 | 类型 | 说明 |
|--------|------|------|
| field | string | 筛选字段名，用于请求参数 |
| name | string | 筛选类别名称，用于显示 |
| values | array | 筛选选项列表 |

### values对象结构
```json
{
  "keyword": "1",
  "name": "日本"
}
```

| 字段名 | 类型 | 说明 |
|--------|------|------|
| keyword | string | 筛选选项值，用于请求参数 |
| name | string | 筛选选项名称，用于显示 |

## order数组结构说明

### order对象结构
```json
{
  "field": "order",
  "name": "排序",
  "sort": "0"
}
```

| 字段名 | 类型 | 说明 |
|--------|------|------|
| field | string | 排序字段名，用于请求参数 |
| name | string | 排序类别名称，用于显示 |
| sort | string | 排序值，用于请求参数 |

## 完整返回示例

### 番剧筛选条件返回示例
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "filter": [
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
            "name": "国产"
          },
          {
            "keyword": "2",
            "name": "日本"
          },
          {
            "keyword": "3",
            "name": "韩国"
          },
          {
            "keyword": "4",
            "name": "美国"
          },
          {
            "keyword": "5",
            "name": "英国"
          },
          {
            "keyword": "6",
            "name": "法国"
          },
          {
            "keyword": "7",
            "name": "其他"
          }
        ]
      },
      {
        "field": "style_id",
        "name": "风格",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "原创"
          },
          {
            "keyword": "2",
            "name": "漫画改"
          },
          {
            "keyword": "3",
            "name": "小说改"
          },
          {
            "keyword": "4",
            "name": "游戏改"
          },
          {
            "keyword": "5",
            "name": "特摄"
          },
          {
            "keyword": "6",
            "name": "布袋戏"
          },
          {
            "keyword": "7",
            "name": "热血"
          },
          {
            "keyword": "8",
            "name": "穿越"
          },
          {
            "keyword": "9",
            "name": "奇幻"
          },
          {
            "keyword": "10",
            "name": "恋爱"
          },
          {
            "keyword": "11",
            "name": "校园"
          },
          {
            "keyword": "12",
            "name": "搞笑"
          },
          {
            "keyword": "13",
            "name": "日常"
          },
          {
            "keyword": "14",
            "name": "科幻"
          },
          {
            "keyword": "15",
            "name": "萌系"
          },
          {
            "keyword": "16",
            "name": "治愈"
          },
          {
            "keyword": "17",
            "name": "职场"
          },
          {
            "keyword": "18",
            "name": "推理"
          },
          {
            "keyword": "19",
            "name": "冒险"
          },
          {
            "keyword": "20",
            "name": "悬疑"
          },
          {
            "keyword": "21",
            "name": "历史"
          },
          {
            "keyword": "22",
            "name": "战争"
          },
          {
            "keyword": "23",
            "name": "竞技"
          },
          {
            "keyword": "24",
            "name": "魔法"
          },
          {
            "keyword": "25",
            "name": "机战"
          },
          {
            "keyword": "26",
            "name": "未来"
          },
          {
            "keyword": "27",
            "name": "美食"
          },
          {
            "keyword": "28",
            "name": "偶像"
          },
          {
            "keyword": "29",
            "name": "乙女"
          },
          {
            "keyword": "30",
            "name": "音乐"
          }
        ]
      },
      {
        "field": "is_finish",
        "name": "连载状态",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "0",
            "name": "连载中"
          },
          {
            "keyword": "1",
            "name": "已完结"
          }
        ]
      },
      {
        "field": "season_status",
        "name": "付费类型",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "免费"
          },
          {
            "keyword": "2",
            "name": "付费"
          },
          {
            "keyword": "3",
            "name": "大会员"
          }
        ]
      },
      {
        "field": "season_month",
        "name": "播出时间",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "1月"
          },
          {
            "keyword": "2",
            "name": "2月"
          },
          {
            "keyword": "3",
            "name": "3月"
          },
          {
            "keyword": "4",
            "name": "4月"
          },
          {
            "keyword": "5",
            "name": "5月"
          },
          {
            "keyword": "6",
            "name": "6月"
          },
          {
            "keyword": "7",
            "name": "7月"
          },
          {
            "keyword": "8",
            "name": "8月"
          },
          {
            "keyword": "9",
            "name": "9月"
          },
          {
            "keyword": "10",
            "name": "10月"
          },
          {
            "keyword": "11",
            "name": "11月"
          },
          {
            "keyword": "12",
            "name": "12月"
          }
        ]
      },
      {
        "field": "year",
        "name": "年份",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "2024",
            "name": "2024"
          },
          {
            "keyword": "2023",
            "name": "2023"
          },
          {
            "keyword": "2022",
            "name": "2022"
          },
          {
            "keyword": "2021",
            "name": "2021"
          },
          {
            "keyword": "2020",
            "name": "2020"
          },
          {
            "keyword": "2019",
            "name": "2019"
          },
          {
            "keyword": "2018",
            "name": "2018"
          },
          {
            "keyword": "2017",
            "name": "2017"
          },
          {
            "keyword": "2016",
            "name": "2016"
          },
          {
            "keyword": "2015",
            "name": "2015"
          }
        ]
      },
      {
        "field": "season_version",
        "name": "版本",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "正片"
          },
          {
            "keyword": "2",
            "name": "电影"
          },
          {
            "keyword": "3",
            "name": "其他"
          }
        ]
      }
    ],
    "order": [
      {
        "field": "order",
        "name": "排序",
        "sort": "0"
      },
      {
        "field": "order",
        "name": "播放最多",
        "sort": "2"
      },
      {
        "field": "order",
        "name": "最新上架",
        "sort": "1"
      },
      {
        "field": "order",
        "name": "评分最高",
        "sort": "3"
      }
    ]
  }
}
```

### 影视筛选条件返回示例
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "filter": [
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
            "name": "国产"
          },
          {
            "keyword": "2",
            "name": "日本"
          },
          {
            "keyword": "3",
            "name": "韩国"
          },
          {
            "keyword": "4",
            "name": "美国"
          },
          {
            "keyword": "5",
            "name": "英国"
          },
          {
            "keyword": "6",
            "name": "法国"
          },
          {
            "keyword": "7",
            "name": "其他"
          }
        ]
      },
      {
        "field": "style_id",
        "name": "类型",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "2",
            "name": "电影"
          },
          {
            "keyword": "5",
            "name": "电视剧"
          },
          {
            "keyword": "3",
            "name": "纪录片"
          },
          {
            "keyword": "7",
            "name": "综艺"
          }
        ]
      },
      {
        "field": "is_finish",
        "name": "完结状态",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "0",
            "name": "连载中"
          },
          {
            "keyword": "1",
            "name": "已完结"
          }
        ]
      },
      {
        "field": "season_status",
        "name": "付费类型",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "1",
            "name": "免费"
          },
          {
            "keyword": "2",
            "name": "付费"
          },
          {
            "keyword": "3",
            "name": "大会员"
          }
        ]
      },
      {
        "field": "year",
        "name": "年份",
        "values": [
          {
            "keyword": "-1",
            "name": "全部"
          },
          {
            "keyword": "2024",
            "name": "2024"
          },
          {
            "keyword": "2023",
            "name": "2023"
          },
          {
            "keyword": "2022",
            "name": "2022"
          },
          {
            "keyword": "2021",
            "name": "2021"
          },
          {
            "keyword": "2020",
            "name": "2020"
          }
        ]
      }
    ],
    "order": [
      {
        "field": "order",
        "name": "排序",
        "sort": "0"
      },
      {
        "field": "order",
        "name": "播放最多",
        "sort": "2"
      },
      {
        "field": "order",
        "name": "最新上架",
        "sort": "1"
      },
      {
        "field": "order",
        "name": "评分最高",
        "sort": "3"
      }
    ]
  }
}
```

## 使用说明

### 1. 获取筛选条件

首先调用筛选条件接口获取可用的筛选选项：

```javascript
// 获取番剧筛选条件
fetch('https://api.bilibili.com/pgc/season/index/condition?season_type=1&type=0')
  .then(response => response.json())
  .then(data => {
    // 处理筛选条件数据
    const filters = data.data.filter;
    const orders = data.data.order;
  });
```

### 2. 应用筛选条件

使用获取到的筛选条件构建请求参数：

```javascript
// 构建筛选参数
const params = {
  page: 1,
  pagesize: 20,
  season_type: 1,
  type: 1,
  // 从筛选条件中选择
  area: '2',  // 日本
  style_id: '7',  // 热血
  is_finish: '0',  // 连载中
  order: '3',  // 评分最高
  year: '2023'  // 2023年
};

// 使用筛选参数请求列表
fetch(`https://api.bilibili.com/pgc/season/index/result?${new URLSearchParams(params)}`)
  .then(response => response.json())
  .then(data => {
    // 处理列表数据
  });
```

## 注意事项

1. **参数映射**：筛选条件中的 `field` 字段对应列表接口的参数名
2. **值映射**：筛选选项中的 `keyword` 字段对应列表接口的参数值
3. **默认值**：通常使用第一个选项作为默认值（keyword为-1的"全部"选项）
4. **动态性**：筛选条件可能会根据后台配置动态变化，建议每次都重新获取
5. **错误处理**：接口可能返回错误，需要做好错误处理和降级方案

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| -400 | 请求错误 |
| -404 | 资源不存在 |
| -500 | 服务器内部错误 |
| 10001 | 参数错误 |
| 10002 | 签名错误 |
| 10003 | 权限不足 |