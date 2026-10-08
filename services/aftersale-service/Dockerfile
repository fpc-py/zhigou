# syntax=docker/dockerfile:1
# ============================================================
# 智购 · aftersale-service 生产级多阶段构建
# 构建：maven:3.9-eclipse-temurin-17（与 maven-compiler target=17 一致）
# 运行：eclipse-temurin:17 JRE，非 root 用户，健康检查
# 构建命令（项目根执行）：
#   docker build -f services/aftersale-service/Dockerfile -t zhigou/aftersale-service:0.1.0 .
# ============================================================

# ---- Stage 1: 构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 1) 先拷全部 POM（根 reactor 解析必需）最大化层缓存
COPY pom.xml /build/pom.xml
COPY services/auth-center/pom.xml /build/services/auth-center/pom.xml
COPY services/user-service/pom.xml /build/services/user-service/pom.xml
COPY services/file-service/pom.xml /build/services/file-service/pom.xml
COPY services/product-service/pom.xml /build/services/product-service/pom.xml
COPY services/cart-service/pom.xml /build/services/cart-service/pom.xml
COPY services/order-service/pom.xml /build/services/order-service/pom.xml
COPY services/inventory-service/pom.xml /build/services/inventory-service/pom.xml
COPY services/payment-service/pom.xml /build/services/payment-service/pom.xml
COPY services/marketing-service/pom.xml /build/services/marketing-service/pom.xml
COPY services/logistics-service/pom.xml /build/services/logistics-service/pom.xml
COPY services/aftersale-service/pom.xml /build/services/aftersale-service/pom.xml
COPY packages/common/pom.xml /build/packages/common/pom.xml
COPY packages/proto/pom.xml /build/packages/proto/pom.xml

# 2) 拷源码并打包（-am 连带构建依赖的 proto/common）
COPY packages/proto /build/packages/proto
COPY packages/common /build/packages/common
COPY services/aftersale-service /build/services/aftersale-service
RUN mvn -pl services/aftersale-service -am package -DskipTests -q

# ---- Stage 2: 运行 ----
FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=256m -Duser.timezone=Asia/Shanghai"
WORKDIR /app

# 非 root 运行（安全基线：容器内以 app 用户执行）
RUN groupadd -r app && useradd -r -g app -u 1001 app && \
    mkdir -p /app/logs && chown -R app:app /app

COPY --from=builder /build/services/aftersale-service/target/zhigou-aftersale-service-0.1.0-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 8090

# 健康检查（服务已引入 spring-boot-starter-actuator）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]