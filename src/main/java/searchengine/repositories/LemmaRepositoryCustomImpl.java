package searchengine.repositories;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LemmaRepositoryCustomImpl implements LemmaRepositoryCustom {
    Logger logger = LoggerFactory.getLogger(LemmaRepositoryCustomImpl.class);
    @PersistenceContext
    private EntityManager entityManager;
    private final int MAX_QUERY_LENGTH = 16000;
    private final int MAX_COUNT_INSERT = 10;

    @Override
    public void saveLemmas(Set<String> lemmas, SiteEntity site) {
        String lemmaSQL = """
                INSERT INTO lemma(frequency,lemma,site_id) VALUES <query>
                 ON DUPLICATE KEY UPDATE frequency = frequency + 1
                """;
        String lemmaSelectTemplate = "(1,'<lemma>'," + site.getId() + ")";
        StringBuilder lemmaSelect = new StringBuilder();

        lemmas.forEach(l -> lemmaSelect.append(lemmaSelect.isEmpty() ? "" : "\n,")
                .append(lemmaSelectTemplate.replace("<lemma>", l)));

        String queryString = lemmaSQL.replace("<query>", lemmaSelect);
        Query query = entityManager.createNativeQuery(queryString);

        query.executeUpdate();
    }

    @Override
    public void saveLemmasByProcedure(String queryText) {
        String callSQL = "call mass_insert_with_deadlock_check('<query>')";
        String queryString = callSQL.replace("<query>", queryText);
        Query query = entityManager.createNativeQuery(queryString);
        Object result = query.getSingleResult();
        logger.info(result.toString());
    }

    @Override
    public List<String> getSaveLemmasQueriesSingleInsert(Map<String, Integer> lemmas, PageEntity page) {
        List<String> queryList = new ArrayList<>();
        if (lemmas == null || lemmas.isEmpty()) {
            return queryList;
        }

        String lemmaIndexSQL = "INSERT INTO lemma(frequency,lemma,site_id) VALUES (1,''<lemma>''," +
                page.getSite().getId() + ") ON DUPLICATE KEY UPDATE frequency = frequency + 1;";

        StringBuilder lemmaIndexQuery = new StringBuilder();
        for (Map.Entry<String, Integer> lemma : lemmas.entrySet()) {
            StringBuilder tempQuery = new StringBuilder();
            tempQuery.append(lemmaIndexQuery.isEmpty() ? "" : "\n")
                    .append(lemmaIndexSQL.replace("<lemma>", lemma.getKey()));

            int queryLength = lemmaIndexQuery.length() + tempQuery.length();
            if (queryLength > MAX_QUERY_LENGTH) {
                queryList.add(lemmaIndexQuery.toString());
                lemmaIndexQuery = new StringBuilder();
            }

            lemmaIndexQuery.append(tempQuery);
        }
        queryList.add(lemmaIndexQuery.toString());

        return queryList;
    }

    @Override
    public List<String> getSaveLemmasQueriesMasInsert(Set<String> lemmas, SiteEntity site) {
        List<String> queryList = new ArrayList<>();
        if (lemmas == null || lemmas.isEmpty()) {
            return queryList;
        }

        String lemmaSQL = """
                INSERT INTO lemma(frequency,lemma,site_id) VALUES <query>
                 ON DUPLICATE KEY UPDATE frequency = frequency + 1;
                """;
        String lemmaSelectTemplate = "(1,''<lemma>''," + site.getId() + ")";

        return getQueryListForMassInsert(lemmas, lemmaSQL, lemmaSelectTemplate);
    }

    private List<String> getQueryListForMassInsert(Set<String> lemmas, String lemmaSQL, String lemmaSelectTemplate) {
        List<String> queryList = new ArrayList<>();

        StringBuilder lemmaValues = new StringBuilder();
        StringBuilder lemmaInsert = new StringBuilder();
        int currentCountInsert = 0;
        for (String lemma : lemmas) {
            StringBuilder tempQuery = new StringBuilder();
            tempQuery.append(lemmaValues.isEmpty() ? "" : "\n,")
                    .append(lemmaSelectTemplate.replace("<lemma>", lemma));

            int queryLength = tempQuery.length() + lemmaValues.length() + lemmaInsert.length();
            if (queryLength > MAX_QUERY_LENGTH) {
                queryList.add(lemmaInsert.toString());
                lemmaInsert = new StringBuilder();
            }

            lemmaValues.append(tempQuery);
            currentCountInsert++;

            if (currentCountInsert == MAX_COUNT_INSERT) {
                lemmaInsert.append(lemmaSQL.replace("<query>", lemmaValues));
                lemmaValues = new StringBuilder();
                currentCountInsert = 0;
            }
        }

        if (!lemmaValues.isEmpty()) {
            lemmaInsert.append(lemmaSQL.replace("<query>", lemmaValues));
        }
        if (!lemmaInsert.isEmpty()) {
            queryList.add(lemmaInsert.toString());
        }

        return queryList;
    }

}
