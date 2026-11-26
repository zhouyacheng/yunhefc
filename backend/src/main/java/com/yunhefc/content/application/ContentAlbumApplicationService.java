package com.yunhefc.content.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunhefc.common.api.PageQuery;
import com.yunhefc.common.api.PageResponse;
import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.security.CurrentUser;
import com.yunhefc.common.util.Ulid;
import com.yunhefc.contracts.activity.ActivityReadPort;
import com.yunhefc.contracts.match.MatchSummaryReadPort;
import com.yunhefc.contracts.player.PlayerReadPort;
import com.yunhefc.contracts.player.PlayerSnapshot;
import com.yunhefc.contracts.security.AuthPrincipal;
import com.yunhefc.content.domain.ContentAlbum;
import com.yunhefc.content.domain.ContentAlbumPhoto;
import com.yunhefc.content.domain.ContentObjectKey;
import com.yunhefc.content.domain.ContentRules;
import com.yunhefc.content.dto.ContentDtos.AlbumCreateRequest;
import com.yunhefc.content.dto.ContentDtos.AlbumDeleteResult;
import com.yunhefc.content.dto.ContentDtos.AlbumDetail;
import com.yunhefc.content.dto.ContentDtos.AlbumPhotoDeleteResult;
import com.yunhefc.content.dto.ContentDtos.AlbumPhotoUploadView;
import com.yunhefc.content.dto.ContentDtos.AlbumPhotoView;
import com.yunhefc.content.dto.ContentDtos.AlbumSummary;
import com.yunhefc.content.dto.ContentDtos.AlbumUpdateRequest;
import com.yunhefc.content.infrastructure.ContentAlbumMapper;
import com.yunhefc.content.infrastructure.ContentAlbumPhotoMapper;
import com.yunhefc.content.infrastructure.ContentObjectDeletion;
import com.yunhefc.infra.storage.ImageSniffer;
import com.yunhefc.infra.storage.OssGateway;
import com.yunhefc.infra.storage.UploadLimits;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * C 端相册与照片：CNT-01~08。归属校验经 contracts 只读端口；不直接访问比赛/活动表。
 */
