package com.zhigou.marketing.engine;

import com.ql.util.express.DefaultContext;
import com.ql.util.express.ExpressRunner;
import com.zhigou.marketing.entity.PromotionRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j @Component
public class RuleEngine {
    private final ExpressRunner runner = new ExpressRunner();
    private final Map<Long, String> ruleScripts = new LinkedHashMap<>();

    public void loadRules(List<PromotionRule> ruleList) {
        ruleScripts.clear();
        for (PromotionRule r : ruleList) {
            if (r.getStatus() == 1) {
                ruleScripts.put(r.getId(), r.getRuleContent());
            }
        }
        log.info("加载 {} 条促销规则", ruleScripts.size());
    }

    public Map<Long, Long> execute(Long totalAmount, List<?> items) {
        Map<Long, Long> results = new LinkedHashMap<>();
        DefaultContext<String, Object> ctx = new DefaultContext<>();
        ctx.put("totalAmount", totalAmount);
        ctx.put("items", items);

        for (Map.Entry<Long, String> entry : ruleScripts.entrySet()) {
            try {
                Object result = runner.execute(entry.getValue(), ctx, null, true, false);
                if (result instanceof Number) {
                    long discount = ((Number) result).longValue();
                    if (discount > 0) results.put(entry.getKey(), discount);
                }
            } catch (Exception e) {
                log.error("规则执行失败: ruleId={}", entry.getKey(), e);
            }
        }
        return results;
    }
}