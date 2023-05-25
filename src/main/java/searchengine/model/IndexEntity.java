package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
@Entity
@Table(name = "search_index",
        uniqueConstraints = { @UniqueConstraint(name = "index_li_pi_index"
                , columnNames = { "lemma_id", "page_id" })
        })
@NoArgsConstructor
@Getter
@Setter
public class IndexEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(optional = false, cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false, foreignKey = @ForeignKey(name = "fk_index_page_id"))
    @OnDelete(action= OnDeleteAction.CASCADE)
    private PageEntity page;

    @ManyToOne(optional = false, cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @JoinColumn(name = "lemma_id", nullable = false, foreignKey = @ForeignKey(name = "fk_index_lemma_id"))
    @OnDelete(action= OnDeleteAction.CASCADE)
    private LemmaEntity lemma;

    @Column(nullable = false)
    private float rating;
}
