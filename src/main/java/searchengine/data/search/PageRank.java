package searchengine.data.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import searchengine.model.PageEntity;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageRank {
    private PageEntity page;
    private double rank;
}
