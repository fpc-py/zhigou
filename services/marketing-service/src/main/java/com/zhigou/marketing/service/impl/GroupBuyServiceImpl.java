package com.zhigou.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.marketing.dto.GroupBuyActivityVO;
import com.zhigou.marketing.dto.GroupBuyGroupVO;
import com.zhigou.marketing.entity.GroupBuyActivity;
import com.zhigou.marketing.entity.GroupBuyMember;
import com.zhigou.marketing.entity.GroupBuyOrder;
import com.zhigou.marketing.mapper.GroupBuyActivityMapper;
import com.zhigou.marketing.mapper.GroupBuyMemberMapper;
import com.zhigou.marketing.mapper.GroupBuyOrderMapper;
import com.zhigou.marketing.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class GroupBuyServiceImpl implements GroupBuyService {

    private static final String OPEN = "OPEN";
    private static final String SUCCESS = "SUCCESS";
    private static final String CLOSED = "CLOSED";

    private final GroupBuyActivityMapper activityMapper;
    private final GroupBuyOrderMapper orderMapper;
    private final GroupBuyMemberMapper memberMapper;

    @Override
    public List<GroupBuyActivityVO> activities() {
        List<GroupBuyActivity> acts = activityMapper.selectList(new LambdaQueryWrapper<GroupBuyActivity>()
                .eq(GroupBuyActivity::getStatus, 1)
                .orderByDesc(GroupBuyActivity::getId));
        List<GroupBuyOrder> openOrders = orderMapper.selectList(new LambdaQueryWrapper<GroupBuyOrder>()
                .eq(GroupBuyOrder::getStatus, OPEN));
        Map<Long, List<GroupBuyOrder>> byAct = openOrders.stream()
                .collect(Collectors.groupingBy(GroupBuyOrder::getActivityId));

        return acts.stream().map(a -> {
            List<GroupBuyOrder> groups = byAct.getOrDefault(a.getId(), Collections.emptyList());
            List<GroupBuyGroupVO> vos = new ArrayList<>();
            for (GroupBuyOrder g : groups) {
                int memberCount = countMember(g.getId());
                vos.add(GroupBuyGroupVO.builder()
                        .groupId(g.getId()).leaderUserId(g.getLeaderUserId())
                        .memberCount(memberCount).targetSize(g.getTargetSize())
                        .remain(Math.max(0, g.getTargetSize() - memberCount))
                        .status(g.getStatus()).expireTime(g.getExpireTime()).build());
            }
            return GroupBuyActivityVO.builder()
                    .id(a.getId()).skuId(a.getSkuId()).spuId(a.getSpuId())
                    .title(a.getTitle()).imageUrl(a.getImageUrl())
                    .soloPrice(a.getSoloPrice()).groupPrice(a.getGroupPrice())
                    .groupSize(a.getGroupSize()).limitMinutes(a.getLimitMinutes())
                    .startTime(a.getStartTime()).endTime(a.getEndTime())
                    .openGroups(vos).build();
        }).collect(Collectors.toList());
    }

    @Override @Transactional
    public GroupBuyOrder open(Long activityId, Long userId) {
        GroupBuyActivity act = activityMapper.selectById(activityId);
        if (act == null || act.getStatus() != 1) throw new BizException(404, "拼团活动不存在或已结束");
        if (act.getGroupStock() != null && act.getGroupStock() <= 0) throw new BizException(40040, "该活动成团名额已满");

        // 同一用户同一活动同时只能有一个 OPEN 团
        List<GroupBuyOrder> mine = orderMapper.selectList(new LambdaQueryWrapper<GroupBuyOrder>()
                .eq(GroupBuyOrder::getLeaderUserId, userId)
                .eq(GroupBuyOrder::getActivityId, activityId)
                .eq(GroupBuyOrder::getStatus, OPEN));
        if (!mine.isEmpty()) throw new BizException(40041, "你已开过该商品的团，等待成团即可");

        LocalDateTime now = LocalDateTime.now();
        GroupBuyOrder order = new GroupBuyOrder();
        order.setActivityId(activityId);
        order.setLeaderUserId(userId);
        order.setTargetSize(act.getGroupSize());
        order.setStatus(OPEN);
        order.setExpireTime(now.plusHours(act.getLimitMinutes() != null ? act.getLimitMinutes() : 24));
        orderMapper.insert(order);

        GroupBuyMember leader = new GroupBuyMember();
        leader.setGroupId(order.getId());
        leader.setUserId(userId);
        leader.setIsLeader(1);
        leader.setJoinTime(now);
        memberMapper.insert(leader);

        // 扣活动库存
        act.setGroupStock(act.getGroupStock() - 1);
        activityMapper.updateById(act);

        log.info("开团: activityId={}, userId={}, groupId={}", activityId, userId, order.getId());
        return order;
    }

    @Override @Transactional
    public GroupBuyOrder join(Long groupId, Long userId) {
        GroupBuyOrder order = orderMapper.selectById(groupId);
        if (order == null) throw new BizException(404, "团单不存在");
        if (!OPEN.equals(order.getStatus())) throw new BizException(40042, "该团已结束");
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            order.setStatus(CLOSED);
            orderMapper.updateById(order);
            throw new BizException(40042, "该团已过期");
        }
        if (order.getLeaderUserId().equals(userId)) throw new BizException(40043, "你已是该团团主");
        long joined = memberMapper.selectCount(new LambdaQueryWrapper<GroupBuyMember>()
                .eq(GroupBuyMember::getGroupId, groupId)
                .eq(GroupBuyMember::getUserId, userId));
        if (joined > 0) throw new BizException(40043, "你已参与该团");

        GroupBuyMember m = new GroupBuyMember();
        m.setGroupId(groupId);
        m.setUserId(userId);
        m.setIsLeader(0);
        m.setJoinTime(LocalDateTime.now());
        memberMapper.insert(m);

        int memberCount = countMember(groupId);
        if (memberCount >= order.getTargetSize()) {
            order.setStatus(SUCCESS);
            order.setSuccessTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("成团: groupId={}, members={}", groupId, memberCount);
        }
        return orderMapper.selectById(groupId);
    }

    @Override
    public List<Map<String, Object>> mine(Long userId) {
        List<Long> groupIds = new ArrayList<>();
        List<GroupBuyOrder> led = orderMapper.selectList(new LambdaQueryWrapper<GroupBuyOrder>()
                .eq(GroupBuyOrder::getLeaderUserId, userId));
        led.forEach(g -> groupIds.add(g.getId()));
        memberMapper.selectList(new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getUserId, userId))
                .forEach(m -> groupIds.add(m.getGroupId()));
        if (groupIds.isEmpty()) return Collections.emptyList();

        List<GroupBuyOrder> orders = orderMapper.selectBatchIds(groupIds.stream().distinct().collect(Collectors.toList()));
        Map<Long, GroupBuyActivity> actMap = activityMapper.selectBatchIds(
                        orders.stream().map(GroupBuyOrder::getActivityId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(GroupBuyActivity::getId, a -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupBuyOrder o : orders) {
            int memberCount = countMember(o.getId());
            GroupBuyActivity a = actMap.get(o.getActivityId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupId", o.getId());
            row.put("status", o.getStatus());
            row.put("memberCount", memberCount);
            row.put("targetSize", o.getTargetSize());
            row.put("remain", Math.max(0, o.getTargetSize() - memberCount));
            row.put("expireTime", o.getExpireTime());
            row.put("successTime", o.getSuccessTime());
            row.put("activity", a);
            result.add(row);
        }
        return result;
    }

    @Override
    public Map<String, Object> detail(Long groupId) {
        GroupBuyOrder o = orderMapper.selectById(groupId);
        if (o == null) throw new BizException(404, "团单不存在");
        GroupBuyActivity a = activityMapper.selectById(o.getActivityId());
        List<GroupBuyMember> members = memberMapper.selectList(new LambdaQueryWrapper<GroupBuyMember>()
                .eq(GroupBuyMember::getGroupId, groupId)
                .orderByDesc(GroupBuyMember::getIsLeader));
        int memberCount = members.size();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("groupId", o.getId());
        row.put("status", o.getStatus());
        row.put("leaderUserId", o.getLeaderUserId());
        row.put("memberCount", memberCount);
        row.put("targetSize", o.getTargetSize());
        row.put("remain", Math.max(0, o.getTargetSize() - memberCount));
        row.put("expireTime", o.getExpireTime());
        row.put("successTime", o.getSuccessTime());
        row.put("activity", a);
        row.put("members", members);
        return row;
    }

    private int countMember(Long groupId) {
        return Math.toIntExact(memberMapper.selectCount(new LambdaQueryWrapper<GroupBuyMember>()
                .eq(GroupBuyMember::getGroupId, groupId)));
    }
}
