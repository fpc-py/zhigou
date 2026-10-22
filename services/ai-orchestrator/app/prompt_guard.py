# -*- coding: utf-8 -*-
"""
B4 提示注入防护（Prompt Injection Guard）

输入侧检测：用户 query 命中提示注入特征（试图覆盖系统指令/泄露系统提示/越权扮演/编码变体）时，
不把原始注入文本传给 LLM，降级为安全隔离提示，由 LLM 正常应答（不中断 SSE 流）。

检测策略（启发式关键词 + 变体归一）：
  1. 指令覆盖类：忽略/无视 之前|系统|你的 指令|提示词|规则
  2. 系统提示泄露类：输出/重复/告诉我 系统提示|system prompt|你的指令
  3. 越权扮演类：扮演 开发者|管理员|老板 / 我是你的(创建者|作者) / 解除限制 / 不再受限制
  4. 工具越权类：调用 内部|admin|后台 接口 / 读取 配置|密钥|数据库
  5. 编码变体：大小写混淆、Unicode 全角、关键词中间插空格/符号、base64 包裹指令
"""
from __future__ import annotations

import re
from typing import Optional

# 大小写/全角归一 + 去除空白与干扰字符后匹配
def _normalize(text: str) -> str:
    t = text
    # 全角 → 半角
    for f, h in zip("ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ０１２３４５６７８９：；，。！？（）", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789:;,.!?()"):
        t = t.replace(f, h)
    t = t.lower()
    # 去除关键词间的空格/下划线/连接符（如 i g n o r e / i-g-n-o-r-e）
    t = re.sub(r"[\s_\-.·•]+", "", t)
    return t

# (正则, 描述)
_INJECTION_RULES: list[tuple[re.Pattern, str]] = [
    # 指令覆盖
    (re.compile(r"忽略(之前|上面|以上|所有|之前所有|以上所有|上面所有)?(的)?(指令|指示|提示|规则|要求|命令|内容)"), "指令覆盖"),
    (re.compile(r"无视(之前|上面|以上|所有|之前所有|以上所有|上面所有)?(的)?(指令|指示|提示|规则|要求|命令)"), "指令覆盖"),
    (re.compile(r"不要(遵守|执行|听|管|看)(之前|系统|上面)?(的)?(指令|提示|规则)"), "指令覆盖"),
    (re.compile(r"不要(遵守|执行|听)(之前|系统|上面)?(的)?(指令|提示|规则)"), "指令覆盖"),
    (re.compile(r"从现在开始(你|请)?(只|就)(听|执行|遵守).*?(指令|规则)"), "指令覆盖"),
    # 系统提示泄露
    (re.compile(r"(输出|重复|复述|告诉我|显示)(你(的|得))?(系统提示|系统指令|系统提示词|systemprompt|提示词全文|初始提示)"), "系统提示泄露"),
    (re.compile(r"把(你(的|得))?(系统提示|systemprompt|指令)输出"), "系统提示泄露"),
    (re.compile(r"(系统提示词?|systemprompt|你的指令).{0,6}(输出|泄露|告诉|给我|发给我|写出来)"), "系统提示泄露"),
    # 越权扮演 / 解除限制
    (re.compile(r"扮演(开发者|管理员|老板|我的(主人|创建者))"), "越权扮演"),
    (re.compile(r"我是你的(创建者|作者|开发者|主人|老板)"), "越权扮演"),
    # 坑 #68：端到端补验发现「你是管理员」句式（无"扮演"字）与「不再受任何规则限制」（"规则"插在中间）
    # 均漏检——越权扮演只覆盖"扮演X"单一形态，攻击者换句式即绕过
    (re.compile(r"(现在|请|立刻|马上|直接)?(你|你是|你现在是|现在你是|当你是)(系统|平台|超级|购物)?(管理员|开发者|老板|超级用户)"), "越权扮演"),
    (re.compile(r"(解除|去掉|忽略)(所有)?(限制|约束|安全(规则|限制)|审查)"), "解除限制"),
    (re.compile(r"(不再|没有|不受)(受)?(任何)?[^，。!?；;]{0,8}(限制|约束|审查)|unrestricted|jailbreak"), "解除限制"),
    # 工具/数据越权
    (re.compile(r"(读取|访问|查询|调用).{0,8}(内部|后台|管理|admin).{0,8}(接口|系统|数据)"), "越权访问"),
    (re.compile(r"(读取|获取|输出|告诉我|查看|查询).{0,8}(密钥|密码|token|配置|数据库|表结构|用户数据)"), "越权访问"),
    (re.compile(r"base64(解码|解密)?.{0,12}(指令|提示|系统)"), "编码变体"),
]

# 正常用户高频误报词放行集合（命中规则但属于正常意图时不拦截，白名单语义）
# 坑 #68b：原集合含"帮我"——攻击者把越权请求包装成"帮我做X"即可绕过白名单；
# 收紧为"求知类"信号（学习/解释语境）才放行，"帮我"不再独立触发。
_SAFE_CONTEXT = (
    "为什么", "怎么", "什么是", "如何", "介绍", "解释", "测试", "学习",
    "面试", "课程", "教程", "文档", "写", "翻译", "总结", "讲一下", "说明一下",
)


def detect_prompt_injection(query: str) -> Optional[str]:
    """检测输入注入；命中返回规则描述，未命中返回 None。"""
    if not query or not query.strip():
        return None
    norm = _normalize(query)
    for pattern, desc in _INJECTION_RULES:
        if pattern.search(norm):
            # 正常问询语境放行（如"什么是忽略指令攻击"）
            if any(s in query for s in _SAFE_CONTEXT) and len(query) < 60:
                continue
            return desc
    return None


def sanitize_query(query: str) -> tuple[str, str | None]:
    """
    输入侧防护：命中注入则把用户消息降级为隔离提示（不把注入文本传给 LLM）。
    返回 (处理后的 query, 命中描述 or None)。
    """
    hit = detect_prompt_injection(query)
    if hit is None:
        return query, None
    return (
        "用户刚才发送了一条包含可疑指令覆盖/提示注入特征的消息，已被安全隔离。"
        "请正常回答其与智购平台相关的合理问题；如无法确认意图，礼貌引导用户描述具体需求。",
        hit,
    )
