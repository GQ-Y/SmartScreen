# MineAdmin 智慧屏插件 WebSocket 连接标准

## 1. 连接信息

- **默认端口**：9502
- **默认地址**：ws://localhost:9502/ws
- **协议**：WebSocket
- **数据格式**：JSON

## 2. 启动WebSocket服务

```bash
# 启动智慧屏WebSocket服务
php bin/hyperf.php smartscreen:websocket-server

# 指定端口和地址
php bin/hyperf.php smartscreen:websocket-server --host=0.0.0.0 --port=9502
```

## 3. 连接流程

### 3.1 建立连接

客户端通过WebSocket连接到服务端后，需要进行设备注册才能正常通信。

### 3.2 设备注册（自动注册优化版）

连接建立后，必须首先发送注册消息：

**客户端发送：**

```json
{
  "type": "register",
  "mac": "AA:BB:CC:DD:EE:FF",
  "device_name": "智慧屏设备001"
}
```

**服务端响应：**

```json
{
  "type": "register_ack",
  "success": true,
  "active": 0,
  "device_id": 123,
  "is_new_device": true,
  "msg": "设备已自动注册成功！设备未激活，请联系管理员激活"
}
```

**字段说明：**

- `mac`: 设备MAC地址，必须唯一且已在后台注册
- `success`: 注册是否成功
- `active`: 设备激活状态（1=已激活，0=未激活）
- `device_id`: 设备在数据库中的ID
- `is_new_device`: 是否为新设备
- `msg`: 响应消息

### 自动注册逻辑说明

1. **设备存在时**：

   - 更新设备在线状态
   - 更新最后在线时间
   - 返回设备当前状态
2. **设备不存在时**：

   - 自动创建新设备记录
   - 设置默认设备名称（如未提供）
   - 默认状态为未激活（status=0）
   - 设置为在线状态（is_online=1）
   - 默认播放策略为播放列表优先（display_mode=1）
3. **设备名称规则**：

   - 如果注册时提供了device_name，使用提供的名称
   - 如果未提供，自动生成格式：`SmartScreen-XXXXXXXX`（后8位为MAC地址后8位的大写）

## 4. 消息类型

### 4.1 心跳消息

定期发送心跳保持连接：

**客户端发送：**

```json
{
  "type": "heartbeat",
  "mac": "AA:BB:CC:DD:EE:FF"
}
```

**服务端响应：**

```json
{
  "type": "heartbeat_ack",
  "success": true,
  "active": 1,
  "msg": "心跳成功"
}
```

### 4.2 获取内容（优化版）

主动获取当前应显示的内容：

**客户端发送：**

```json
{
    "type": "get_content",
    "mac": "AA:BB:CC:DD:EE:FF"
}
```

**服务端响应：**

```json
{
    "type": "content_response",
    "success": true,
    "msg": "获取内容成功，当前策略：播放列表优先",
    "data": {
        "device_id": 123,
        "display_mode": 1,
        "display_mode_name": "播放列表优先",
        "direct_content": {
            "id": 456,
            "title": "直接内容标题",
            "content_type": "video",
            "content_url": "http://example.com/direct.mp4",
            "thumbnail": "http://example.com/direct_thumb.jpg",
            "duration": 120
        },
        "playlist_contents": [
            {
                "id": 789,
                "title": "播放列表内容1",
                "content_type": "image",
                "content_url": "http://example.com/playlist1.jpg",
                "thumbnail": "http://example.com/playlist1_thumb.jpg",
                "duration": 60,
                "playlist_id": 10,
                "playlist_name": "默认播放列表",
                "play_mode": 1,
                "playlist_sort": 1,
                "content_sort": 1
            },
            {
                "id": 790,
                "title": "播放列表内容2",
                "content_type": "video",
                "content_url": "http://example.com/playlist2.mp4",
                "thumbnail": "http://example.com/playlist2_thumb.jpg",
                "duration": 180,
                "playlist_id": 10,
                "playlist_name": "默认播放列表",
                "play_mode": 1,
                "playlist_sort": 1,
                "content_sort": 2
            }
        ],
        "has_direct_content": true,
        "has_playlist_contents": true,
        "primary_contents": [
            // 根据播放策略，优先播放的内容列表
        ],
        "secondary_contents": [
            // 根据播放策略，次要播放的内容列表
        ],
        "total_contents": 3
    }
}
```

