package com.zhigou.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.community.dto.NoteVO;
import com.zhigou.community.entity.CommunityComment;
import com.zhigou.community.entity.CommunityInteraction;
import com.zhigou.community.entity.CommunityLive;
import com.zhigou.community.entity.CommunityNote;
import com.zhigou.community.entity.CommunityVideo;
import com.zhigou.community.mapper.CommunityCommentMapper;
import com.zhigou.community.mapper.CommunityInteractionMapper;
import com.zhigou.community.mapper.CommunityLiveMapper;
import com.zhigou.community.mapper.CommunityNoteMapper;
import com.zhigou.community.mapper.CommunityVideoMapper;
import com.zhigou.community.service.CommunityService;
import com.zhigou.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {

    private final CommunityNoteMapper noteMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityInteractionMapper interactionMapper;
    private final CommunityVideoMapper videoMapper;
    private final CommunityLiveMapper liveMapper;

    /** 营销/广告信号词（命中即标记疑似营销内容，不拦截仅降权展示） */
    private static final String[] SPAM_WORDS = {
            "加V", "加微信", "正品保证", "批发", "低价出售", "点击链接", "私聊", "返现", "刷单", "好评返", "关注领取", "V信", "微商"
    };

    private static final int FAKE_FLAG_YES = 1;
    private static final int FAKE_FLAG_NO = 0;
    private static final int TYPE_LIKE = 1;
    private static final int TYPE_FAVORITE = 2;
    private static final int TYPE_VIDEO_LIKE = 3;
    private static final int TYPE_VIDEO_FAVORITE = 4;

    // ── 发布 ──

    @Override
    @Transactional
    public NoteVO publish(Long userId, String authorName, Long spuId, String title, String content, List<String> images) {
        if (userId == null) throw new BizException(401, "请先登录");
        if (!StringUtils.hasText(title) || !StringUtils.hasText(content)) {
            throw new BizException(400, "标题与正文不能为空");
        }
        if (title.length() > 60) throw new BizException(400, "标题最长 60 字");
        if (content.length() > 5000) throw new BizException(400, "正文最长 5000 字");

        CommunityNote note = new CommunityNote();
        note.setAuthorId(userId);
        note.setAuthorName(StringUtils.hasText(authorName) ? authorName : "智友" + (userId % 10000));
        note.setSpuId(spuId);
        note.setTitle(title.trim());
        note.setContent(content.trim());
        note.setImages(images == null || images.isEmpty() ? "[]" : new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(images).toString());
        note.setLikeCount(0);
        note.setFavoriteCount(0);
        note.setCommentCount(0);
        note.setFakeFlag(judgeFake(title, content, userId));
        note.setStatus(0);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(note);
        log.info("发布笔记: noteId={}, userId={}, fakeFlag={}", note.getId(), userId, note.getFakeFlag());
        return detail(note.getId(), userId);
    }

    /** 规则化虚假/营销内容识别：命中信号词或 10 分钟内同正文重复发布 → fakeFlag=1 */
    private int judgeFake(String title, String content, Long userId) {
        String text = title + " " + content;
        for (String w : SPAM_WORDS) {
            if (text.contains(w)) return FAKE_FLAG_YES;
        }
        LambdaQueryWrapper<CommunityNote> dup = new LambdaQueryWrapper<CommunityNote>()
                .eq(CommunityNote::getContent, content.trim())
                .ge(CommunityNote::getCreatedAt, LocalDateTime.now().minusMinutes(10));
        if (noteMapper.selectCount(dup) > 0) return FAKE_FLAG_YES;
        return FAKE_FLAG_NO;
    }

    // ── 信息流 ──

    @Override
    public Map<String, Object> pageNotes(int pageNum, int pageSize, Long userId) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 50);
        LambdaQueryWrapper<CommunityNote> qw = new LambdaQueryWrapper<CommunityNote>()
                .eq(CommunityNote::getStatus, 0)
                .orderByDesc(CommunityNote::getCreatedAt);
        long total = noteMapper.selectCount(qw);
        List<CommunityNote> rows = noteMapper.selectList(qw.last("LIMIT " + (pn - 1) * ps + "," + ps));
        List<NoteVO> list = rows.stream().map(n -> toVO(n, userId)).collect(Collectors.toList());
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", list);
        page.put("total", total);
        page.put("pageNum", pn);
        page.put("pageSize", ps);
        return page;
    }

    @Override
    public NoteVO detail(Long id, Long userId) {
        CommunityNote note = noteMapper.selectById(id);
        if (note == null || note.getStatus() != 0) throw new BizException(404, "笔记不存在或已删除");
        NoteVO vo = toVO(note, userId);
        vo.setComments(commentsOf(id));
        return vo;
    }

    @Override
    public List<NoteVO> mine(Long userId) {
        List<CommunityNote> rows = noteMapper.selectList(new LambdaQueryWrapper<CommunityNote>()
                .eq(CommunityNote::getAuthorId, userId)
                .orderByDesc(CommunityNote::getCreatedAt));
        return rows.stream().map(n -> toVO(n, userId)).collect(Collectors.toList());
    }

    // ── 互动 ──

    @Override
    @Transactional
    public Map<String, Object> toggleLike(Long noteId, Long userId) {
        Map<String, Object> r = toggle(noteId, userId, TYPE_LIKE, true);
        return r;
    }

    @Override
    @Transactional
    public Map<String, Object> toggleFavorite(Long noteId, Long userId) {
        Map<String, Object> r = toggle(noteId, userId, TYPE_FAVORITE, false);
        return r;
    }

    private Map<String, Object> toggle(Long noteId, Long userId, int type, boolean isLike) {
        CommunityNote note = noteMapper.selectById(noteId);
        if (note == null) throw new BizException(404, "笔记不存在");
        CommunityInteraction exist = interactionMapper.selectOne(new LambdaQueryWrapper<CommunityInteraction>()
                .eq(CommunityInteraction::getNoteId, noteId)
                .eq(CommunityInteraction::getUserId, userId)
                .eq(CommunityInteraction::getType, type));
        boolean active;
        if (exist != null) {
            interactionMapper.deleteById(exist.getId());
            active = false;
        } else {
            CommunityInteraction it = new CommunityInteraction();
            it.setNoteId(noteId);
            it.setUserId(userId);
            it.setType(type);
            it.setCreatedAt(LocalDateTime.now());
            interactionMapper.insert(it);
            active = true;
        }
        // 重算计数（避免并发脏数：以互动表为准）
        Long cnt = interactionMapper.selectCount(new LambdaQueryWrapper<CommunityInteraction>()
                .eq(CommunityInteraction::getNoteId, noteId).eq(CommunityInteraction::getType, type));
        int c = cnt.intValue();
        if (isLike) {
            note.setLikeCount(c);
        } else {
            note.setFavoriteCount(c);
        }
        noteMapper.updateById(note);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(isLike ? "liked" : "favorited", active);
        r.put(isLike ? "likeCount" : "favoriteCount", c);
        return r;
    }

    // ── 评论 ──

    @Override
    @Transactional
    public Map<String, Object> comment(Long noteId, Long userId, String authorName, String content) {
        CommunityNote note = noteMapper.selectById(noteId);
        if (note == null) throw new BizException(404, "笔记不存在");
        if (!StringUtils.hasText(content) || content.length() > 500) throw new BizException(400, "评论最长 500 字");
        CommunityComment c = new CommunityComment();
        c.setNoteId(noteId);
        c.setUserId(userId);
        c.setAuthorName(StringUtils.hasText(authorName) ? authorName : "智友" + (userId % 10000));
        c.setContent(content.trim());
        c.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(c);
        note.setCommentCount(note.getCommentCount() == null ? 1 : note.getCommentCount() + 1);
        noteMapper.updateById(note);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", c.getId());
        r.put("noteId", String.valueOf(noteId));
        r.put("authorName", c.getAuthorName());
        r.put("content", c.getContent());
        r.put("createdAt", c.getCreatedAt().toString());
        return r;
    }

    // ── 组装 ──

    private NoteVO toVO(CommunityNote n, Long userId) {
        NoteVO vo = new NoteVO();
        vo.setId(n.getId());
        vo.setAuthorId(n.getAuthorId());
        vo.setAuthorName(n.getAuthorName());
        vo.setSpuId(n.getSpuId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setLikeCount(n.getLikeCount() == null ? 0 : n.getLikeCount());
        vo.setFavoriteCount(n.getFavoriteCount() == null ? 0 : n.getFavoriteCount());
        vo.setCommentCount(n.getCommentCount() == null ? 0 : n.getCommentCount());
        vo.setFakeFlag(n.getFakeFlag() == null ? 0 : n.getFakeFlag());
        vo.setCreatedAt(n.getCreatedAt());
        try {
            List<String> imgs = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                    n.getImages() == null ? "[]" : n.getImages(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            vo.setImages(imgs == null ? new ArrayList<>() : imgs);
        } catch (Exception e) {
            vo.setImages(new ArrayList<>());
        }
        vo.setLiked(userId != null && interactionMapper.selectCount(new LambdaQueryWrapper<CommunityInteraction>()
                .eq(CommunityInteraction::getNoteId, n.getId())
                .eq(CommunityInteraction::getUserId, userId)
                .eq(CommunityInteraction::getType, TYPE_LIKE)) > 0);
        vo.setFavorited(userId != null && interactionMapper.selectCount(new LambdaQueryWrapper<CommunityInteraction>()
                .eq(CommunityInteraction::getNoteId, n.getId())
                .eq(CommunityInteraction::getUserId, userId)
                .eq(CommunityInteraction::getType, TYPE_FAVORITE)) > 0);
        return vo;
    }

    private List<Map<String, Object>> commentsOf(Long noteId) {
        List<CommunityComment> list = commentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                .eq(CommunityComment::getNoteId, noteId)
                .orderByAsc(CommunityComment::getCreatedAt));
        return list.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(c.getId()));
            m.put("authorName", c.getAuthorName());
            m.put("content", c.getContent());
            m.put("createdAt", c.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
    }

    // ===== 短视频（图文 MVP）=====

    @Override
    public List<CommunityVideo> videoPage(int pageNum, int pageSize) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), 50);
        return videoMapper.selectList(new LambdaQueryWrapper<CommunityVideo>()
                .eq(CommunityVideo::getStatus, 0)
                .orderByDesc(CommunityVideo::getCreatedAt)
                .last("LIMIT " + (pn - 1) * ps + "," + ps));
    }

    @Override
    public CommunityVideo videoDetail(Long id) {
        CommunityVideo v = videoMapper.selectById(id);
        if (v == null || v.getStatus() != 0) throw new BizException(404, "视频不存在或已删除");
        v.setViewCount(v.getViewCount() == null ? 1 : v.getViewCount() + 1);
        videoMapper.updateById(v);
        return v;
    }

    @Override
    @Transactional
    public CommunityVideo videoPublish(CommunityVideo v) {
        if (v.getAuthorId() == null) throw new BizException(401, "请先登录");
        if (!StringUtils.hasText(v.getTitle()) || !StringUtils.hasText(v.getVideoUrl())) {
            throw new BizException(400, "标题与视频/图文物料不能为空");
        }
        if (v.getTitle().length() > 60) throw new BizException(400, "标题最长 60 字");
        v.setAuthorName(StringUtils.hasText(v.getAuthorName()) ? v.getAuthorName() : "智友" + (v.getAuthorId() % 10000));
        if (v.getLikeCount() == null) v.setLikeCount(0);
        if (v.getFavoriteCount() == null) v.setFavoriteCount(0);
        if (v.getCommentCount() == null) v.setCommentCount(0);
        if (v.getViewCount() == null) v.setViewCount(0);
        if (v.getDurationSec() == null) v.setDurationSec(0);
        if (v.getStatus() == null) v.setStatus(0);
        v.setCreatedAt(LocalDateTime.now());
        v.setUpdatedAt(LocalDateTime.now());
        videoMapper.insert(v);
        return v;
    }

    @Override
    @Transactional
    public Map<String, Object> videoLike(Long videoId, Long userId) {
        return videoToggle(videoId, userId, TYPE_VIDEO_LIKE, true);
    }

    @Override
    @Transactional
    public Map<String, Object> videoFavorite(Long videoId, Long userId) {
        return videoToggle(videoId, userId, TYPE_VIDEO_FAVORITE, false);
    }

    private Map<String, Object> videoToggle(Long videoId, Long userId, int type, boolean isLike) {
        CommunityVideo v = videoMapper.selectById(videoId);
        if (v == null) throw new BizException(404, "视频不存在");
        CommunityInteraction exist = interactionMapper.selectOne(new LambdaQueryWrapper<CommunityInteraction>()
                .eq(CommunityInteraction::getNoteId, videoId)
                .eq(CommunityInteraction::getUserId, userId)
                .eq(CommunityInteraction::getType, type));
        boolean active;
        if (exist != null) {
            interactionMapper.deleteById(exist.getId());
            active = false;
        } else {
            CommunityInteraction it = new CommunityInteraction();
            it.setNoteId(videoId);
            it.setUserId(userId);
            it.setType(type);
            it.setCreatedAt(LocalDateTime.now());
            interactionMapper.insert(it);
            active = true;
        }
        Long cnt = interactionMapper.selectCount(new LambdaQueryWrapper<CommunityInteraction>()
                .eq(CommunityInteraction::getNoteId, videoId).eq(CommunityInteraction::getType, type));
        int c = cnt.intValue();
        if (isLike) v.setLikeCount(c); else v.setFavoriteCount(c);
        videoMapper.updateById(v);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put(isLike ? "liked" : "favorited", active);
        r.put(isLike ? "likeCount" : "favoriteCount", c);
        return r;
    }

    // ===== 直播 =====

    @Override
    public List<CommunityLive> liveList() {
        return liveMapper.selectList(new LambdaQueryWrapper<CommunityLive>()
                .orderByAsc(CommunityLive::getStatus)
                .orderByDesc(CommunityLive::getCreatedAt));
    }

    @Override
    public CommunityLive liveDetail(Long id) {
        CommunityLive l = liveMapper.selectById(id);
        if (l == null) throw new BizException(404, "直播不存在");
        return l;
    }
}
