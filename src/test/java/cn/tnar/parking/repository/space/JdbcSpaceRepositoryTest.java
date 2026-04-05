package cn.tnar.parking.repository.space;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JdbcSpaceRepository 集成测试
 */
@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class JdbcSpaceRepositoryTest {

    @Autowired
    private JdbcSpaceRepository spaceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 清空测试数据
        jdbcTemplate.update("DELETE FROM space_snapshot");
    }

    @Test
    void saveSpaceSnapshot_savesDataCorrectly() {
        // Given
        String parkCode = "PARK001";
        int total = 100;
        int remained = 60;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        SpaceSnapshot snapshot = saved.get();
        assertEquals(parkCode, snapshot.getParkCode());
        assertEquals(total, snapshot.getTotalSpaces());
        assertEquals(remained, snapshot.getRemainedSpaces());
        assertEquals(new BigDecimal("40.00"), snapshot.getOccupancyRate());
    }

    @Test
    void saveSpaceSnapshot_calculatesOccupancyRateCorrectly() {
        // Given
        String parkCode = "PARK002";
        int total = 200;
        int remained = 50;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        // (200-50)/200 * 100 = 75.00
        assertEquals(new BigDecimal("75.00"), saved.get().getOccupancyRate());
    }

    @Test
    void findLatestByParkCode_returnsEmptyWhenNotFound() {
        // When
        Optional<SpaceSnapshot> result = spaceRepository.findLatestByParkCode("NON_EXISTENT");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void findLatestByParkCode_returnsLatestSnapshot() {
        // Given
        String parkCode = "PARK003";
        long now = System.currentTimeMillis();

        spaceRepository.saveSpaceSnapshot(parkCode, 100, 80, now - 10000);
        spaceRepository.saveSpaceSnapshot(parkCode, 100, 50, now);

        // When
        Optional<SpaceSnapshot> result = spaceRepository.findLatestByParkCode(parkCode);

        // Then
        assertTrue(result.isPresent());
        assertEquals(50, result.get().getRemainedSpaces());
    }

    @Test
    void findByParkCodeAndTimeRange_returnsFilteredResults() {
        // Given
        String parkCode = "PARK004";
        long startTime = System.currentTimeMillis();

        spaceRepository.saveSpaceSnapshot(parkCode, 100, 90, startTime);
        spaceRepository.saveSpaceSnapshot(parkCode, 100, 70, startTime + 5000);
        spaceRepository.saveSpaceSnapshot(parkCode, 100, 50, startTime + 10000);

        // When - 查询前 5 秒的数据
        List<SpaceSnapshot> results = spaceRepository.findByParkCodeAndTimeRange(
            parkCode,
            startTime,
            startTime + 6000
        );

        // Then
        assertEquals(2, results.size());
        assertEquals(90, results.get(0).getRemainedSpaces());
        assertEquals(70, results.get(1).getRemainedSpaces());
    }

    @Test
    void findByParkCodeAndTimeRange_returnsEmptyWhenNoDataInRange() {
        // Given
        String parkCode = "PARK005";
        long now = System.currentTimeMillis();

        spaceRepository.saveSpaceSnapshot(parkCode, 100, 80, now - 100000);

        // When - 查询未来时间范围
        List<SpaceSnapshot> results = spaceRepository.findByParkCodeAndTimeRange(
            parkCode,
            now,
            now + 10000
        );

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    void saveSpaceSnapshot_zeroOccupancyRate() {
        // Given - 剩余车位等于总车位，占用率为 0%
        String parkCode = "PARK_ZERO";
        int total = 100;
        int remained = 100;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        assertEquals(new BigDecimal("0.00"), saved.get().getOccupancyRate());
    }

    @Test
    void saveSpaceSnapshot_fullOccupancyRate() {
        // Given - 剩余车位为 0，占用率为 100%
        String parkCode = "PARK_FULL";
        int total = 100;
        int remained = 0;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        assertEquals(new BigDecimal("100.00"), saved.get().getOccupancyRate());
    }

    @Test
    void saveSpaceSnapshot_fractionalOccupancyRate() {
        // Given - 33.33% 占用率
        String parkCode = "PARK_FRACTIONAL";
        int total = 300;
        int remained = 200;
        long spaceTime = System.currentTimeMillis();

        // When
        spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);

        // Then
        Optional<SpaceSnapshot> saved = spaceRepository.findLatestByParkCode(parkCode);
        assertTrue(saved.isPresent());
        assertEquals(new BigDecimal("33.33"), saved.get().getOccupancyRate());
    }

    @Test
    void saveSpaceSnapshot_throwsWhenParkCodeEmpty() {
        // Given
        String parkCode = "";
        int total = 100;
        int remained = 50;
        long spaceTime = System.currentTimeMillis();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);
        });
    }

    @Test
    void saveSpaceSnapshot_throwsWhenParkCodeNull() {
        // Given
        String parkCode = null;
        int total = 100;
        int remained = 50;
        long spaceTime = System.currentTimeMillis();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);
        });
    }

    @Test
    void saveSpaceSnapshot_throwsWhenTotalZero() {
        // Given
        String parkCode = "PARK001";
        int total = 0;
        int remained = 0;
        long spaceTime = System.currentTimeMillis();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);
        });
    }

    @Test
    void saveSpaceSnapshot_throwsWhenRemainedNegative() {
        // Given
        String parkCode = "PARK001";
        int total = 100;
        int remained = -1;
        long spaceTime = System.currentTimeMillis();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);
        });
    }

    @Test
    void saveSpaceSnapshot_throwsWhenRemainedExceedsTotal() {
        // Given
        String parkCode = "PARK001";
        int total = 100;
        int remained = 150;
        long spaceTime = System.currentTimeMillis();

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            spaceRepository.saveSpaceSnapshot(parkCode, total, remained, spaceTime);
        });
    }
}
