package com.yunhefc.infra.storage;

import com.yunhefc.common.error.BusinessException;
import com.yunhefc.common.error.ErrorCode;
import com.yunhefc.common.util.Ulid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片上传 module：读取、体积、格式嗅探、对象键、OSS 写入与签名收在 implementation；
 * 调用方只提供 ImagePolicy、owner 用户 ID 与后续领域动作。
 */
@Component
@RequiredArgsConstructor
public class ImageStore {

    private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

    private final OssGateway ossGateway;
    private final ObjectRemoval objectRemoval;

    public StoredImage store(MultipartFile file, ImagePolicy policy, long ownerUserId) {
        byte[] content = read(file);
        if (content.length > policy.maxBytes()) {
            throw ImageSniffer.invalidFile(policy.tooLargeMessage());
        }
        ImageSniffer.Format format = ImageSniffer.sniff(content);
        String objectKey = policy.prefix() + "/" + ownerUserId + "/" + Ulid.generate()
                + "." + format.extension();
        try {
            ossGateway.putObject(objectKey, content, format.contentType());
        } catch (RuntimeException ex) {
            log.warn("image upload failed, prefix={}, objectKey={}", policy.prefix(), objectKey, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new StoredImage(objectKey, ossGateway.signedUrl(objectKey), objectRemoval);
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
