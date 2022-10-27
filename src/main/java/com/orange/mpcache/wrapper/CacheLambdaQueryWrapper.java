package com.orange.mpcache.wrapper;

import com.baomidou.mybatisplus.core.conditions.AbstractLambdaWrapper;
import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.query.Query;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.ArrayUtils;
import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.compare.ComparableUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CacheLambdaQueryWrapper<T> extends AbstractLambdaWrapper<T, CacheLambdaQueryWrapper<T>>
        implements Query<CacheLambdaQueryWrapper<T>, T, SFunction<T, ?>> {

    private Predicate<T> predicate;

    @Getter
    private Boolean isOr = false;

    private List<String> fieldList = new LinkedList<>();

    /**
     * 查询字段
     */
    private SharedString sqlSelect = new SharedString();

    public CacheLambdaQueryWrapper() {
        this((T) null);
    }

    public CacheLambdaQueryWrapper(T entity) {
        super.setEntity(entity);
        super.initNeed();
    }

    public CacheLambdaQueryWrapper(Class<T> entityClass) {
        super.setEntityClass(entityClass);
        super.initNeed();
    }

    CacheLambdaQueryWrapper(T entity, Class<T> entityClass, SharedString sqlSelect, AtomicInteger paramNameSeq,
                            Map<String, Object> paramNameValuePairs, MergeSegments mergeSegments, SharedString paramAlias,
                            SharedString lastSql, SharedString sqlComment, SharedString sqlFirst) {
        super.setEntity(entity);
        super.setEntityClass(entityClass);
        this.paramNameSeq = paramNameSeq;
        this.paramNameValuePairs = paramNameValuePairs;
        this.expression = mergeSegments;
        this.sqlSelect = sqlSelect;
        this.paramAlias = paramAlias;
        this.lastSql = lastSql;
        this.sqlComment = sqlComment;
        this.sqlFirst = sqlFirst;
    }

    /**
     * SELECT 部分 SQL 设置
     *
     * @param columns 查询字段
     */
    @SafeVarargs
    @Override
    public final CacheLambdaQueryWrapper<T> select(SFunction<T, ?>... columns) {
        if (ArrayUtils.isNotEmpty(columns)) {
            for (SFunction<T, ?> column : columns) {
                LambdaMeta meta = LambdaUtils.extract(column);
                String fieldName = PropertyNamer.methodToProperty(meta.getImplMethodName());
                fieldList.add(fieldName);
            }
            this.sqlSelect.setStringValue(columnsToString(false, columns));
        }
        return typedThis;
    }

    /**
     * 过滤查询的字段信息(主键除外!)
     * <p>例1: 只要 java 字段名以 "test" 开头的             -> select(i -&gt; i.getProperty().startsWith("test"))</p>
     * <p>例2: 只要 java 字段属性是 CharSequence 类型的     -> select(TableFieldInfo::isCharSequence)</p>
     * <p>例3: 只要 java 字段没有填充策略的                 -> select(i -&gt; i.getFieldFill() == FieldFill.DEFAULT)</p>
     * <p>例4: 要全部字段                                   -> select(i -&gt; true)</p>
     * <p>例5: 只要主键字段                                 -> select(i -&gt; false)</p>
     *
     * @param predicate 过滤方式
     * @return this
     */
    @Override
    public CacheLambdaQueryWrapper<T> select(Class<T> entityClass, Predicate<TableFieldInfo> predicate) {
        if (entityClass == null) {
            entityClass = getEntityClass();
        } else {
            setEntityClass(entityClass);
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        List<TableFieldInfo> tableFieldInfos = tableInfo.getFieldList().stream()
                .filter(predicate)
                .collect(Collectors.toList());
        fieldList = tableFieldInfos.stream().map(TableFieldInfo::getProperty).collect(Collectors.toList());
        Assert.notNull(entityClass, "entityClass can not be null");
        this.sqlSelect.setStringValue(tableInfo.chooseSelect(predicate));
        return typedThis;
    }

    @Override
    public String getSqlSelect() {
        return sqlSelect.getStringValue();
    }

    public String getFieldSelect() {
        return StringUtils.join(fieldList, ",");
    }

    public String getSqlSelectOrDefault() {
        return sqlSelect.getStringValue() == null ? "" : sqlSelect.getStringValue();
    }

    /**
     * 用于生成嵌套 sql
     * <p>故 sqlSelect 不向下传递</p>
     */
    @Override
    protected CacheLambdaQueryWrapper<T> instance() {
        return new CacheLambdaQueryWrapper<>(getEntity(), getEntityClass(), null, paramNameSeq, paramNameValuePairs,
                new MergeSegments(), paramAlias, SharedString.emptyString(), SharedString.emptyString(), SharedString.emptyString());
    }

    @Override
    public void clear() {
        super.clear();
        sqlSelect.toNull();
    }

    @Override
    public CacheLambdaQueryWrapper<T> eq(boolean condition, SFunction<T, ?> column, Object val) {
        Predicate<T> p = t -> Objects.equals(column.apply(t), val);
        if (condition) {
            if (predicate == null) {
                predicate = p;
            } else if(isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.eq(condition, column, val);
    }

    @Override
    public CacheLambdaQueryWrapper<T> ne(boolean condition, SFunction<T, ?> column, Object val) {
        if (condition) {
            Predicate<T> p = t -> !Objects.equals(column.apply(t), val);
            if (predicate == null) {
                predicate = p;
            } else if(isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.ne(condition, column, val);
    }

    @Override
    public CacheLambdaQueryWrapper<T> gt(boolean condition, SFunction<T, ?> column, Object val) {
        if (condition) {
            Predicate<T> p = t -> ComparableUtils.<Comparable>gt((Comparable<Object>) val).test((Comparable<Object>) column.apply(t));
            if (predicate == null) {
                predicate = p;
            }
            else if (isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.gt(condition, column, val);
    }

    @Override
    public CacheLambdaQueryWrapper<T> lt(boolean condition, SFunction<T, ?> column, Object val) {
        if (condition) {
            Predicate<T> p = t -> ComparableUtils.<Comparable>lt((Comparable<Object>) val).test((Comparable<Object>) column.apply(t));
            if (predicate == null) {
                predicate = p;
            }
            else if (isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.lt(condition, column, val);
    }

    @Override
    public CacheLambdaQueryWrapper<T> ge(boolean condition, SFunction<T, ?> column, Object val) {
        if (condition) {
            Predicate<T> p = t -> ComparableUtils.<Comparable>ge((Comparable<Object>) val).test((Comparable<Object>) column.apply(t));
            if (predicate == null) {
                predicate = p;
            }
            else if (isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.ge(condition, column, val);
    }

    @Override
    public CacheLambdaQueryWrapper<T> le(boolean condition, SFunction<T, ?> column, Object val) {
        if (condition) {
            Predicate<T> p = t -> ComparableUtils.<Comparable>le((Comparable<Object>) val).test((Comparable<Object>) column.apply(t));
            if (predicate == null) {
                predicate = p;
            }
            else if (isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.le(condition, column, val);
    }

    @Override
    public CacheLambdaQueryWrapper<T> between(boolean condition, SFunction<T, ?> column, Object val1, Object val2) {
        if (condition) {
            Predicate<T> p = t -> ComparableUtils.<Comparable>between((Comparable<Object>) val1, (Comparable<Object>) val2).test((Comparable<Object>) column.apply(t));
            if (predicate == null) {
                predicate = p;
            }
            else if (isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.between(condition, column, val1, val2);
    }

    @Override
    public CacheLambdaQueryWrapper<T> notBetween(boolean condition, SFunction<T, ?> column, Object val1, Object val2) {
        if (condition) {
            Predicate<T> p = t -> !ComparableUtils.<Comparable>between((Comparable<Object>) val1, (Comparable<Object>) val2).test((Comparable<Object>) column.apply(t));
            if (predicate == null) {
                predicate = p;
            }
            else if (isOr) {
                isOr = false;
                predicate = predicate.or(p);
            } else {
                predicate = predicate.and(p);
            }
        }
        return super.notBetween(condition, column, val1, val2);
    }

    @Override
    public CacheLambdaQueryWrapper<T> or() {
        isOr = true;
        return super.or();
    }


    public List<String> getFieldList() {
        return fieldList;
    }

    public CacheLambdaQueryWrapper<T> selectAll() {
        this.sqlSelect = new SharedString();
        return typedThis;
    }

    public Predicate<T> getPredicate() {
        return predicate == null ? t -> true : predicate;
    }

}
