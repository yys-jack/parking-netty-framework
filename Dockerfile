# 多阶段构建：阶段 1 - 复制 JDK
FROM debian:bookworm-slim AS jdk

ENV JAVA_HOME=/opt/jdk
ENV PATH=$JAVA_HOME/bin:$PATH

COPY dragonwell-21/ /opt/jdk/

RUN java -version

# 阶段 2：构建应用镜像
FROM debian:bookworm-slim

# 从 JDK 阶段复制 JDK
COPY --from=jdk /opt/jdk /opt/jdk

ENV JAVA_HOME=/opt/jdk
ENV PATH=$JAVA_HOME/bin:$PATH

WORKDIR /app

COPY build/libs/parking-netty-framework-1.0.0.jar app.jar

ENV SERVER_PORT=8080
ENV NETTY_TCP_PORT=8889

ENTRYPOINT ["java", "-jar", "app.jar"]
