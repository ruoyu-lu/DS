# 分布式发布-订阅系统

## 版本更新

 -2024.10.10 完成基本功能，目前publisher使用show命令展示读者数量的时候会报超时，应该是broker互相连接的问题，还有delete功能没有实现
 -2024.10.11 将连接使用Integer端口连接，防止多余链接的情况
 -2024.10.12 修复了broker无法正确回传订阅者数量的问题，改进了broker间通信逻辑
 -2024.10.13 进一步优化了broker间通信，增加了错误处理和数据验证
 -2024.10.14 重构了broker间通信逻辑，解决了并发读取和消息同步问题

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
   java -jar broker.jar <port> [-b <broker_ip_1:port1> <broker_ip_2:port2> ...]
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