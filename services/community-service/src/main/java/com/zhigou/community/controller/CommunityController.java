package com.zhigou.community.controller;

import com.zhigou.common.Result;
import com.zhigou.community.dto.NoteVO;
import com.zhigou.community.entity.CommunityLive;
import com.zhigou.community.entity.CommunityVideo;
import com.zhigou.community.service.CommunityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    /** 发布笔记 */
    @PostMapping("/community/note")
    public Result<NoteVO> publish(@RequestBody Map<String, Object> body) {
        Long spuId = body.get("spuId") == null ? null : Long.valueOf(String.valueOf(body.get("spuId")));
        @SuppressWarnings("unchecked")
        List<String> images = (List<String>) body.get("images");
        return Result.ok(communityService.publish(
                Long.valueOf(String.valueOf(body.get("userId"))),
                (String) body.get("authorName"),
                spuId,
                (String) body.get("title"),
                (String) body.get("content"),
                images));
    }

    /** 信息流分页 */
    @GetMapping("/community/note/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) Long userId) {
        return Result.ok(communityService.pageNotes(pageNum, pageSize, userId));
    }

    /** 笔记详情（含评论） */
    @GetMapping("/community/note/{id}")
    public Result<NoteVO> detail(@PathVariable Long id, @RequestParam(required = false) Long userId) {
        return Result.ok(communityService.detail(id, userId));
    }

    /** 点赞/取消 */
    @PostMapping("/community/note/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return Result.ok(communityService.toggleLike(id, body.get("userId")));
    }

    /** 收藏/取消 */
    @PostMapping("/community/note/{id}/favorite")
    public Result<Map<String, Object>> favorite(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return Result.ok(communityService.toggleFavorite(id, body.get("userId")));
    }

    /** 发表评论 */
    @PostMapping("/community/comment")
    public Result<Map<String, Object>> comment(@RequestBody Map<String, Object> body) {
        return Result.ok(communityService.comment(
                Long.valueOf(String.valueOf(body.get("noteId"))),
                Long.valueOf(String.valueOf(body.get("userId"))),
                (String) body.get("authorName"),
                (String) body.get("content")));
    }

    /** 我的笔记 */
    @GetMapping("/community/mine")
    public Result<List<NoteVO>> mine(@RequestParam Long userId) {
        return Result.ok(communityService.mine(userId));
    }

    // ===== 短视频（图文 MVP）=====

    /** 短视频信息流 */
    @GetMapping("/community/video/page")
    public Result<List<CommunityVideo>> videoPage(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(communityService.videoPage(pageNum, pageSize));
    }

    /** 发布短视频（图文物料） */
    @PostMapping("/community/video")
    public Result<CommunityVideo> videoPublish(@RequestBody CommunityVideo v) {
        return Result.ok(communityService.videoPublish(v));
    }

    /** 短视频详情（自增播放量） */
    @GetMapping("/community/video/{id}")
    public Result<CommunityVideo> videoDetail(@PathVariable Long id) {
        return Result.ok(communityService.videoDetail(id));
    }

    /** 视频点赞/取消 */
    @PostMapping("/community/video/{id}/like")
    public Result<Map<String, Object>> videoLike(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return Result.ok(communityService.videoLike(id, body.get("userId")));
    }

    /** 视频收藏/取消 */
    @PostMapping("/community/video/{id}/favorite")
    public Result<Map<String, Object>> videoFavorite(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return Result.ok(communityService.videoFavorite(id, body.get("userId")));
    }

    // ===== 直播 =====

    /** 直播列表（预告/直播中/已结束） */
    @GetMapping("/community/live/list")
    public Result<List<CommunityLive>> liveList() {
        return Result.ok(communityService.liveList());
    }

    /** 直播详情 */
    @GetMapping("/community/live/{id}")
    public Result<CommunityLive> liveDetail(@PathVariable Long id) {
        return Result.ok(communityService.liveDetail(id));
    }
}

