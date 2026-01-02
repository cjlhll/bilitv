# 追番/追剧列表 API 文档

## 1. 获取我的追番/追剧列表

**接口地址：** `/x/space/bangumi/follow/list`

**请求方法：** GET

**请求头（Headers）：**

| Header名 | 类型 | 必填 | 说明 |
|----------|------|------|------|
| user-agent | String | 是 | 用户代理，例如：`Dart/3.6 (dart:io)` |
| accept-encoding | String | 是 | 接受的编码，例如：`br,gzip` |
| connection | String | 否 | 连接类型，例如：`keep-alive` |
| cookie | String | 是 | **认证Cookie，必须包含登录信息** |
| referer | String | 是 | 来源地址，例如：`https://www.bilibili.com` |
| env | String | 是 | 环境标识，固定值：`prod` |
| app-key | String | 是 | 应用标识，固定值：`android64` |
| x-bili-aurora-zone | String | 是 | 区域标识，固定值：`sh001` |
| x-bili-mid | String | 是 | 用户ID，从Cookie中的DedeUserID获取 |
| x-bili-aurora-eid | String | 是 | 设备ID，自动生成的唯一标识 |

**Cookie 必须包含的字段：**

| Cookie名 | 说明 | 获取方式 |
|----------|------|----------|
| buvid3 | 浏览器/设备指纹 | 自动生成 |
| DedeUserID | 用户ID | 登录后获取 |
| SESSDATA | 会话数据 | 登录后获取 |
| bili_jct | CSRF Token | 登录后获取 |
| DedeUserID__ckMd5 | 用户ID的MD5 | 登录后获取 |

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| vmid | int | 否 | 用户ID，默认为当前登录用户 |
| type | int | 是 | 类型：0-追番（番剧），1-追剧 |
| follow_status | int | 否 | 追番状态：1-想看，2-在看，3-看过 |
| pn | int | 是 | 页码 |

**返回数据结构：**

```dart
{
  "code": 0,
  "data": {
    "list": [
      {
        "season_id": int,              // 番剧ID
        "media_id": int,               // 媒体ID
        "season_type": int,            // 季度类型
        "season_type_name": String,    // 季度类型名称
        "title": String,               // 标题
        "cover": String,               // 封面
        "total_count": int,            // 总集数
        "is_finish": int,              // 是否完结
        "is_started": int,             // 是否开播
        "is_play": int,                // 是否可播放
        "badge": String,               // 徽章
        "badge_type": int,             // 徽章类型
        "rights": Object,              // 权限信息
        "stat": Object,                // 统计信息
        "new_ep": Object,              // 最新剧集
        "rating": Object,              // 评分
        "square_cover": String,        // 方形封面
        "season_status": int,          // 季度状态
        "season_title": String,        // 季度标题
        "badge_ep": String,            // 剧集徽章
        "media_attr": int,             // 媒体属性
        "season_attr": int,            // 季度属性
        "evaluate": String,            // 评价
        "areas": Array,                // 地区
        "subtitle": String,            // 副标题
        "first_ep": int,               // 第一集
        "can_watch": int,              // 是否可观看
        "series": Object,              // 系列信息
        "publish": Object,             // 发布信息
        "mode": int,                   // 模式
        "section": Array,              // 分区
        "url": String,                 // URL
        "badge_info": Object,          // 徽章信息
        "renewal_time": String,        // 更新时间
        "first_ep_info": Object,       // 第一集信息
        "formal_ep_count": int,        // 正式集数
        "short_url": String,           // 短URL
        "badge_infos": Object,         // 徽章信息集合
        "season_version": String,      // 季度版本
        "horizontal_cover_16_9": String, // 16:9横封面
        "horizontal_cover_16_10": String, // 16:10横封面
        "subtitle_14": String,         // 副标题14
        "viewable_crowd_type": int,    // 可观看人群类型
        "producers": Array,            // 制作方
        "summary": String,             // 简介
        "styles": Array,               // 风格
        "config_attrs": Object,        // 配置属性
        "follow_status": int,          // 追番状态
        "is_new": int,                 // 是否新番
        "progress": String,            // 进度
        "both_follow": bool,           // 双方关注
        "subtitle_25": String          // 副标题25
      }
    ],
    "pn": int,     // 当前页码
    "ps": int,     // 每页数量
    "total": int   // 总数
  }
}
```