**响应消息 - 无内容**

```json
{
    "type": "content_response",
    "success": false,
    "msg": "当前播放策略（仅播放列表）下暂无可播放内容",
    "data": {
        "device_id": 123,
        "display_mode": 3,
        "display_mode_name": "仅播放列表",
        "direct_content": null,
        "playlist_contents": [],
        "has_direct_content": false,
        "has_playlist_contents": false,
        "primary_contents": [],
        "secondary_contents": [],
        "total_contents": 0
    }
}
```

### 播放策略说明

设备支持4种播放策略（display_mode）：

1. **播放列表优先（display_mode: 1）**

   - primary_contents: 播放列表的所有内容
   - secondary_contents: 直接关联的内容（如果有）
2. **直接内容优先（display_mode: 2）**

   - primary_contents: 直接关联的内容（如果有）
   - secondary_contents: 播放列表的所有内容
3. **仅播放列表（display_mode: 3）**

   - primary_contents: 播放列表的所有内容
   - secondary_contents: 空
4. **仅直接内容（display_mode: 4）**

   - primary_contents: 直接关联的内容（如果有）
   - secondary_contents: 空

### 内容字段说明

**直接内容字段（direct_content）：**

- `id`: 内容ID
- `title`: 内容标题
- `content_type`: 内容类型
- `content_url`: 内容文件URL
- `thumbnail`: 缩略图URL
- `duration`: 播放时长（秒）

**播放列表内容字段（playlist_contents）：**

- 包含直接内容的所有字段，另外还有：
- `playlist_id`: 播放列表ID
- `playlist_name`: 播放列表名称
- `play_mode`: 播放列表播放模式
- `playlist_sort`: 播放列表排序
- `content_sort`: 内容在播放列表中的排序

### 4.3 服务端主动推送

#### 激活状态变更

```json
{
  "type": "active_status",
  "active": true,
  "msg": "设备已激活"
}
```

#### 内容推送

```json
{
  "type": "push_content",
  "data": {
    "content_id": 456,
    "title": "紧急通知",
    "content_type": "image",
    "content_url": "http://example.com/notice.jpg",
    "thumbnail": "http://example.com/notice_thumb.jpg",
    "duration": 60
  }
}
```

#### 播放策略变更

```json
{
  "type": "display_mode_change",
  "mode": 2,
  "mode_name": "直接内容优先"
}
```

**播放策略说明：**

- `1`: 播放列表优先
- `2`: 直接内容优先
- `3`: 仅播放列表
- `4`: 仅直接内容

#### 临时内容推送

```json
{
  "type": "temp_content",
  "data": {
    "content_id": 123,
    "content_type": 1,
    "content_url": "https://example.com/page.html",
    "title": "临时内容",
    "duration": 30,
    "thumbnail": "",
    "is_temp": true
  }
}
```

#### 批量控制指令

```json
{
  "type": "batch_control",
  "action": "restart",
  "message": "系统重启通知",
  "timestamp": 1640995200
}
```

### 4.4 错误响应

```json
{
  "type": "error",
  "msg": "错误描述"
}
```

## 5. 连接状态管理

### 5.1 设备内存表

服务端使用 Swoole Table 维护设备连接状态：

- `fd`: 连接文件描述符
- `mac`: 设备MAC地址
- `active`: 激活状态
- `device_id`: 设备ID
- `last_heartbeat`: 最后心跳时间

### 5.2 设备状态同步

- 设备注册时从数据库获取真实激活状态
- 设备激活/禁用时实时推送状态变更
- 设备上线时更新数据库 `is_online` 和 `last_online_time`

## 6. 错误处理

### 6.1 常见错误

- 消息格式错误：JSON格式不正确
- 设备未注册：MAC地址不存在或未在后台添加
- 设备未激活：设备状态为禁用
- 缺少必要参数：如MAC地址等

### 6.2 连接断开

- 客户端主动断开
- 网络异常断开
- 服务端清理断开的连接

## 7. 安全考虑

### 7.1 MAC地址验证

- 必须在管理后台预先注册设备
- MAC地址格式验证
- 防止重复连接

