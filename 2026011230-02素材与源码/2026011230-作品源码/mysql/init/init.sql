create table data_import_job
(
    id               bigint auto_increment
        primary key,
    tenant_id        bigint                                                                          not null comment '归属租户ID',
    file_name        varchar(255)                                                                    null comment '文件名',
    status           enum ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') default 'PENDING'         null comment '状态',
    success_count    int                                                   default 0                 null,
    failure_count    int                                                   default 0                 null,
    error_report_url varchar(500)                                                                    null comment '错误报告地址',
    created_at       datetime                                              default CURRENT_TIMESTAMP null,
    finished_at      datetime                                                                        null comment '完成时间'
)
    comment '数据导入任务表';


create table inventory
(
    id               bigint auto_increment
        primary key,
    tenant_id        bigint                             not null comment '归属租户ID',
    product_id       bigint                             not null comment '商品ID',
    stock            int      default 0                 null comment '当前库存',
    safety_stock     int      default 10                null comment '安全库存线',
    latest_update_on datetime                           null comment '最后一次由销售更新的时间',
    created_at       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    updated_at       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_tenant_product
        unique (tenant_id, product_id)
)
    comment '库存表';


create table product
(
    id         bigint auto_increment
        primary key,
    tenant_id  bigint                               not null comment '归属租户ID',
    name       varchar(100)                         not null comment '商品名称',
    category   varchar(50)                          null comment '类目',
    created_at datetime   default CURRENT_TIMESTAMP null,
    deleted    tinyint(1) default 0                 null
)
    comment '商品表';

create index idx_tenant_id
    on product (tenant_id);
create table sales_data
(
    id         bigint auto_increment
        primary key,
    tenant_id  bigint                             not null comment '归属租户ID',
    product_id bigint                             not null comment '商品ID',
    date       date                               not null comment '销售日期',
    sales      int      default 0                 null comment '销量',
    price      decimal(10, 2)                     null comment '单价',
    created_at datetime default CURRENT_TIMESTAMP null
)
    comment '销售数据表';

create index idx_tenant_date
    on sales_data (tenant_id, date);

create table user
(
    id         bigint auto_increment comment '主键ID'
        primary key,
    username   varchar(50)                           not null comment '用户名/租户名',
    password   varchar(255)                          not null comment '加密后的密码',
    role       varchar(20) default 'USER'            null comment '角色: ADMIN, USER',
    created_at datetime    default CURRENT_TIMESTAMP null comment '创建时间',
    constraint username
        unique (username)
)
    comment '租户用户表';

