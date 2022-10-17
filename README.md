# mpcache

#### 介绍
mybatisplus缓存，对返回对象进行增强，调用set方法自动同步到数据库，做到面向集合编程

#### 说明
目前还处于开发阶段，求求来个人测试吧

#### 示例
```java
@Resource
private Cache cache;

ModelDO modelDO = new ModelDO();
modelDO.setName("model");

//add
cache.add(modelDO);

//get
modelDO = cache.get(ModelDO.class, modelDO.getId());
List<ModelDO> list = cache.find(Cache.find(ModelDO.class,
        new CacheLambdaQueryWrapper<ModelDO>()
            .eq(ModelDO::getName, "model")
            .select(ModelDO::getName));

//set
modelDO.setName("yeah");
```