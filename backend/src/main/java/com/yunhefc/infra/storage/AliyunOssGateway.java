package com.yunhefc.infra.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Date;

public class AliyunOssGateway implements OssGateway {

    private static final long SIGNED_URL_TTL_SECONDS = 15 * 60;

    private final OSS client;
    private final String bucket;

    public AliyunOssGateway(OssProperties properties) {
        this.client = new OSSClientBuilder().build(
                properties.getEndpoint(), properties.getAccessKeyId(), properties.getAccessKeySecret());
        this.bucket = properties.getBucket();
    }

    @Override
    public void putObject(String objectKey, byte[] content, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        client.putObject(bucket, objectKey, new ByteArrayInputStream(content), metadata);
    }

    @Override
    public String signedUrl(String objectKey) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.GET);
        request.setExpiration(Date.from(Instant.now().plusSeconds(SIGNED_URL_TTL_SECONDS)));
        return client.generatePresignedUrl(request).toString();
    }

    @Override
    public boolean exists(String objectKey) {
        return client.doesObjectExist(bucket, objectKey);
    }

    @Override
    public byte[] getObject(String objectKey) {
        try (OSSObject object = client.getObject(bucket, objectKey);
             InputStream content = object.getObjectContent()) {
            return content.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void shutdown() {
        client.shutdown();
    }
}
