package com.yunhefc.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.contracts.security.UserAccountReadPort;
import com.yunhefc.contracts.security.UserAccountSnapshot;
import com.yunhefc.content.domain.ContentAlbum;
import com.yunhefc.content.domain.ContentPost;
import com.yunhefc.content.domain.ContentRules;
import com.yunhefc.content.dto.ContentDtos.AlbumSummary;
import com.yunhefc.content.dto.ContentDtos.PostAuthor;
import com.yunhefc.content.dto.ContentDtos.PostCreateRequest;
import com.yunhefc.content.dto.ContentDtos.PostDeleteResult;
import com.yunhefc.content.dto.ContentDtos.PostLikeResult;
import com.yunhefc.content.dto.ContentDtos.PostView;
import com.yunhefc.content.infrastructure.ContentAlbumMapper;
import com.yunhefc.content.infrastructure.ContentPostLikeCountRow;
import com.yunhefc.content.infrastructure.ContentPostLikeMapper;
import com.yunhefc.content.infrastructure.ContentPostMapper;
import com.yunhefc.infra.storage.AvatarUrlSigner;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端动态：CNT-09~12。相册为软引用，删除相册不删除动态；点赞不产生通知。
 */
@Service
@RequiredArgsConstructor
public class ContentPostApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ContentPostApplicationService.class);
    private static final String PERMISSION_DELETE_ANY = "content:delete:any";

    private final ContentPostMapper postMapper;
    private final ContentPostLikeMapper likeMapper;
    private final ContentAlbumMapper albumMapper;
    private final ContentAlbumAssembler albumAssembler;
    private final PlayerReadPort playerReadPort;
    private final UserAccountReadPort userAccountReadPort;
    private final AvatarUrlSigner avatarUrlSigner;

    @Transactional(readOnly = true)
    public PageResponse<PostView> list(Long albumId, long page, long pageSize) {
        Long teamId = CurrentUser.principal().teamId();
        PageQuery pageQuery = PageQuery.of(page, pageSize);
        Page<ContentPost> result = postMapper.selectPage(
                new Page<>(pageQuery.page(), pageQuery.pageSize()),
                new LambdaQueryWrapper<ContentPost>()
                        .eq(ContentPost::getTeamId, teamId)
                        .eq(albumId != null, ContentPost::getAlbumId, albumId)
                        .orderByDesc(ContentPost::getCreatedAt)
                        .orderByDesc(ContentPost::getId));
        return PageResponse.of(views(teamId, result.getRecords()),
                result.getTotal(), pageQuery.page(), pageQuery.pageSize());
    }

    @Transactional
    public PostView create(PostCreateRequest request) {
        AuthPrincipal principal = CurrentUser.principal();
        String content = ContentRules.requirePostContent(request.content());
        requireAlbum(principal.teamId(), request.albumId());

        ContentPost post = new ContentPost();
        post.setTeamId(principal.teamId());
        post.setAuthorUserId(principal.userId());
        post.setContent(content);
        post.setAlbumId(request.albumId());
        postMapper.insert(post);
        ContentPost saved = postMapper.selectById(post.getId());
        log.info("content post created, postId={}, teamId={}", saved.getId(), saved.getTeamId());
        return views(saved.getTeamId(), List.of(saved)).get(0);
    }

    /**
     * 本人或持 content:delete:any 的管理员可删除；不级联相册与点赞。
     */
    @Transactional
    public PostDeleteResult delete(Long postId) {
        AuthPrincipal principal = CurrentUser.principal();
        ContentPost post = requirePost(principal.teamId(), postId);
        boolean canDeleteAny = principal.permissions().contains(PERMISSION_DELETE_ANY);
        if (!post.getAuthorUserId().equals(principal.userId()) && !canDeleteAny) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除本人动态");
        }
        postMapper.deleteById(postId);
        log.info("content post deleted, postId={}, teamId={}", postId, principal.teamId());
        return new PostDeleteResult(true);
    }

    /**
     * 状态幂等：点赞不存在则插入、已取消则恢复同一行；取消置 is_deleted=1；不通知作者。
     */
    @Transactional
    public PostLikeResult setLike(Long postId, boolean liked) {
        AuthPrincipal principal = CurrentUser.principal();
        requirePost(principal.teamId(), postId);
        if (liked) {
            likeMapper.upsertLike(postId, principal.userId());
        } else {
            likeMapper.removeLike(postId, principal.userId());
        }
        // 回读实际状态，避免并发点赞/取消时响应与落库不一致。
        boolean actualLiked = !likeMapper
                .selectLikedPostIds(principal.userId(), List.of(postId)).isEmpty();
        int likeCount = activeLikeCounts(List.of(postId)).getOrDefault(postId, 0);
        return new PostLikeResult(actualLiked, likeCount);
    }

    private List<PostView> views(Long teamId, List<ContentPost> posts) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Long viewerUserId = CurrentUser.principal().userId();
        List<Long> postIds = posts.stream().map(ContentPost::getId).toList();
        Set<Long> authorIds = posts.stream().map(ContentPost::getAuthorUserId).collect(Collectors.toSet());
        Map<Long, PlayerSnapshot> players = playerReadPort.findByUserIds(authorIds).stream()
                .collect(Collectors.toMap(PlayerSnapshot::userId, Function.identity(), (left, right) -> left));
        Map<Long, UserAccountSnapshot> accounts = userAccountReadPort.findByIds(authorIds);
        Map<Long, AlbumSummary> albums = albumAssembler.byIds(teamId,
                posts.stream().map(ContentPost::getAlbumId).filter(id -> id != null).collect(Collectors.toSet()));
        Map<Long, Integer> likeCounts = activeLikeCounts(postIds);
        Set<Long> likedPostIds = likeMapper.selectLikedPostIds(viewerUserId, postIds).stream()
                .collect(Collectors.toSet());
        return posts.stream()
                .map(post -> new PostView(
                        post.getId(),
                        author(post.getAuthorUserId(), players, accounts),
                        post.getContent(),
                        post.getAlbumId() == null ? null : albums.get(post.getAlbumId()),
                        likeCounts.getOrDefault(post.getId(), 0),
                        likedPostIds.contains(post.getId()),
                        post.getCreatedAt()))
                .toList();
    }

    private PostAuthor author(Long userId, Map<Long, PlayerSnapshot> players,
                              Map<Long, UserAccountSnapshot> accounts) {
        PlayerSnapshot player = players.get(userId);
        UserAccountSnapshot account = accounts.get(userId);
        String name = player != null ? player.name()
                : account == null ? null : account.phoneMasked();
        return new PostAuthor(userId, name, player == null ? null : avatarUrlSigner.sign(player.avatarUrl()));
    }

    private Map<Long, Integer> activeLikeCounts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return likeMapper.selectActiveCounts(postIds).stream()
                .collect(Collectors.toMap(ContentPostLikeCountRow::getPostId,
                        ContentPostLikeCountRow::getLikeCount));
    }

    private void requireAlbum(Long teamId, Long albumId) {
        if (albumId == null) {
            return;
        }
        ContentAlbum album = albumMapper.selectOne(new LambdaQueryWrapper<ContentAlbum>()
                .eq(ContentAlbum::getId, albumId)
                .eq(ContentAlbum::getTeamId, teamId));
        if (album == null) {
            throw ContentRules.validationError("albumId", "相册不存在或不属于本队");
        }
    }

    private ContentPost requirePost(Long teamId, Long postId) {
        ContentPost post = postMapper.selectOne(new LambdaQueryWrapper<ContentPost>()
                .eq(ContentPost::getId, postId)
                .eq(ContentPost::getTeamId, teamId));
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
        }
        return post;
    }
}
