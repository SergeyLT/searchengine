package searchengine.repositories;

import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

public interface LemmaRepositoryCustom {
    @Transactional
    void saveLemmas(Set<String> lemmas, SiteEntity site);

    @Transactional
    void saveLemmasByProcedure(String queryText);

    List<String> getSaveLemmasQueriesMasInsert(Set<String> lemmas, SiteEntity site);
}
