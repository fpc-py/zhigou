# /api-endpoint

给指定服务新增一个 API 接口。

## 用法

```
/api-endpoint <service> <method> <path>
```

## 执行步骤

1. 读 CLAUDE.md 确认技术栈和规范
2. 读目标 service 现有代码结构
3. 创建 DTO（入参/出参），加 @Valid 校验
4. 在 Controller 加接口，加 Knife4j 注解
5. 在 Service 实现业务逻辑，处理异常
6. 写单测（正常 + 异常至少各 1 个）
7. 跑 `./mvnw -pl services/<svc> test` 确认绿

## 默认行为

- 自动加 JWT 鉴权（从 SecurityContext 拿 userId）
- 返回统一 Result<T>
- 入参加 @Valid，金额用 Long 存分