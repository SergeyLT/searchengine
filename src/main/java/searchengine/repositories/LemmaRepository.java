package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Set;

@Repository
@Transactional
public interface LemmaRepository extends JpaRepository<LemmaEntity, Integer>, LemmaRepositoryCustom {
    int countBySite(SiteEntity site);

    @Query("SELECT l FROM LemmaEntity l WHERE l.lemma IN (:lemmas) AND l.site IN (:sites) ORDER BY l.site, l.frequency")
    List<LemmaEntity> findLemmaByLemmasAndSites(@Param("lemmas") Set<String> lemmas
            , @Param("sites") List<SiteEntity> sites);

    @Query(value = "DELETE LemmaEntity l")
    @Modifying
    int deleteAllLemmas();

    @Query(value = "UPDATE LemmaEntity l SET l.frequency = l.frequency - 1 WHERE l.id IN (:idList)")
    @Modifying
    int decreaseFrequencyById(@Param("idList") List<Integer> idList);

    @Query(value = "DELETE LemmaEntity WHERE id IN (:idList) AND frequency <= 0")
    @Modifying
    int deleteZeroLemmas(@Param("idList") List<Integer> idList);
}
