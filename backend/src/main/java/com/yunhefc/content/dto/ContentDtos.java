package com.yunhefc.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yunhefc.common.api.PresenceTrackedRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public final class ContentDtos {

    private ContentDtos() {
    }

    public record AlbumSummary(
            Long id,
            String title,
            Long matchId,
            Long activityId,
            String ownerName,
            String coverThumbnailUrl,
            int photoCount,
            Long createdBy,
            LocalDateTime createdAt) {
    }

    public record AlbumDetail(
            Long id,
            String title,
            Long matchId,
            Long activityId,
            String ownerName,
            String coverThumbnailUrl,
            String coverUrl,
            int photoCount,
            Long createdBy,
            LocalDateTime createdAt) {
    }

    public record AlbumPhotoView(
            Long id,
            String thumbnailUrl,
            String originalUrl,
            Long uploadedBy,
            String uploaderName,
            LocalDateTime createdAt) {
    }

    public record AlbumCreateRequest(
            @NotBlank @Size(max = 64) String title,
            Long matchId,
            Long activityId) {

        /** 与 05 CNT-04 一致：长度按去除首尾空白后的名称校验。 */
        public AlbumCreateRequest {
            title = title == null ? null : title.strip();
        }
    }

    /**
     * 部分更新：未出现的字段保持原值；归属显式传 null 表示清空，
     * 切换归属时须同时把另一侧显式置空，否则按「至多一个非空」返回 40001。
     */
    @Getter
    @Setter
    public static class AlbumUpdateRequest extends PresenceTrackedRequest {

        @Size(max = 64)
        private String title;

        private Long coverPhotoId;

        private Long matchId;

        private Long activityId;

        /** 先去除首尾空白再进入 @Size 校验，与 05 CNT-05 的长度口径一致。 */
        @JsonProperty("title")
        public void setTitle(String title) {
            markPresent("title");
            this.title = title == null ? null : title.strip();
        }

        @JsonProperty("coverPhotoId")
        public void setCoverPhotoId(Long coverPhotoId) {
            markPresent("coverPhotoId");
            this.coverPhotoId = coverPhotoId;
        }

        @JsonProperty("matchId")
        public void setMatchId(Long matchId) {
            markPresent("matchId");
            this.matchId = matchId;
        }

        @JsonProperty("activityId")
        public void setActivityId(Long activityId) {
            markPresent("activityId");
            this.activityId = activityId;
        }

        @JsonIgnore
        public boolean hasTitle() {
            return getPresentFields().contains("title");
        }

        @JsonIgnore
        public boolean hasCoverPhotoId() {
            return getPresentFields().contains("coverPhotoId");
        }

        @JsonIgnore
        public boolean hasMatchId() {
            return getPresentFields().contains("matchId");
        }

        @JsonIgnore
        public boolean hasActivityId() {
            return getPresentFields().contains("activityId");
        }
    }

    public record AlbumPhotoUploadView(AlbumPhotoView photo, int photoCount) {
    }

    public record AlbumDeleteResult(boolean deleted, int deletedPhotos) {
    }

    public record AlbumPhotoDeleteResult(boolean deleted, boolean coverReset) {
    }
}
