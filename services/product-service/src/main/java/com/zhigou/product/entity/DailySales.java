package com.zhigou.product.entity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
@Data @TableName("daily_sales")
public class DailySales {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    private String skuId; private String productName;
    private LocalDate salesDate; private Integer salesQty;
    @Version private Integer version; @TableLogic private Integer deleted;
}
