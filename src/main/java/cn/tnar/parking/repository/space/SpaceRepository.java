package cn.tnar.parking.repository.space;

import java.util.List;
import java.util.Optional;

/**
 * 车位快照数据访问接口
 */
public interface SpaceRepository {
    /**
     * 保存车位快照
     * @param parkCode 停车场编码
     * @param total 总车位数
     * @param remained 剩余车位数
     * @param spaceTime 设备上报时间戳 (毫秒)
     */
    void saveSpaceSnapshot(String parkCode, int total, int remained, long spaceTime);

    /**
     * 查询停车场最新快照
     * @param parkCode 停车场编码
     * @return 最新快照，不存在则返回空
     */
    Optional<SpaceSnapshot> findLatestByParkCode(String parkCode);

    /**
     * 按时间范围查询快照
     * @param parkCode 停车场编码
     * @param startTime 开始时间戳 (毫秒)
     * @param endTime 结束时间戳 (毫秒)
     * @return 快照列表，按时间升序
     */
    List<SpaceSnapshot> findByParkCodeAndTimeRange(String parkCode, long startTime, long endTime);
}
