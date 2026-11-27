package com.yunhefc.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunhefc.contracts.activity.ActivityReadPort;
import com.yunhefc.contracts.activity.ActivitySnapshot;
import com.yunhefc.contracts.match.MatchSummaryReadPort;
import com.yunhefc.contracts.match.MatchSummarySnapshot;
import com.yunhefc.content.domain.ContentAlbum;
import com.yunhefc.content.domain.ContentAlbumPhoto;
import com.yunhefc.content.domain.ContentRules;
import com.yunhefc.content.dto.ContentDtos.AlbumDetail;
import com.yunhefc.content.dto.ContentDtos.AlbumSummary;
import com.yunhefc.content.infrastructure.ContentAlbumMapper;
import com.yunhefc.content.infrastructure.ContentAlbumPhotoCountRow;
import com.yunhefc.content.infrastructure.ContentAlbumPhotoMapper;
import com.yunhefc.infra.storage.OssGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 相册摘要批量组装：相册列表与动态引用的相册共用，批量查询归属、封面与照片数，避免 N+1。
 */
@Component
@RequiredArgsConstructor
public class ContentAlbumAssembler {

    private final ContentAlbumMapper albumMapper;
    private final ContentAlbumPhotoMapper photoMapper;
    private final MatchSummaryReadPort matchSummaryReadPort;
    private final ActivityReadPort activityReadPort;
    private final OssGateway ossGateway;

    public List<AlbumSummary> summaries(Long teamId, List<ContentAlbum> albums) {
        if (albums.isEmpty()) {
            return List.of();
        }
        List<Long> albumIds = albums.stream().map(ContentAlbum::getId).toList();
        Map<Long, ContentAlbumPhoto> covers = coverPhotos(albumIds);
        Map<Long, Integer> counts = photoCounts(albumIds);
        Map<Long, MatchSummarySnapshot> matches = matchSummaryReadPort.findSummaries(
                albums.stream().map(ContentAlbum::getMatchId).filter(id -> id != null).collect(Collectors.toSet()),
                teamId);
        Map<Long, ActivitySnapshot> activities = activityReadPort.findByIds(teamId,
                albums.stream().map(ContentAlbum::getActivityId).filter(id -> id != null)
                        .collect(Collectors.toSet()));
        return albums.stream()
                .map(album -> {
                    ContentAlbumPhoto cover = covers.get(album.getId());
                    MatchSummarySnapshot match = album.getMatchId() == null
                            ? null : matches.get(album.getMatchId());
                    ActivitySnapshot activity = album.getActivityId() == null
                            ? null : activities.get(album.getActivityId());
                    return new AlbumSummary(
                            album.getId(),
                            album.getTitle(),
                            album.getMatchId(),
                            album.getActivityId(),
                            ownerName(match, activity),
                            thumbnailUrl(cover),
                            counts.getOrDefault(album.getId(), 0),
                            album.getCreatedBy(),
                            album.getCreatedAt());
                })
                .toList();
    }

    /**
     * 相册详情：摘要 + 封面原图；调用方已完成所属校验。
     */
    public AlbumDetail detail(Long teamId, ContentAlbum album) {
        ContentAlbumPhoto cover = coverPhoto(album.getId());
        return new AlbumDetail(
                album.getId(),
                album.getTitle(),
                album.getMatchId(),
                album.getActivityId(),
                ownerName(teamId, album),
                thumbnailUrl(cover),
                cover == null ? null : ossGateway.signedUrl(cover.getObjectKey()),
                photoMapper.countActive(album.getId()),
                album.getCreatedBy(),
                album.getCreatedAt());
    }

    /**
     * 按 ID 批量读取本队未删除相册摘要；已删除或跨队相册不出现在结果中。
     */
    public Map<Long, AlbumSummary> byIds(Long teamId, Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return Map.of();
        }
        List<ContentAlbum> albums = albumMapper.selectList(new LambdaQueryWrapper<ContentAlbum>()
                .eq(ContentAlbum::getTeamId, teamId)
                .in(ContentAlbum::getId, albumIds));
        return summaries(teamId, albums).stream()
                .collect(Collectors.toMap(AlbumSummary::id, Function.identity()));
    }

    /**
     * 有效封面：显式封面优先，未设置或已失效时取最早照片。
     */
    public ContentAlbumPhoto coverPhoto(Long albumId) {
        return coverPhotos(List.of(albumId)).get(albumId);
    }

    private String ownerName(Long teamId, ContentAlbum album) {
        MatchSummarySnapshot match = album.getMatchId() == null ? null
                : matchSummaryReadPort.findSummary(album.getMatchId(), teamId).orElse(null);
        ActivitySnapshot activity = album.getActivityId() == null ? null
                : activityReadPort.findById(teamId, album.getActivityId()).orElse(null);
        return ownerName(match, activity);
    }

    /** 归属摘要：比赛优先，其次活动；读取失败（对象已删/跨队）时为 null。 */
    private String ownerName(MatchSummarySnapshot match, ActivitySnapshot activity) {
        if (match != null) {
            return match.title();
        }
        return activity == null ? null : activity.title();
    }

    private String thumbnailUrl(ContentAlbumPhoto cover) {
        return cover == null ? null
                : ossGateway.signedUrl(cover.getObjectKey(), ContentRules.THUMBNAIL_PROCESS);
    }

    private Map<Long, ContentAlbumPhoto> coverPhotos(Collection<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Map.of();
        }
        return photoMapper.selectCoverPhotos(albumIds).stream()
                .collect(Collectors.toMap(ContentAlbumPhoto::getAlbumId, Function.identity()));
    }

    private Map<Long, Integer> photoCounts(Collection<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Map.of();
        }
        return photoMapper.selectActiveCounts(albumIds).stream()
                .collect(Collectors.toMap(ContentAlbumPhotoCountRow::getAlbumId,
                        ContentAlbumPhotoCountRow::getPhotoCount));
    }
}
