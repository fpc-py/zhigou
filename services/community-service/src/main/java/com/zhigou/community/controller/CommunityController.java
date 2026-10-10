package com.zhigou.community.controller;

import com.zhigou.common.Result;
import com.zhigou.community.dto.NoteVO;
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
}
