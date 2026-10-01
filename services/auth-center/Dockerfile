# ============================================================
# 智购 · auth-center 多阶段 Docker 构建
# ============================================================

# ---- Stage 1: 构建 ----
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

# 先复制父 POM 和公共库，利用 Docker 层缓存
COPY pom.xml /build/pom.xml
COPY packages/common/pom.xml /build/packages/common/pom.xml
COPY packages/common/src /build/packages/common/src
COPY packages/proto/pom.xml /build/packages/proto/pom.xml
COPY services/auth-center/pom.xml /build/services/auth-center/pom.xml
COPY services/auth-center/src /build/services/auth-center/src

# 构建，跳过测试（CI 阶段已跑过）
RUN mvn -pl packages/common,services/auth-center install -DskipTests -am -q

# ---- Stage 2: 运行 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /build/services/auth-center/target/*.jar /app/auth-center.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/auth-center.jar"]