package com.warehouse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.warehouse.entity.SyncProcessedLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SyncProcessedLogMapper extends BaseMapper<SyncProcessedLog> {

    @Select("SELECT * FROM sync_processed_log " +
            "WHERE client_id = #{clientId} AND local_id = #{localId} LIMIT 1")
    SyncProcessedLog selectByClientIdAndLocalId(@Param("clientId") String clientId,
                                                @Param("localId") Long localId);
}
