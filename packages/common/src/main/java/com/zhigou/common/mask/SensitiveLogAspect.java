package com.zhigou.common.mask;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 日志脱敏切面：拦截标注 {@link SensitiveLog} 的方法，
 * 在方法前后打印【已脱敏】的入参与返回值（含耗时）。
 *
 * <p>规则：
 * <ul>
 *   <li>入参/返回值通过 Jackson 序列化后递归遍历，字段名命中敏感词（phone/password/...）
 *       或值命中手机号/身份证/邮箱正则 → 掩码后再打印；</li>
 *   <li>仅影响日志，不改动业务数据；</li>
 *   <li>JSON 序列化失败时退化为对象 toString + 智能正则掩码，绝不抛异常影响主流程。</li>
 * </ul>
 */
@Aspect
public class SensitiveLogAspect {

    private static final Logger log = LoggerFactory.getLogger(SensitiveLogAspect.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(com.zhigou.common.mask.SensitiveLog) || @within(com.zhigou.common.mask.SensitiveLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        long start = System.currentTimeMillis();
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            if (log.isWarnEnabled()) {
                log.warn("[SensitiveLog] {} 执行异常: {} | 入参={}",
                        sig.toShortString(), t.getMessage(), maskedArgs(pjp.getArgs()));
            }
            throw t;
        }
        long cost = System.currentTimeMillis() - start;
        if (log.isInfoEnabled()) {
            log.info("[SensitiveLog] {} 完成 | 耗时 {}ms | 入参={} | 返回={}",
                    sig.toShortString(), cost, maskedArgs(pjp.getArgs()), maskedResult(result));
        }
        return result;
    }

    private String maskedArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(maskObject(args[i]));
        }
        return sb.append(']').toString();
    }

    private String maskedResult(Object result) {
        return maskObject(result);
    }

    private String maskObject(Object obj) {
        if (obj == null) {
            return "null";
        }
        try {
            // 1) 反射：字段标注 @SensitiveField 的按注解类型精确掩码（如验证码 code）
            Map<String, Object> annotated = collectAnnotatedFields(obj);
            if (annotated != null) {
                maskMap(annotated);
                return objectMapper.writeValueAsString(annotated);
            }
            // 2) 默认：序列化后按字段名敏感词 + 值正则智能掩码
            String json = objectMapper.writeValueAsString(obj);
            Object tree = objectMapper.readValue(json, Object.class);
            if (tree instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) tree;
                maskMap(map);
                return objectMapper.writeValueAsString(map);
            }
            // 数组/标量：按正则智能掩码
            return MaskUtil.mask(json);
        } catch (Exception e) {
            // 序列化失败：toString + 正则智能掩码兜底
            return MaskUtil.mask(String.valueOf(obj));
        }
    }

    /**
     * 反射收集字段：类上存在任一 {@link SensitiveField} 注解时收集全部字段——
     * 注解字段按注解类型直接掩码，其余字段原值交给 maskMap 按敏感词/正则处理。
     * 无注解字段时返回 null（走默认序列化路径）。
     */
    private Map<String, Object> collectAnnotatedFields(Object obj) throws IllegalAccessException {
        Map<String, Object> result = null;
        boolean hasAnnotation = false;
        for (Class<?> clazz = obj.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                SensitiveField ann = f.getAnnotation(SensitiveField.class);
                if (ann != null) {
                    hasAnnotation = true;
                }
            }
        }
        if (!hasAnnotation) {
            return null;
        }
        result = new java.util.LinkedHashMap<>();
        for (Class<?> clazz = obj.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
                SensitiveField ann = f.getAnnotation(SensitiveField.class);
                f.setAccessible(true);
                Object raw = f.get(obj);
                if (ann != null) {
                    result.put(f.getName(), raw == null ? null : MaskUtil.mask(String.valueOf(raw), ann.value()));
                } else {
                    result.put(f.getName(), raw);
                }
            }
        }
        return result;
    }

    private void maskMap(Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> child = (Map<String, Object>) v;
                maskMap(child);
            } else if (v instanceof java.util.List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?>) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> itemMap = (Map<String, Object>) item;
                        maskMap(itemMap);
                    }
                }
            } else {
                e.setValue(MaskUtil.maskByKey(e.getKey(), v));
            }
        }
    }
}
