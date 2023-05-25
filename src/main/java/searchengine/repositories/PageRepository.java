package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
@Transactional
public interface PageRepository  extends JpaRepository<PageEntity, Integer> {
    PageEntity findByPathAndSite(String path, SiteEntity site);

    @Query("SELECT p FROM PageEntity p WHERE p.path IN (:links)")
    List<PageEntity> findPagesByPathList(@Param("links") List<String> links);

    int countBySite(SiteEntity site);

    @Query(value = "DELETE PageEntity p")
    @Modifying
    int deleteAllPages();
}
