package com.saas.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.sales.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}