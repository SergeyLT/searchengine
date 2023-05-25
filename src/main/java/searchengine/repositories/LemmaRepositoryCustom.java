package searchengine.repositories;

import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface LemmaRepositoryCustom {
    @Transactional
    void saveLemmas(Set<String> lemmas, SiteEntity site);

    @Transactional
    void saveLemmasByProcedure(String queryText);

    List<String> getSaveLemmasQueriesSingleInsert(Map<String, Integer> lemmas, PageEntity page);

    List<String> getSaveLemmasQueriesMasInsert(Set<String> lemmas, SiteEntity site);
}
