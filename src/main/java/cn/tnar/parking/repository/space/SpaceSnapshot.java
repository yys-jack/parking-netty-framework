package cn.tnar.parking.repository.space;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 车位状态快照实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceSnapshot {
    private Long id;
    private String parkCode;
    private Integer totalSpaces;
    private Integer remainedSpaces;
    private BigDecimal occupancyRate;
    private Instant spaceTime;
    private Instant createTime;
}
