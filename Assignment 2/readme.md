# 分布式发布-订阅系统

## 版本更新

- 2024-10-01 v0.2.0: 实现了broker之间的通信功能
- 2024-10-01 v0.2.1: 改进了subscriber的消息接收逻辑
- 2024-10-01 v0.2.2: 优化了subscriber的用户界面和消息处理
- 2024-10-01 v0.2.3: 改进了订阅者列出主题的功能，现在显示更详细的信息
- 2024-10-02 v0.2.4: 更新了订阅者的当前订阅显示，现在包括主题ID、主题名称和发布者名称
- 2024-10-03 v0.3.0: 实现了发布者发布消息功能，订阅者可以实时接收消息
- 2024-10-04 v0.3.1: 修复了broker的消息传递、主题列表显示和订阅确认机制的问题
- 2024-10-05 v0.3.2: 改进了发布者的 "Show Subscriber Count" 功能，现在显示所有创建的主题及其订阅者数量

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

2. 运行broker:
   ```
   java -jar broker.jar <port> [-b <broker_ip_1:port1> <broker_ip_2:port2>]
   ```

3. 运行publisher:
   ```
   java -jar publisher.jar <username> <broker_ip> <broker_port>
   ```

4. 运行subscriber:
   ```
   java -jar subscriber.jar <username> <broker_ip> <broker_port>
   ```

## 待办事项

1. ~~实现broker之间的通信，确保消息能够在整个网络中正确传播。~~
2. ~~改进subscriber的消息接收逻辑，确保能够正确接收和显示来自broker的消息。~~
3. ~~优化subscriber的用户界面和消息处理逻辑。~~
4. ~~实现publisher发布消息功能，确保消息能够正确发送给所有订阅者。~~
5. ~~修复broker的消息传递、主题列表显示和订阅确认机制的问题。~~
6. 添加更多的错误��理和日志记录，以便于调试和监控系统。
7. 编写单元测试和集成测试，确保系统按照要求运行。
8. 优化性能，处理并发和多线程问题。
9. 实现通过命令行参数启动程序的功能。
10. ~~更新了 topic ID 生成机制，现在使用 "brokerID-sequentialNumber" 格式而不是 UUID。~~
11. ~~改进了订阅者列出主题的功能，现在显示主题ID、主题名称和发布者信息。~~
12. ~~更新了订阅者的当前订阅显示，现在包括主题ID、主题名称和发布者名称。~~
13. 改进了发布者的订阅者计数功能，现在显示该发布者创建的所有主题及其订阅者数量。

## 注意事项

- 确保所有类都正确处理IOException和其他可能的异常。
- 在实际部署时，需要考虑网络安全性，可能需要添加身份验证和加密机制。
- 考虑使用配置文件来管理broker的端口号和其他设置，而不是硬编码。
- 当前实现假设有3个broker节点，如果需要更改节点数量，请相应修改BROKER_COUNT常量。
