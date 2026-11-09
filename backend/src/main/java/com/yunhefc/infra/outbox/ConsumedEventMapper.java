package com.yunhefc.infra.outbox;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ConsumedEventMapper {

    @Insert("""
            INSERT IGNORE INTO consumed_event
                (consumer_group, event_id, topic, partition_no, offset_no)
            VALUES (#{group}, #{eventId}, #{topic}, #{partition}, #{offset})
            """)
    int tryInsert(@Param("group") String group, @Param("eventId") String eventId,
                  @Param("topic") String topic, @Param("partition") int partition,
                  @Param("offset") long offset);
}
