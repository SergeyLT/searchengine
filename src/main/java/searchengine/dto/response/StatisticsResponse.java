package searchengine.dto.response;

import lombok.Data;
import searchengine.data.statistics.StatisticsData;

@Data
public class StatisticsResponse {
    private boolean result;
    private StatisticsData statistics;
}
