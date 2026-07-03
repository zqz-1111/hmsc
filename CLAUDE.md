# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指引。

## 项目简介

黑马商城（HMall）—— 基于 Spring Boot 的单体电商应用。Maven 多模块结构：
- `hm-common`：公共模块，通过 `spring.factories` 自动装配，提供全局异常处理、工具类、通用 DTO
- `hm-service`：主应用模块，包含全部业务逻辑

## 构建与运行

```bash
# 打包（在项目根目录执行）
mvn clean package

# 运行
java -jar hm-service/target/hm-service.jar

# 运行全部测试
mvn test

# 运行单个测试类
mvn test -pl hm-service -Dtest=ItemServiceImplTest

# Docker 构建
docker build -t hm-service ./hm-service
```

应用端口：**8080**。默认 Spring Profile 为 `dev`，切换用 `--spring.profiles.active=local`。

## 架构说明

**分层单体架构**，每个业务实体遵循：Controller → Service（接口 + ServiceImpl）→ Mapper（MyBatis-Plus BaseMapper）→ MySQL。

**领域对象命名规范**，按职责拆分到不同包：
- `po/` —— 持久化对象（数据库实体）
- `dto/` —— 数据传输对象（请求/响应参数）
- `vo/` —— 视图对象（返回给前端的数据）
- `query/` —— 查询条件对象

**公共模块**（`hm-common`）自动装配的 Bean：全局异常处理（`CommonExceptionAdvice`）、MyBatis 配置、JSON 配置、工具类（`UserContext`、`BeanUtils`、`PageDTO`、`R`）。

## 认证机制

基于 JWT RS256（Hutool 库实现）。`LoginInterceptor` 校验 `Authorization` 头，将 userId 存入 `UserContext`（ThreadLocal）。认证路径通过 `hm.auth.includePaths` / `hm.auth.excludePaths` 配置。公开接口：`/search/**`、`/users/login`、`/items/**`、`/hi`。

## 关键约定

- **价格单位为分（fen）**，不是元
- MyBatis-Plus 的 Service 层继承 `IService<T>` / `ServiceImpl<M, T>`
- 所有 Controller 使用 Swagger 注解（`@Api`、`@ApiOperation`），API 文档地址：`/doc.html`（Knife4j）
- 自定义配置前缀：`hm.*`（包括 `hm.db.*`、`hm.jwt.*`、`hm.auth.*`、`hm.mq.*`）
- 异常体系（在 `hm-common` 中）：`CommonException` → `BadRequestException`、`BizIllegalException`、`DbException`、`ForbiddenException`、`UnauthorizedException`
- 全项目使用 Lombok 减少样板代码

## 技术栈

- Java 11、Spring Boot 2.7.12、Spring Cloud 2021.0.3、Spring Cloud Alibaba 2021.0.4.0
- MyBatis-Plus 3.4.3、MySQL 8、Redis、RabbitMQ
- Knife4j 4.1.0、Hutool 5.8.11、Lombok