---

## 2. 添加追番/追剧

**接口地址：** `/pgc/web/follow/add`

**请求方法：** POST

**请求参数（form-urlencoded）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| season_id | int | 是 | 番剧ID |
| csrf | String | 是 | CSRF Token |

**返回数据结构：**

```dart
{
  "code": 0,
  "result": {
    "toast": String  // 提示信息
  }
}
```

---

## 3. 取消追番/追剧

**接口地址：** `/pgc/web/follow/del`

**请求方法：** POST

**请求参数（form-urlencoded）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| season_id | int | 是 | 番剧ID |
| csrf | String | 是 | CSRF Token |

**返回数据结构：**

```dart
{
  "code": 0,
  "result": {
    "toast": String  // 提示信息
  }
}
```

---

## 4. 更新追番/追剧状态

**接口地址：** `/pgc/web/follow/status/update`

**请求方法：** POST

**请求参数（form-urlencoded）：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| season_id | String | 是 | 番剧ID（多个ID用逗号分隔） |
| status | int | 是 | 状态：1-想看，2-在看，3-看过 |
| csrf | String | 是 | CSRF Token |

**返回数据结构：**

```dart
{
  "code": 0,
  "result": {
    "toast": String  // 提示信息
  }
}
```

---

## 5. 获取番剧状态

**接口地址：** `/pgc/view/web/season/user/status`

**请求方法：** GET

**请求参数：**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| season_id | int | 是 | 番剧ID |

**返回数据结构：**

```dart
{
  "code": 0,
  "result": Object  // 番剧状态信息
}
```

---

## 参数说明

### type 参数（类型）
- `0`：追番（番剧）
- `1`：追剧

### follow_status 参数（追番状态）
- `1`：想看
- `2`：在看
- `3`：看过
- `-1`：未追番

### season_status 参数（季度状态）
- `1`：未开播
- `2`：连载中
- `3`：已完结

### is_finish 参数（是否完结）
- `0`：未完结
- `1`：已完结

### is_started 参数（是否开播）
- `0`：未开播
- `1`：已开播

---

## 常见错误说明

### 400 Bad Request 错误

**可能原因：**

1. **缺少必要的 Cookie**
   - 未登录或 Cookie 已过期
   - 缺少 `DedeUserID`、`SESSDATA`、`bili_jct` 等必要字段
   - 解决方案：确保用户已登录，Cookie 完整有效

2. **缺少必要的 Headers**
   - 缺少 `x-bili-mid`、`x-bili-aurora-eid` 等自定义头部
   - 缺少 `referer` 头部
   - 解决方案：按照上述请求头列表添加所有必要的头部

3. **参数错误**
   - `type` 参数缺失或值不正确（必须是 0 或 1）
   - `pn` 参数缺失或值不正确（必须大于 0）
   - 解决方案：检查请求参数是否完整且正确

4. **Cookie 格式错误**
   - Cookie 字符串格式不正确
   - Cookie 值包含非法字符
   - 解决方案：确保 Cookie 格式为 `key1=value1; key2=value2; ...`

5. **用户未登录**
   - Cookie 中没有有效的登录信息
   - 解决方案：先进行登录操作获取有效的 Cookie

**调试建议：**

1. 检查 Cookie 是否包含所有必要字段：
   ```
   Cookie: buvid3=xxx; DedeUserID=xxx; SESSDATA=xxx; bili_jct=xxx; DedeUserID__ckMd5=xxx
   ```

2. 检查 Headers 是否完整：
   ```
   Headers: {
     "user-agent": "Dart/3.6 (dart:io)",
     "accept-encoding": "br,gzip",
     "cookie": "...",
     "referer": "https://www.bilibili.com",
     "env": "prod",
     "app-key": "android64",
     "x-bili-aurora-zone": "sh001",
     "x-bili-mid": "用户ID",
     "x-bili-aurora-eid": "设备ID"
   }
   ```

3. 检查请求参数：
   ```
   Query: {
     "vmid": "用户ID",
     "type": "0或1",
     "follow_status": "1/2/3",
     "pn": "页码"
   }
   ```

### 其他错误码

- `-101`：账号未登录
- `-111`：CSRF 校验失败
- `-400`：请求参数错误
- `403`：无权限访问
- `412`：请求被拦截