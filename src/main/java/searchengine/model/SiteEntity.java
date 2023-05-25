package searchengine.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "site")
@NoArgsConstructor
@Getter
@EqualsAndHashCode
public class SiteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "enum('INDEXING', 'INDEXED', 'FAILED')")
    private SiteIndexingStatus status;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private String url;

    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;

    public SiteEntity setId(int id) {
        this.id = id;
        return this;
    }

    public SiteEntity setStatus(SiteIndexingStatus status) {
        this.status = status;
        return this;
    }

    public SiteEntity setStatusTime(LocalDateTime statusTime) {
        this.statusTime = statusTime;
        return this;
    }

    public SiteEntity setLastError(String lastError) {
        this.lastError = lastError;
        return this;
    }

    public SiteEntity setUrl(String url) {
        this.url = url;
        return this;
    }

    public SiteEntity setName(String name) {
        this.name = name;
        return this;
    }
}
