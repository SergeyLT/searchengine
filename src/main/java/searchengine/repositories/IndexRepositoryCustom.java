package searchengine.repositories;

import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;

import java.util.Map;

public interface IndexRepositoryCustom {
    @Transactional
    void saveIndices(Map<String, Integer> lemmas, PageEntity page);
}
