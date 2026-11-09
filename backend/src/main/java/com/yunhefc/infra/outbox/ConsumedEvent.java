package com.yunhefc.infra.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("consumed_event")
public class ConsumedEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String consumerGroup;
    private String eventId;
    private String topic;
    private Integer partitionNo;
    private Long offsetNo;
    private LocalDateTime processedAt;
    @TableLogic
    private Integer isDeleted;
}
