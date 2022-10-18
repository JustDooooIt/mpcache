# mpcache

#### 介绍
mybatisplus缓存，对返回对象进行增强，调用set方法自动同步到数据库，做到面向集合编程

#### 说明
目前还处于开发阶段，求求来个人测试和提需求吧

#### 前置条件
1，表必须有主键，否则无法正常使用

2，目前只支持单表查询

#### 示例
```yaml
mybatis-plus:
  mapper-class-package: com.example.mybatisdemo.mapper
  cache-size: 16
```

```java
@Resource
private Cache cache;

ModelDO modelDO = new ModelDO();
modelDO.setName("model");

//add 插入数据，如果数据已存在则返回false
cache.add(modelDO);

//get 从缓存查询数据
//目前缓存条件构造器支持eq,ne,gt,ge,lt,le,between,notBetween
modelDO = cache.get(ModelDO.class, modelDO.getId());
List<ModelDO> list = cache.find(ModelDO.class,
        new CacheLambdaQueryWrapper<ModelDO>()
            .eq(ModelDO::getName, "model")
            .select(ModelDO::getName));

//set 调用set方法会自动将数据同步到数据库
modelDO.setName("yeah");
```