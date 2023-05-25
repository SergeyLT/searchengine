package searchengine.repositories;

import searchengine.model.PageEntity;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Map;

public class IndexRepositoryCustomImpl implements IndexRepositoryCustom{
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void saveIndices(Map<String, Integer> lemmas, PageEntity page) {
        String indexSQL = """
                INSERT INTO search_index(rating, lemma_id, page_id)
                SELECT t.rating, l.id, t.page_id
                FROM (
                <query>
                ) t
                JOIN lemma l ON l.site_id = t.site_id and l.lemma = t.lemma
                """;
        String indexSelectTemplate = "select '<lemma>' lemma, " + page.getId() + " page_id, "
                + page.getSite().getId() + " site_id, <rating> rating";
        StringBuilder indexSelect = new StringBuilder();

        lemmas.entrySet().forEach(l -> {
            indexSelect.append(indexSelect.isEmpty() ? "" : " union\n")
                    .append(indexSelectTemplate.replace("<lemma>", l.getKey())
                            .replace("<rating>", l.getValue().toString()));
        });

        String queryString = indexSQL.replace("<query>", indexSelect);
        Query query = entityManager.createNativeQuery(queryString);
        query.executeUpdate();
    }
}
