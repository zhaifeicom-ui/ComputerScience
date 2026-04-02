package com.saas.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.saas.sales.entity.SalesData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.time.LocalDate;

@Mapper
public interface SalesDataMapper extends BaseMapper<SalesData> {
    
    int insertBatchSomeColumn(@Param("list") List<SalesData> entityList);
    
    @Select("SELECT * FROM sales_data WHERE date BETWEEN #{startDate} AND #{endDate} ORDER BY date")
    List<SalesData> selectSalesDataByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
            
    @Select("SELECT * FROM sales_data WHERE product_id = #{productId} AND date BETWEEN #{startDate} AND #{endDate} ORDER BY date")
    List<SalesData> selectProductSalesByDateRange(
            @Param("productId") Long productId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}