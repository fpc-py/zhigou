package com.zhigou.product.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.common.BizException;
import com.zhigou.product.dto.ReviewStatsVO;
import com.zhigou.product.dto.ReviewSubmitRequest;
import com.zhigou.product.dto.ReviewVO;
import com.zhigou.product.entity.ProductReview;
import com.zhigou.product.mapper.ProductReviewMapper;
import com.zhigou.product.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品评价服务（P1 四批）。
 * 统计接口同时输出星级分布与重复内容数，供 AI review_analysis 差评/水军识别。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;

    private final ProductReviewMapper reviewMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, ReviewSubmitRequest request) {
        if (userId == null || userId <= 0) {
            throw new BizException(401, "未登录用户不能发表评价");
        }
        ProductReview review = new ProductReview();
        review.setReviewId(IdUtil.getSnowflakeNextId());
        review.setSpuId(request.getSpuId());
        review.setUserId(userId);
        // 昵称脱敏：用户 + 尾 4 位，避免明文暴露
        review.setUserName("用户" + String.valueOf(userId).replaceAll("\\d{4}$", "****"));
        review.setRating(request.getRating());
        review.setContent(request.getContent().trim());
        review.setImages(request.getImages() == null || request.getImages().isBlank() ? null : request.getImages());
        review.setIsMock(0); // 用户提交为真实评价
        reviewMapper.insert(review);
        return review.getReviewId();
    }

    @Override
    public Page<ReviewVO> page(Long spuId, Integer minRating, Integer maxRating, int pageNum, int pageSize) {
        LambdaQueryWrapper<ProductReview> qw = new LambdaQueryWrapper<>();
        qw.eq(ProductReview::getSpuId, spuId)
                .ge(minRating != null, ProductReview::getRating, minRating)
                .le(maxRating != null, ProductReview::getRating, maxRating)
                .orderByDesc(ProductReview::getCreateTime);
        Page<ProductReview> p = reviewMapper.selectPage(Page.of(pageNum, pageSize), qw);
        Page<ReviewVO> vo = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        vo.setRecords(p.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public ReviewStatsVO stats(Long spuId) {
        long total = reviewMapper.selectCount(
                new LambdaQueryWrapper<ProductReview>().eq(ProductReview::getSpuId, spuId));

        Map<Integer, Long> dist = new HashMap<>();
        for (Map<String, Object> row : reviewMapper.countByRating(spuId)) {
            dist.put(((Number) row.get("rating")).intValue(), ((Number) row.get("cnt")).longValue());
        }
        // 补齐缺失星级
        for (int r = MIN_RATING; r <= MAX_RATING; r++) {
            dist.putIfAbsent(r, 0L);
        }
        long weighted = dist.entrySet().stream()
                .mapToLong(e -> (long) e.getKey() * e.getValue()).sum();
        double avg = total == 0 ? 0.0 : Math.round(weighted * 10.0 / total) / 10.0;

        long imageCount = reviewMapper.selectCount(
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getSpuId, spuId)
                        .isNotNull(ProductReview::getImages));
        long lowStarCount = dist.entrySet().stream()
                .filter(e -> e.getKey() <= 3).mapToLong(Map.Entry::getValue).sum();
        long duplicateCount = reviewMapper.findDuplicateContents(spuId).stream()
                .mapToLong(m -> ((Number) m.get("cnt")).longValue()).sum();

        return ReviewStatsVO.builder()
                .total(total)
                .avgRating(avg)
                .ratingDist(dist)
                .imageCount(imageCount)
                .lowStarCount(lowStarCount)
                .duplicateCount(duplicateCount)
                .build();
    }

    private ReviewVO toVO(ProductReview r) {
        return ReviewVO.builder()
                .reviewId(r.getReviewId())
                .spuId(r.getSpuId())
                .userName(r.getUserName())
                .rating(r.getRating())
                .content(r.getContent())
                .images(r.getImages())
                .isMock(r.getIsMock())
                .createTime(r.getCreateTime())
                .build();
    }
}
