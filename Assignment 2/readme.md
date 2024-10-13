# 分布式发布-订阅系统

## 版本更新

 -2024.10.10 完成基本功能，目前publisher使用show命令展示读者数量的时候会报超时，应该是broker互相连接的问题，还有delete功能没有实现
 -2024.10.11 将连接使用Integer端口连接，防止多余链接的情况
 -2024.10.12 修复了broker无法正确回传订阅者数量的问题，改进了broker间通信逻辑
 -2024.10.13 进一步优化了broker间通信，增加了错误处理和数据验证
 -2024.10.14 重构了broker间通信逻辑，解决了并发读取和消息同步问题
 -2024.10.15 实现了topic删除的broker间同步，确保所有broker的topic列表一致性
 -2024.10.16 修复了subscriber在topic被删除后仍能看到已订阅的已删除topic的问题
 -2024.10.17 实现了subscriber取消订阅功能，并确保broker间同步这些信息，使publisher能看到准确的订阅者数量
 -2024.10.21 优化了broker类结构,移除了冗余方法,简化了订阅和取消订阅的处理逻辑
 -2024.10.22 修复了安全漏洞,确保只有创建topic的publisher才能删除该topic
 -2024.10.23 修复了安全漏洞,确���有创建topic的publisher才能向该topic发布消息
 -2024.10.24 修复了创建和删除topic时的响应错乱问题，提高了系统的可靠性和用户体验
 -2024.10.25 实现了 publisher 和 subscriber 断开连接时的自动清理功能，包括删除相关 topics 和取消订阅
 -2024.10.26 更新了 publisher 和 subscriber 的启动方式，现在通过 Directory Service 获取 broker 信息
   - 修改了 publisher.java 和 subscriber.java 的 main 方法
   - 添加了从 Directory Service 获取 broker 信息的功能
   - 实现了连接失败时的重试机制
 - 2024.10.27 实现了 broker 心跳机制和 DirectoryService 的超时检测
   - 添加了 broker 向 DirectoryService 发送心跳的功能
   - 在 DirectoryService 中实现了心跳接收和超时处理
   - broker 每 5 秒发送一次心跳
   - DirectoryService 每 15 秒检查一次 broker 的活跃状态
   - 如果 broker 超过 15 秒没有发送心跳，会被从活跃列表中移除

## 开发阶段使用说明

在开发阶段，您可以直接通过IDE运行 `broker.java`、`publisher.java` 和 `subscriber.java` 的 main 方法。默认设置如下：

- Broker 端口：8080, 8081, 8082
- Publisher 和 Subscriber 可以连接到任意一个 broker

## 生产环境使用说明

在项目完成后，您可以按照以下步骤创建和运行 JAR 文件：

1. 编译并创建JAR文件：
   ```
   javac src/*.java
   jar cvfe broker.jar broker -C src .
   jar cvfe publisher.jar publisher -C src .
   jar cvfe subscriber.jar subscriber -C src .
   ```

2. 运行publisher:
   ```
   java -jar publisher.jar <username> <directoryServiceIp:directoryServicePort>
   ```

3. 运行subscriber:
   ```
   java -jar subscriber.jar <username> <directoryServiceIp:directoryServicePort>
   ```

## 待办事项

1. 优化消息广播机制
2. 实现broker之间的负载均衡
3. 添加更多的错误处理和恢复机制
4. 实现broker连接的自动重连和故障转移

## 注意事项

- 确保所有类都正确处理IOException和其他可能的异常。
- 在实际部署时，需要考虑网络安全性，可能需要添加身份验证和加密机制。
- 考虑使用配置文件来管理broker的端口号和其他设置，而不是硬编码。
- 确保所有broker之间的网络连接正常，以保证正确的订阅者数量统计。
- 定期检查和清理 processedMessages 集合，以防止内存泄漏。
- 在进行大规模部署之前，务必进行充分的测试，特别是针对broker间通信的稳定性和性能。
- 确保在取消订阅时正确更新所有相关的broker和publisher。
- broker类现在直接在handleSubscriber方法中处理订阅和取消订阅请求,简化了代码结构。
- 确保只有创建topic的publisher才能删除该topic,以防止未经授权的删除操作。
- 确保只有创建topic的publisher才能向该topic发布消息,以防止未经授权的发布操作。
- 确保broker返回的响应明确且一致，特别是在创建和删除topic时。
- 定期检查和测试系统的各个功能，确保响应的准确性和一致性。
- 当 publisher 断开连接时，其创建的所有 topics 将被自动删除，相关的 subscribers 将收到通知。
- 当 subscriber 断开连接时，其所有的订阅将被自动取消。
- 确保在处理断开连接时，正确同步所有 brokers 的状态。
- broker 现在会定期向 DirectoryService 发送心跳，确保其在活跃列表中保持更新状态。
- DirectoryService 会定期检查 broker 的活跃状态，并移除超时的 broker。
- 确保网络环境稳定，以防止由于网络问题导致的误判。
