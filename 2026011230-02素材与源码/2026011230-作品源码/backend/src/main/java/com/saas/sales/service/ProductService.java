package com.saas.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.saas.sales.config.TenantContextHolder;
import com.saas.sales.dto.ProductCreateRequest;
import com.saas.sales.dto.ProductResponse;
import com.saas.sales.dto.ProductUpdateRequest;
import com.saas.sales.entity.Product;
import com.saas.sales.mapper.ProductMapper;
import com.saas.sales.mapper.SalesDataMapper;
import com.saas.sales.entity.SalesData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@CacheConfig(cacheNames = "products")
public class ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SalesDataMapper salesDataMapper;
	@Cacheable(value = "products-page", key = "T(com.saas.sales.config.TenantContextHolder).getTenantId() + ':' + #page + '-' + #size")
    public Page<ProductResponse> getProducts(int page, int size) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Page<Product> productPage = new Page<>(page, size);
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getDeleted, 0)
                   .orderByDesc(Product::getCreatedAt);

        Page<Product> result = productMapper.selectPage(productPage, queryWrapper);

        // 转换为响应DTO
        Page<ProductResponse> responsePage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<ProductResponse> productResponses = result.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(productResponses);

        return responsePage;
    }

    public List<ProductResponse> getAllProducts() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getDeleted, 0)
                   .orderByDesc(Product::getCreatedAt);

        List<Product> products = productMapper.selectList(queryWrapper);

        return products.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(key = "T(com.saas.sales.config.TenantContextHolder).getTenantId() + ':' + #id")
    public ProductResponse getProductById(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Product product = productMapper.selectById(id);
        if (product == null || product.getDeleted() == 1) {
            throw new RuntimeException("产品不存在");
        }

        return convertToResponse(product);
    }

    @Transactional
    @CacheEvict(value = "products-page", allEntries = true)
    public ProductResponse createProduct(ProductCreateRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        // 检查商品名称是否已存在
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getName, request.getName())
                   .eq(Product::getDeleted, 0);
        Product existingProduct = productMapper.selectOne(queryWrapper);
        if (existingProduct != null) {
            throw new RuntimeException("商品名称已存在");
        }

        // 创建商品
        Product product = new Product();
        product.setTenantId(tenantId);
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setCreatedAt(LocalDateTime.now());
        
        productMapper.insert(product);
        
        return convertToResponse(product);
    }

    @Transactional

    @CacheEvict(value = "products-page", allEntries = true)
    public ProductResponse updateProduct(Long id, ProductUpdateRequest request) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Product product = productMapper.selectById(id);
        if (product == null || product.getDeleted() == 1) {
            throw new RuntimeException("产品不存在");
        }

        // 检查商品名称是否已存在（排除当前商品）
        if (request.getName() != null && !request.getName().equals(product.getName())) {
            LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Product::getName, request.getName())
                       .eq(Product::getDeleted, 0)
                       .ne(Product::getId, id);
            Product existingProduct = productMapper.selectOne(queryWrapper);
            if (existingProduct != null) {
                throw new RuntimeException("商品名称已存在");
            }
        }

        // 更新商品信息
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }

        productMapper.updateById(product);

        return convertToResponse(product);
    }

    @Transactional
    @CacheEvict(value = "products-page", allEntries = true)
    public void deleteProduct(Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户ID");
        }

        Product product = productMapper.selectById(id);
        if (product == null || product.getDeleted() == 1) {
            throw new RuntimeException("产品不存在");
        }

        // 检查商品是否有关联的销售数据
        LambdaQueryWrapper<SalesData> salesQuery = new LambdaQueryWrapper<>();
        salesQuery.eq(SalesData::getProductId, id);
        Long salesCount = salesDataMapper.selectCount(salesQuery);
        if (salesCount > 0) {
            throw new RuntimeException("该商品已有销售数据，无法删除");
        }

        // 物理删除
        productMapper.deleteById(id);

        log.info("删除商品: {}", id);
    }

    private ProductResponse convertToResponse(Product product) {
        ProductResponse response = new ProductResponse();
        BeanUtils.copyProperties(product, response);
        return response;
    }
}