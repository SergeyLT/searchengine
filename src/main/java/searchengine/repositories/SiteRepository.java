package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SiteEntity;
import searchengine.data.siteindexing.SiteIndexingStatus;

import java.util.List;

@Repository
@Transactional
public interface SiteRepository extends JpaRepository<SiteEntity, Integer> {
    @Query(value = """
        UPDATE SiteEntity SET
        status = :status
        ,statusTime = now()
        ,lastError = :lastError
        WHERE status = 'INDEXING'
    """)
    @Modifying
    int updateStoppedIndexingSite(@Param("status") SiteIndexingStatus status, @Param("lastError") String lastError);

    SiteEntity findByUrl(String url);

    @Query("SELECT s FROM SiteEntity s WHERE s.url IN (:links)")
    List<SiteEntity> findSitesByURLList(@Param("links") List<String> links);

    @Query("SELECT s FROM SiteEntity s WHERE (s.url = :url or 1 = :searchAll) and s.status != 'INDEXING'")
    List<SiteEntity> findIndexedSites(@Param("url") String ulr, @Param("searchAll") int searchAll);

    @Query(value = "DELETE SiteEntity s")
    @Modifying
    int deleteAllSites();

    @Query("SELECT s.status FROM SiteEntity s")
    List<SiteIndexingStatus> getAllStatus();
}
