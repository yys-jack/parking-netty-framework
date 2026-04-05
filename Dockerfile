# 阶段 1: 构建自定义 JRE
FROM debian:bookworm-slim AS build

ENV JAVA_HOME=/opt/jdk
ENV PATH=$JAVA_HOME/bin:$PATH

# 安装 jlink 所需的工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    binutils \
    && rm -rf /var/lib/apt/lists/*

# 复制完整 JDK（用于 jlink 裁剪）
COPY dragonwell-21/ /opt/jdk/

# 复制应用 JAR
COPY build/libs/parking-netty-framework-1.0.0.jar /app/app.jar

# 使用 jlink 创建最小 JRE（添加 Spring Boot + Tomcat 需要的模块）
RUN /opt/jdk/bin/jlink \
    --add-modules java.base,java.logging,java.net.http,java.management,java.sql,java.naming,java.desktop,java.datatransfer,java.security.jgss,java.instrument \
    --output /opt/jre-custom \
    --no-header-files \
    --no-man-pages \
    --strip-debug

# 阶段 2: 生产运行镜像
FROM debian:bookworm-slim

# 设置时区为上海
ENV TZ=Asia/Shanghai

# 安装时区数据和健康检查工具
RUN apt-get update && apt-get install -y --no-install-recommends \
    tzdata wget \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

# 创建非 root 用户
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# 从构建阶段复制自定义 JRE
COPY --from=build /opt/jre-custom /opt/jre

# 复制应用 JAR
COPY --from=build /app/app.jar /app/app.jar

# 设置环境变量
ENV JAVA_HOME=/opt/jre
ENV PATH=$JAVA_HOME/bin:$PATH
ENV SERVER_PORT=8080
ENV NETTY_TCP_PORT=8889

# JVM 容器感知参数
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:InitialRAMPercentage=50.0 \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/logs/heap_dump.hprof"

# 设置工作目录和权限
WORKDIR /app
RUN chown -R appuser:appgroup /app

# 切换到非 root 用户
USER appuser

# 健康检查 (检查 HTTP 响应，200 或 503 都表示应用正常运行)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --server-response --timeout=5 http://localhost:${MANAGEMENT_SERVER_PORT:-8081}/actuator/health 2>&1 | grep -q "HTTP" || exit 1

# 暴露端口
EXPOSE ${SERVER_PORT} ${NETTY_TCP_PORT}

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
