# /new-service
初始化一个新的 Spring Boot 微服务骨架。
用法: /new-service <service-name>

执行步骤:
1. 在 services/<name> 建目录,包结构: controller/service/mapper/entity/dto/config
2. 生成 pom.xml (继承父POM, web/mybatis-plus/mysql/redis/validation/actuator)
3. 全局异常处理器: BizException -> Result.fail
4. 配置文件: application-dev.yml / application-prod.yml
5. 生成 Dockerfile (多阶段, eclipse-temurin:21-jre)
6. 健康检查 /actuator/health 返回 UP
7. 在父 pom.xml 注册子模块

约束: 包名 com.zhigou.<name>, 端口按编号递增, 不写业务接口只建骨架