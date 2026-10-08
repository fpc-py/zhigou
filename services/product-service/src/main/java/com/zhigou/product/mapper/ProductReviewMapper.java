package com.zhigou.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.product.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/** 商品评价 Mapper（P1 四批） */
@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /** 星级分布统计（deleted=0 由 MyBatis-Plus 逻辑删除自动附加） */
    @Select("SELECT rating AS rating, COUNT(*) AS cnt FROM product_review " +
            "WHERE spu_id = #{spuId} AND deleted = 0 " +
            "GROUP BY rating ORDER BY rating")
    List<Map<String, Object>> countByRating(@Param("spuId") Long spuId);

    /** 重复内容检测：内容相同且出现 >= 2 次的评价文本（水军特征） */
    @Select("SELECT content, COUNT(*) AS cnt FROM product_review " +
            "WHERE spu_id = #{spuId} AND deleted = 0 " +
            "GROUP BY content HAVING COUNT(*) >= 2 ORDER BY cnt DESC")
    List<Map<String, Object>> findDuplicateContents(@Param("spuId") Long spuId);
}
