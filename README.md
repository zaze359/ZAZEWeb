# 简易Web

## 技术栈

### 前端

### 后端

* Java + Kotlin
* Spring Boot
* Spring Data Jpa
* MySql

### 分包

| 包          | 说明                                       |
|------------|------------------------------------------|
| controller | 定义对外访问的业务接口                              |
| repository | 数据仓库, 负责数据库DAO相关操作                       |
| service    | 业务服务，实现业务服务相关接口                          |
| pojo       | 面向数据库的实例类，不包含业务逻辑。在Service层和DAO层中使用      |
| vo         | View Object, pojo对象处理后得到，一般作为Service层的返回 |
| model      | 提供不同的数据对象间的转换功能，例如 pojo转vo，vo转pojo等      |