"""
智购 AI Orchestrator — AI 在线客服（FAQ 知识库 + 人工工单）

设计要点（对齐项目红线）：
- 不重复造轮子：答疑优先走**结构化 FAQ 知识库**（关键词命中返回确定答案，不逐条烧 LLM、不编造）；
  未命中如实说明，交由主对话链路（其他工具/LLM）继续，绝不硬答；
- 转人工确认门：仅当用户明确表达「转人工 / 找客服 / 投诉 / 举报 / 人工处理」时才创建工单（路由层已
  按强信号强制触发，工具内部再做一次复核，双保险——错误不骚扰红线）；
- 工单持久化：data/support_tickets.json（与会话记忆 data/sessions、定时购 data/scheduled_buys.json
  同目录风格，原子写 tmp+replace，生产可换 Redis/DB）。

工单状态机：OPEN（待处理）→ PROCESSING（处理中）→ RESOLVED（已完结）；CLOSED（用户取消）。
"""

from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

_DATA_DIR = Path(__file__).resolve().parent.parent / "data"
_TICKETS_FILE = _DATA_DIR / "support_tickets.json"

# ── 工单状态 ──
OPEN = "OPEN"
PROCESSING = "PROCESSING"
RESOLVED = "RESOLVED"
CLOSED = "CLOSED"

_CATEGORY_HINT = {
    "COMPLAINT": "投诉/举报",
    "INVOICE": "发票",
    "AFTERSALE": "售后咨询",
    "LOGISTICS": "物流咨询",
    "GENERAL": "综合咨询",
}

# ── FAQ 知识库（结构化，关键词命中即返回确定答案，杜绝编造） ──
# 每项：(topic, [关键词], [答案行])
_FAQ: list[tuple[str, list[str], list[str]]] = [
    (
        "运费规则",
        ["运费", "配送费", "邮费", "快递费", "包邮", "运费怎么", "运费多少"],
        [
            "运费规则（智购平台）",
            "· 单笔订单满 ¥99 包邮（不含偏远地区）；未满 ¥99 收取 ¥8 运费",
            "· 偏远地区（新疆/西藏/内蒙古/青海/甘肃/宁夏）：首重 ¥12，续重 ¥4/kg",
            "· 运费在结算页实时计算并展示，下单前可见，不会额外加收",
        ],
    ),
    (
        "退货政策",
        ["退货政策", "退换货政策", "七天无理由", "7天无理由", "七天退", "7天退", "退款政策"],
        [
            "退换货政策（智购平台）",
            "· 支持签收后 7 天无理由退货（商品完好、不影响二次销售）",
            "· 质量问题（破损/错发/故障）：支持退货退款或换货，运费由平台承担",
            "· 申请入口：订单详情 → 申请售后；或直接对「小智」说『我要退货/换货』，AI 会引导提交",
        ],
    ),
    (
        "发票",
        ["发票", "开票", "报销", "发票怎么", "开发票", "电子发票"],
        [
            "发票说明（智购平台）",
            "· 支持开具电子普通发票，订单完成后可在「我的-发票」申请",
            "· 可填写单位抬头（企业报销需提供税号）；发票金额为实付金额",
            "· 如发票信息有误，请联系人工客服更正（工作时间 24 小时内处理）",
        ],
    ),
    (
        "发货时效",
        ["多久发货", "几天发货", "发货时效", "几天到", "多久到", "什么时候发货", "什么时候到"],
        [
            "发货时效说明",
            "· 现货商品下单后 48 小时内发货（大促期间以页面公告为准）",
            "· 物流时效参考：同城 1-2 天，省内 2-3 天，跨省 3-5 天（偏远地区 5-7 天）",
            "· 物流进度可在「我的-订单-物流」实时查看；有异常可让「小智」帮你查",
        ],
    ),
    (
        "会员权益",
        ["会员权益", "会员等级", "积分怎么", "积分规则", "有什么权益", "会员卡", "等级权益"],
        [
            "会员权益说明（智购平台）",
            "· 会员等级按近 12 个月累计消费划分：普通 / 白银 / 黄金 / 铂金 / 黑金",
            "· 权益包括：专属券、运费减免次数、生日礼包、专属客服优先响应等",
            "· 等级与权益明细可在「我的-会员中心」查看，或让「小智」帮你解读",
        ],
    ),
    (
        "优惠券规则",
        ["优惠券怎么用", "优惠券规则", "券怎么用", "满减怎么用", "优惠券叠加", "券叠加"],
        [
            "优惠券使用说明",
            "· 结算页自动匹配最优优惠组合；优惠券与满减/秒杀互斥时，系统自动取更优惠方案",
            "· 每笔订单限用一张优惠券，不可拆分使用；有效期以券面为准",
            "· 让「小智」帮你凑单时，会自动逐券试算最优方案（AI 凑单）",
        ],
    ),
    (
        "支付方式",
        ["支付方式", "支持哪些支付", "怎么支付", "可以用什么支付", "支付渠道"],
        [
            "支付方式说明",
            "· 当前支持：微信支付 / 支付宝 / 账户余额支付",
            "· 余额支付需先开通会员钱包（我的-钱包）；支付金额以订单实付为准",
        ],
    ),
]


