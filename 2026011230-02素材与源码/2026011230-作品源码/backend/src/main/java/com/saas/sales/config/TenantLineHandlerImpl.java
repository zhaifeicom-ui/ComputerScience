package com.saas.sales.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class TenantLineHandlerImpl implements TenantLineHandler {

    private static final Set<String> IGNORE_TABLES = new HashSet<>();

    static {
        IGNORE_TABLES.add("user");
        // 其他不需要租户隔离的表可以在这里添加
    }

    @Override
    public Expression getTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            // 如果未设置租户ID，返回null可能导致查询不到数据，根据业务决定
            return new NullValue();
        }
        return new LongValue(tenantId);
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);
    }

    // @Override
    // public boolean ignoreInsert(String tableName) {
    //     return IGNORE_TABLES.contains(tableName);
    // }
}