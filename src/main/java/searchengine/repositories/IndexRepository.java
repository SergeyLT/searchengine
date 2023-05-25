package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;
import searchengine.services.search.PageRankEntity;

import java.util.List;

@Repository
@Transactional
public interface IndexRepository extends JpaRepository<IndexEntity, Integer>, IndexRepositoryCustom{
    @Query(value = "DELETE IndexEntity i WHERE i.page = :page")
    @Modifying
    int deleteByPage(@Param("page") PageEntity page);

    @Query(value = "DELETE IndexEntity i")
    @Modifying
    int deleteAllIndices();

    @Query("""
            SELECT i.page.id
            FROM IndexEntity i
            WHERE i.lemma = :lemma
            AND (i.page.id IN (:pagesId) or 1 = :newPages)
            """)
    List<Integer> findPageIdByLemmaAndPages(@Param("lemma") LemmaEntity lemma
            ,@Param("pagesId") List<Integer> pagesId, @Param("newPages") int newPages);

    @Query("""
            SELECT new searchengine.services.search.PageRankEntity(i.page,SUM(i.rating))
            FROM IndexEntity i
            WHERE i.lemma IN (:lemmas)
            AND i.page.id IN (:pagesId)
            GROUP BY i.page
            ORDER BY SUM(i.rating) DESC
            """)
    List<PageRankEntity> findPagesWithSumRankByLemmasAndPagesId(@Param("lemmas") List<LemmaEntity> lemmas
            , @Param("pagesId") List<Integer> pagesId);

    @Query("""
            SELECT i.lemma.id
            FROM IndexEntity i
            WHERE i.page.id = :pageId
    """)
    List<Integer> findLemmasIdByPage(@Param("pageId") int pageId);
}
