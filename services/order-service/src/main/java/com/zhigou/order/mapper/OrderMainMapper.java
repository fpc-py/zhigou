package com.zhigou.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.common.mybatis.AmountTypeHandler;
import com.zhigou.order.entity.OrderMain;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.type.JdbcType;

import java.util.List;

/**
 * B5 金额加密后，BaseMapper 的 wrapper 查询走自动映射（LongTypeHandler 直读密文会炸），
 * 因此所有订单查询统一走显式 ResultMap（金额列挂 AmountTypeHandler 解密）。
 */
@Mapper
public interface OrderMainMapper extends BaseMapper<OrderMain> {

    @Results(id = "orderFullMap", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "order_id", property = "orderId"),
            @Result(column = "user_id", property = "userId"),
            @Result(column = "request_id", property = "requestId"),
            @Result(column = "order_status", property = "orderStatus"),
            @Result(column = "total_amount", property = "totalAmount", typeHandler = AmountTypeHandler.class, jdbcType = JdbcType.VARCHAR),
            @Result(column = "pay_amount", property = "payAmount", typeHandler = AmountTypeHandler.class, jdbcType = JdbcType.VARCHAR),
            @Result(column = "coupon_id", property = "couponId"),
            @Result(column = "receiver_name", property = "receiverName"),
            @Result(column = "receiver_phone", property = "receiverPhone"),
            @Result(column = "receiver_address", property = "receiverAddress"),
            @Result(column = "close_reason", property = "closeReason"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "version", property = "version"),
            @Result(column = "deleted", property = "deleted")
    })
    @Select("SELECT * FROM order_main WHERE request_id = #{requestId} AND deleted = 0 LIMIT 1")
    OrderMain selectFullByRequestId(@Param("requestId") String requestId);

    @ResultMap("orderFullMap")
    @Select("SELECT * FROM order_main WHERE order_id = #{orderId} AND deleted = 0 LIMIT 1")
    OrderMain selectFullByOrderId(@Param("orderId") Long orderId);

    @ResultMap("orderFullMap")
    @Select("SELECT * FROM order_main WHERE user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC")
    List<OrderMain> selectFullByUserId(@Param("userId") Long userId);

    @ResultMap("orderFullMap")
    @Select("SELECT * FROM order_main WHERE order_status = #{status} AND create_time < #{deadline} AND deleted = 0")
    List<OrderMain> selectFullByStatusBefore(@Param("status") String status, @Param("deadline") java.time.LocalDateTime deadline);

    @ResultMap("orderFullMap")
    @Select("SELECT * FROM order_main WHERE order_status = #{status} AND deleted = 0 ORDER BY create_time ASC")
    List<OrderMain> selectFullByStatus(@Param("status") String status);

    @ResultMap("orderFullMap")
    @Select("SELECT * FROM order_main WHERE deleted = 0 ORDER BY create_time DESC")
    List<OrderMain> selectAllFull();
}
