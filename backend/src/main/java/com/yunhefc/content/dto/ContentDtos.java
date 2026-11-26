package com.yunhefc.content.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

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
    }

    /**
     * 部分更新：未出现的字段保持原值；归属显式传 null 表示清空，切换归属时自动清空另一侧。
     */
    @Getter
    @Setter
    public static class AlbumUpdateRequest {

        @Size(max = 64)
        private String title;

        private Long coverPhotoId;

        private Long matchId;

        private Long activityId;

        @JsonIgnore
        private final Set<String> presentFields = new LinkedHashSet<>();

        @JsonIgnore
        private final Set<String> unknownFields = new LinkedHashSet<>();

        /**
         * 只读幂等指纹：让请求哈希区分「显式 null」与字段缺失及未知字段，同时不暴露可写属性。
         */
        @JsonProperty(value = "_presence", access = JsonProperty.Access.READ_ONLY)
        public String presenceFingerprint() {
            return new TreeSet<>(presentFields) + "|" + new TreeSet<>(unknownFields);
        }

        @JsonProperty("title")
        public void setTitle(String title) {
            presentFields.add("title");
            this.title = title;
        }

        @JsonProperty("coverPhotoId")
        public void setCoverPhotoId(Long coverPhotoId) {
            presentFields.add("coverPhotoId");
            this.coverPhotoId = coverPhotoId;
        }

        @JsonProperty("matchId")
        public void setMatchId(Long matchId) {
            presentFields.add("matchId");
            this.matchId = matchId;
        }

        @JsonProperty("activityId")
        public void setActivityId(Long activityId) {
            presentFields.add("activityId");
            this.activityId = activityId;
        }

        @JsonAnySetter
        public void addUnknownField(String field, JsonNode value) {
            unknownFields.add(field);
        }

        @JsonIgnore
        public boolean hasTitle() {
            return presentFields.contains("title");
        }

        @JsonIgnore
        public boolean hasCoverPhotoId() {
            return presentFields.contains("coverPhotoId");
        }

        @JsonIgnore
        public boolean hasMatchId() {
            return presentFields.contains("matchId");
        }

        @JsonIgnore
        public boolean hasActivityId() {
            return presentFields.contains("activityId");
        }
    }

    public record AlbumPhotoUploadView(AlbumPhotoView photo, int photoCount) {
    }

    public record AlbumDeleteResult(boolean deleted, int deletedPhotos) {
    }

    public record AlbumPhotoDeleteResult(boolean deleted, boolean coverReset) {
    }
}
