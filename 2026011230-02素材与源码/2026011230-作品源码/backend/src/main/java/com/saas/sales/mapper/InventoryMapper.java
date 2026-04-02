package com.saas.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.sales.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
}