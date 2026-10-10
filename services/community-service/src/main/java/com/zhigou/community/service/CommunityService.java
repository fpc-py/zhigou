package com.zhigou.community.service;

import com.zhigou.community.dto.NoteVO;
import com.zhigou.community.entity.CommunityLive;
import com.zhigou.community.entity.CommunityVideo;

import java.util.List;
import java.util.Map;

public interface CommunityService {

    /** 发布笔记 */
    NoteVO publish(Long userId, String authorName, Long spuId, String title, String content, List<String> images);

    /** 信息流分页（status=0 正常；带 userId 时返回 liked/favorited 态） */
    Map<String, Object> pageNotes(int pageNum, int pageSize, Long userId);

    /** 笔记详情（含评论） */
    NoteVO detail(Long id, Long userId);

    /** 点赞/取消（幂等切换），返回 {liked, likeCount} */
    Map<String, Object> toggleLike(Long noteId, Long userId);

    /** 收藏/取消（幂等切换），返回 {favorited, favoriteCount} */
    Map<String, Object> toggleFavorite(Long noteId, Long userId);

    /** 发表评论 */
    Map<String, Object> comment(Long noteId, Long userId, String authorName, String content);

    /** 我的笔记 */
    List<NoteVO> mine(Long userId);

    // ===== 短视频（图文 MVP）=====
    List<CommunityVideo> videoPage(int pageNum, int pageSize);

    CommunityVideo videoDetail(Long id);

    CommunityVideo videoPublish(CommunityVideo v);

    Map<String, Object> videoLike(Long videoId, Long userId);

    Map<String, Object> videoFavorite(Long videoId, Long userId);

    // ===== 直播 =====
    List<CommunityLive> liveList();

    CommunityLive liveDetail(Long id);
}