def _match_faq(issue: str) -> tuple[str, list[str]] | None:
    """关键词命中返回 (topic, 答案行)；未命中返回 None（交由主对话链路，不硬答）。"""
    text = issue or ""
    for topic, keywords, lines in _FAQ:
        if any(k in text for k in keywords):
            return topic, lines
    return None


# ── 工单持久化 ──


def _load_tickets() -> list[dict[str, Any]]:
    if not _TICKETS_FILE.exists():
        return []
    try:
        data = json.loads(_TICKETS_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, list) else []
    except Exception as e:
        logger.warning("support_tickets.json 读取失败: %s", e)
        return []


def _save_tickets(tickets: list[dict[str, Any]]) -> None:
    _DATA_DIR.mkdir(parents=True, exist_ok=True)
    tmp = _TICKETS_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(tickets, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(_TICKETS_FILE)


def create_ticket(user_id: str, category: str, issue: str) -> dict[str, Any]:
    """创建客服工单。返回工单 dict（含 ticketNo）。"""
    tickets = _load_tickets()
    ticket = {
        "id": "ST" + uuid.uuid4().hex[:12].upper(),
        "userId": user_id,
        "category": category if category in _CATEGORY_HINT else "GENERAL",
        "issue": (issue or "")[:500],
        "status": OPEN,
        "createdAt": datetime.now().isoformat(),
        "resolvedAt": None,
        "assignee": None,  # 商家侧客服工作台二期
    }
    tickets.append(ticket)
    _save_tickets(tickets)
    return ticket


def _classify(issue: str) -> str:
    """对用户问题做粗分类（用于工单类别）。"""
    text = issue or ""
    if any(k in text for k in ["投诉", "举报", "差评", "拉黑", "申诉"]):
        return "COMPLAINT"
    if any(k in text for k in ["发票", "开票", "报销"]):
        return "INVOICE"
    if any(k in text for k in ["退货", "退款", "换货", "售后", "坏了", "破损"]):
        return "AFTERSALE"
    if any(k in text for k in ["物流", "快递", "没到", "延误"]):
        return "LOGISTICS"
    return "GENERAL"


def format_faq_answer(topic: str, lines: list[str]) -> str:
    return "\n".join([f"【{topic}】"] + lines)


def format_ticket(t: dict[str, Any]) -> str:
    return "\n".join(
        [
            "客服工单已创建 ✅",
            f"· 工单号：{t.get('id')}",
            f"· 类别：{_CATEGORY_HINT.get(t.get('category'), t.get('category'))}",
            "· 预计响应：24 小时内人工客服跟进（工作时间）",
            "· 后续可在本会话继续提问，或等待人工客服主动联系",
        ]
    )
