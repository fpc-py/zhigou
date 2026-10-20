package com.zhigou.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B3 反射型 XSS 过滤单元测试：攻击样本被 400 拦截；正常请求放行并附加安全头。
 */
class XssFilterTest {

    private final XssFilter filter = new XssFilter();

    @Test
    void scriptTagInQuery_isBlocked() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setQueryString("kw=<script>alert(1)</script>");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        assertEquals(400, resp.getStatus());
        assertTrue(resp.getContentAsString().contains("40010"));
    }

    @Test
    void onerrorInQuery_isBlocked() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setQueryString("img=<img src=x onerror=alert(1)>");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        assertEquals(400, resp.getStatus());
    }

    @Test
    void javascriptSchemeInQuery_isBlocked() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setQueryString("url=javascript:alert(1)");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        assertEquals(400, resp.getStatus());
    }

    @Test
    void normalQuery_passesAndAddsSecurityHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setQueryString("kw=iphone&page=1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getHeader("Content-Security-Policy"));
        assertEquals("nosniff", resp.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", resp.getHeader("X-Frame-Options"));
        assertEquals("1; mode=block", resp.getHeader("X-XSS-Protection"));
    }

    @Test
    void mixedEncodingXss_isBlocked() throws Exception {
        // 大小写/空白变形样本
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/search");
        req.setQueryString("kw=<ScRiPt>alert(1)</ScRiPt>");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        assertEquals(400, resp.getStatus());
    }
}
