# 分布式发布-订阅系统

## 开发阶段使用说明

在开发阶段，您可以直接通过IDE运行 `broker.java`、`publisher.java` 和 `subscriber.java` 的 main 方法。默认设置如下：

- Broker 端口：8080
- Publisher 和 Subscriber 连接到 localhost:8080

## 生产环境使用说明（待实现）

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

1. 实现broker之间的通信，确保消息能够在整个网络中正确传播。
2. 添加更多的错误处理和日志记录，以便于调试和监控系统。
3. 编写单元测试和集成测试，确保系统按照要求运行。
4. 优化性能，处理并发和多线程问题。
5. 实现通过命令行参数启动程序的功能。

## 注意事项

- 确保所有类都正确处理IOException和其他可能的异常。
- 在实际部署时，需要考虑网络安全性，可能需要添加身份验证和加密机制。
- 考虑使用配置文件来管理broker的端口号和其他设置，而不是硬编码。