@Service
@RequiredArgsConstructor
public class ContentAlbumApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ContentAlbumApplicationService.class);
    private static final String PERMISSION_DELETE_ANY = "content:delete:any";
    private static final String PHOTO_TOO_LARGE = "照片不能超过 5MB";
    /** CNT-03 照片分页白名单；与相册列表的 10/20/50/100 不同。 */
    private static final Set<Long> PHOTO_PAGE_SIZES = Set.of(30L, 60L, 100L);

    private final ContentAlbumMapper albumMapper;
    private final ContentAlbumPhotoMapper photoMapper;
    private final ContentAlbumAssembler albumAssembler;
    private final MatchSummaryReadPort matchSummaryReadPort;
    private final ActivityReadPort activityReadPort;
    private final PlayerReadPort playerReadPort;
    private final OssGateway ossGateway;
    private final ContentObjectDeletion objectDeletion;
    private final TransactionTemplate transactionTemplate;

    @Transactional(readOnly = true)
    public PageResponse<AlbumSummary> list(Long matchId, Long activityId, long page, long pageSize) {
        Long teamId = CurrentUser.principal().teamId();
        PageQuery pageQuery = PageQuery.of(page, pageSize);
        Page<ContentAlbum> result = albumMapper.selectPage(
                new Page<>(pageQuery.page(), pageQuery.pageSize()),
                new LambdaQueryWrapper<ContentAlbum>()
                        .eq(ContentAlbum::getTeamId, teamId)
                        .eq(matchId != null, ContentAlbum::getMatchId, matchId)
                        .eq(activityId != null, ContentAlbum::getActivityId, activityId)
                        .orderByDesc(ContentAlbum::getCreatedAt)
                        .orderByDesc(ContentAlbum::getId));
        return PageResponse.of(albumAssembler.summaries(teamId, result.getRecords()),
                result.getTotal(), pageQuery.page(), pageQuery.pageSize());
    }

    @Transactional(readOnly = true)
    public AlbumDetail detail(Long albumId) {
        Long teamId = CurrentUser.principal().teamId();
        return albumAssembler.detail(teamId, requireAlbum(teamId, albumId));
    }

    @Transactional(readOnly = true)
    public PageResponse<AlbumPhotoView> photos(Long albumId, long page, long pageSize) {
        Long teamId = CurrentUser.principal().teamId();
        requireAlbum(teamId, albumId);
        if (page < 1 || !PHOTO_PAGE_SIZES.contains(pageSize)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分页参数不合法");
        }
        Page<ContentAlbumPhoto> result = photoMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<ContentAlbumPhoto>()
                        .eq(ContentAlbumPhoto::getAlbumId, albumId)
                        .orderByAsc(ContentAlbumPhoto::getId));
        return PageResponse.of(photoViews(result.getRecords()), result.getTotal(), page, pageSize);
    }

    @Transactional
    public AlbumDetail create(AlbumCreateRequest request) {
        Long teamId = CurrentUser.principal().teamId();
        ContentRules.requireAtMostOneOwner(request.matchId(), request.activityId());
        String title = ContentRules.requireTitle(request.title());
        requireOwnersInTeam(teamId, request.matchId(), request.activityId());

        ContentAlbum album = new ContentAlbum();
        album.setTeamId(teamId);
        album.setTitle(title);
        album.setMatchId(request.matchId());
        album.setActivityId(request.activityId());
        album.setCreatedBy(CurrentUser.principal().userId());
        albumMapper.insert(album);
        log.info("content album created, albumId={}, teamId={}", album.getId(), teamId);
        return detail(album.getId());
    }

    /**
     * 归属按合并后的状态校验：比赛与活动至多一个非空，切换时须显式清空另一侧。
     */
    @Transactional
    public AlbumDetail update(Long albumId, AlbumUpdateRequest request) {
        request.rejectUnknownFields();
        Long teamId = CurrentUser.principal().teamId();
        ContentAlbum album = requireAlbumForUpdate(teamId, albumId);

        String title = request.hasTitle() ? ContentRules.requireTitle(request.getTitle()) : album.getTitle();
        Long matchId = request.hasMatchId() ? request.getMatchId() : album.getMatchId();
        Long activityId = request.hasActivityId() ? request.getActivityId() : album.getActivityId();
        ContentRules.requireAtMostOneOwner(matchId, activityId);
        requireOwnersInTeam(teamId, matchId, activityId);

        Long coverPhotoId = album.getCoverPhotoId();
        if (request.hasCoverPhotoId()) {
            coverPhotoId = request.getCoverPhotoId();
            if (coverPhotoId != null && !photoInAlbum(albumId, coverPhotoId)) {
                throw ContentRules.validationError("coverPhotoId", "封面照片不属于该相册");
            }
        }

        albumMapper.update(null, new LambdaUpdateWrapper<ContentAlbum>()
                .eq(ContentAlbum::getId, albumId)
                .eq(ContentAlbum::getTeamId, teamId)
                .set(ContentAlbum::getTitle, title)
                .set(ContentAlbum::getMatchId, matchId)
                .set(ContentAlbum::getActivityId, activityId)
                .set(ContentAlbum::getCoverPhotoId, coverPhotoId));
        log.info("content album updated, albumId={}, teamId={}", albumId, teamId);
        return detail(albumId);
    }

    @Transactional
    public AlbumDeleteResult delete(Long albumId) {
        Long teamId = CurrentUser.principal().teamId();
        requireAlbumForUpdate(teamId, albumId);
        List<ContentAlbumPhoto> photos = photoMapper.selectList(new LambdaQueryWrapper<ContentAlbumPhoto>()
                .eq(ContentAlbumPhoto::getAlbumId, albumId));
        albumMapper.deleteById(albumId);
        photoMapper.delete(new LambdaQueryWrapper<ContentAlbumPhoto>()
                .eq(ContentAlbumPhoto::getAlbumId, albumId));
        objectDeletion.deleteAll(photos.stream().map(ContentAlbumPhoto::getObjectKey).toList());
        log.info("content album deleted, albumId={}, teamId={}, photos={}", albumId, teamId, photos.size());
        return new AlbumDeleteResult(true, photos.size());
    }

    /**
     * 单张照片：OSS 上传在事务外完成，只有「锁定相册行 → 计数 → 落库」进事务，
     * 既保证并发上传不超过 200 张，也不在事务内做远程调用。
     */
    public AlbumPhotoUploadView uploadPhoto(Long albumId, MultipartFile file) {
        AuthPrincipal principal = CurrentUser.principal();
        requireAlbum(principal.teamId(), albumId);
        ContentRules.requirePhotoSlot(photoMapper.countActive(albumId));

        byte[] content = read(file);
        if (content.length > UploadLimits.CONTENT_PHOTO_MAX_BYTES) {
            throw ImageSniffer.invalidFile(PHOTO_TOO_LARGE);
        }
        ImageSniffer.Format format = ImageSniffer.sniff(content);
        String objectKey = ContentObjectKey.generate(
                principal.userId(), Ulid.generate(), format.extension());
        try {
            ossGateway.putObject(objectKey, content, format.contentType());
        } catch (RuntimeException ex) {
            log.warn("content photo upload failed, objectKey={}", objectKey, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        ContentAlbumPhoto saved;
        try {
            saved = transactionTemplate.execute(status -> insertPhoto(albumId, principal, objectKey));
        } catch (RuntimeException ex) {
            // 事务已结束（无活动同步），立即 best-effort 回收刚上传的对象。
            objectDeletion.deleteAll(List.of(objectKey));
            throw ex;
        }
        int photoCount = photoMapper.countActive(albumId);
        log.info("content photo uploaded, albumId={}, photoId={}, photoCount={}",
                albumId, saved.getId(), photoCount);
        return new AlbumPhotoUploadView(photoViews(List.of(saved)).get(0), photoCount);
    }

    /**
     * 事务内二次门禁：锁定相册行后重新计数，避免并发请求同时通过外层的快速检查。
     */
    private ContentAlbumPhoto insertPhoto(Long albumId, AuthPrincipal principal, String objectKey) {
        requireAlbumForUpdate(principal.teamId(), albumId);
        ContentRules.requirePhotoSlot(photoMapper.countActive(albumId));
        ContentAlbumPhoto photo = new ContentAlbumPhoto();
        photo.setAlbumId(albumId);
        photo.setObjectKey(objectKey);
        photo.setUploadedBy(principal.userId());
        photoMapper.insert(photo);
        return photoMapper.selectById(photo.getId());
    }

    @Transactional
    public AlbumPhotoDeleteResult deletePhoto(Long albumId, Long photoId) {
        AuthPrincipal principal = CurrentUser.principal();
        ContentAlbum album = requireAlbumForUpdate(principal.teamId(), albumId);
        ContentAlbumPhoto photo = photoMapper.selectOne(new LambdaQueryWrapper<ContentAlbumPhoto>()
                .eq(ContentAlbumPhoto::getId, photoId)
                .eq(ContentAlbumPhoto::getAlbumId, albumId));
        if (photo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "照片不存在");
        }
        boolean canDeleteAny = principal.permissions().contains(PERMISSION_DELETE_ANY);
        if (!photo.getUploadedBy().equals(principal.userId()) && !canDeleteAny) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除本人上传的照片");
        }

        // 有效封面包含「未显式设置时取最早照片」的情形，删除它同样视为封面重置。
        ContentAlbumPhoto cover = albumAssembler.coverPhoto(albumId);
        boolean coverReset = cover != null && photoId.equals(cover.getId());
        photoMapper.deleteById(photoId);
        if (coverReset) {
            albumMapper.update(null, new LambdaUpdateWrapper<ContentAlbum>()
                    .eq(ContentAlbum::getId, albumId)
                    .eq(ContentAlbum::getTeamId, principal.teamId())
                    .set(ContentAlbum::getCoverPhotoId, null));
        }
        objectDeletion.deleteAll(List.of(photo.getObjectKey()));
        log.info("content photo deleted, albumId={}, photoId={}, coverReset={}",
                albumId, photoId, coverReset);
        return new AlbumPhotoDeleteResult(true, coverReset);
    }

    private List<AlbumPhotoView> photoViews(List<ContentAlbumPhoto> photos) {
        Map<Long, String> names = uploaderNames(
                photos.stream().map(ContentAlbumPhoto::getUploadedBy).distinct().toList());
        return photos.stream()
                .map(photo -> new AlbumPhotoView(
                        photo.getId(),
                        ossGateway.signedUrl(photo.getObjectKey(), ContentRules.THUMBNAIL_PROCESS),
                        ossGateway.signedUrl(photo.getObjectKey()),
                        photo.getUploadedBy(),
                        names.get(photo.getUploadedBy()),
                        photo.getCreatedAt()))
                .toList();
    }

    private Map<Long, String> uploaderNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return playerReadPort.findByUserIds(userIds).stream()
                .collect(Collectors.toMap(PlayerSnapshot::userId, PlayerSnapshot::name, (left, right) -> left));
    }

    private void requireOwnersInTeam(Long teamId, Long matchId, Long activityId) {
        if (matchId != null && matchSummaryReadPort.findSummary(matchId, teamId).isEmpty()) {
            throw ContentRules.validationError("matchId", "比赛不存在或不属于本队");
        }
        if (activityId != null && activityReadPort.findById(teamId, activityId).isEmpty()) {
            throw ContentRules.validationError("activityId", "活动不存在或不属于本队");
        }
    }

    private boolean photoInAlbum(Long albumId, Long photoId) {
        return photoMapper.selectCount(new LambdaQueryWrapper<ContentAlbumPhoto>()
                .eq(ContentAlbumPhoto::getId, photoId)
                .eq(ContentAlbumPhoto::getAlbumId, albumId)) > 0;
    }

    private ContentAlbum requireAlbum(Long teamId, Long albumId) {
        ContentAlbum album = albumMapper.selectOne(new LambdaQueryWrapper<ContentAlbum>()
                .eq(ContentAlbum::getId, albumId)
                .eq(ContentAlbum::getTeamId, teamId));
        if (album == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "相册不存在");
        }
        return album;
    }

    private ContentAlbum requireAlbumForUpdate(Long teamId, Long albumId) {
        ContentAlbum album = albumMapper.selectByIdForUpdate(albumId, teamId);
        if (album == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "相册不存在");
        }
        return album;
    }

    private byte[] read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ImageSniffer.invalidFile(ImageSniffer.EMPTY_MESSAGE);
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ImageSniffer.invalidFile(ImageSniffer.CORRUPT_MESSAGE);
        }
    }
}