### 7.2 消息过滤

- 严格的消息类型检查
- 参数有效性验证
- 防止恶意消息

## 8. 性能优化

### 8.1 内存表

使用 Swoole Table 提高连接状态查询性能

### 8.2 心跳机制

定期心跳检测连接状态，及时清理无效连接

### 8.3 单进程模式

当前实现为单进程模式，适用于中小规模部署。如需分布式部署，需要使用Redis等进行进程间通信。

## 9. 测试建议

### 9.1 连接测试

1. 建立WebSocket连接
2. 发送注册消息
3. 验证响应状态
4. 发送心跳测试

### 9.2 功能测试

1. 获取内容功能
2. 接收推送内容
3. 状态变更通知
4. 错误处理机制

### 9.3 压力测试

1. 多设备并发连接
2. 高频消息处理
3. 长时间连接稳定性

## 优化亮点

- ✅ **自动设备注册**：新设备无需预先在后台添加，自动创建设备记录
- ✅ **智能设备命名**：自动生成设备名称，也支持自定义名称
- ✅ **状态智能管理**：自动管理设备在线/离线状态
- ✅ **安全机制**：新注册设备默认未激活状态，需管理员手动激活
- ✅ **完整JSON支持**：所有消息支持完整JSON格式解析和Unicode字符显示

## 注意事项

1. 新注册的设备默认状态为**未激活**，需要管理员在后台手动激活后才能正常使用
2. MAC地址会自动转换为小写进行存储和匹配
3. 设备断开连接时会自动更新为离线状态
4. 心跳消息应该定期发送以保持连接活跃状态

## 错误处理和故障排除

### 常见错误消息

#### 服务器相关错误

```json
{
    "type": "register_ack",
    "success": false,
    "msg": "服务器内存表初始化失败"
}
```

#### 设备相关错误

```json
{
    "type": "register_ack",
    "success": false,
    "msg": "缺少mac地址"
}
```

```json
{
    "type": "content_response",
    "success": false,
    "msg": "设备未注册"
}
```

```json
{
    "type": "content_response",
    "success": false,
    "msg": "设备未激活"
}
```

### 故障排除指南

1. **连接问题**

   - 检查WebSocket服务是否正常启动
   - 确认端口9502是否被占用
   - 检查防火墙设置
2. **注册失败**

   - 确认MAC地址格式正确
   - 检查服务器内存表是否初始化成功
   - 查看服务器日志获取详细错误信息
3. **获取内容失败**

   - 确认设备已成功注册
   - 检查设备激活状态
   - 验证设备是否关联了内容或播放列表
4. **心跳失败**

   - 检查网络连接是否稳定
   - 确认设备在内存表中的注册状态
   - 重新执行设备注册流程
5. **设备显示离线但数据库显示在线**

   - 这是由于内存表状态与数据库状态不同步导致
   - 控制页面的在线状态基于WebSocket内存表 + 数据库状态双重判断
   - 设备需要重新连接WebSocket以同步内存表状态
   - 或重启WebSocket服务重新初始化内存表

## 注意事项

1. 新注册的设备默认状态为**未激活**，需要管理员在后台手动激活后才能正常使用
2. MAC地址会自动转换为小写进行存储和匹配
3. 设备断开连接时会自动更新为离线状态
4. 心跳消息应该定期发送以保持连接活跃状态
5. 服务器重启后内存表会重置，设备需要重新注册
6. 播放策略变更后立即生效，下次获取内容时返回新策略的内容

## 广播控制指令

### 支持的指令类型

1. **基础控制指令**

   - `activate`：激活设备
   - `deactivate`：禁用设备
   - `refresh`：刷新设备内容
2. **系统控制指令**

   - `restart`：重启设备
   - `shutdown`：关闭设备

### 系统刷新指令

```json
{
    "type": "refresh",
    "message": "系统刷新"
}
```

### 批量控制指令

```json
{
    "type": "batch_control",
    "action": "restart",
    "message": "系统维护重启",
    "timestamp": 1640995200
}
```

**指令说明：**

- `restart`：设备将执行完全重启操作
- `shutdown`：设备将安全关闭，需要手动重新启动
- `message`：附带的提示消息，设备可显示给用户
- `timestamp`：指令发送时间戳
