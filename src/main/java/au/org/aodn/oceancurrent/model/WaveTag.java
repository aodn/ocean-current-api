package au.org.aodn.oceancurrent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Entity
@Table(name = "tags", indexes = {
    @Index(name = "ix_tags_tagfile", columnList = "tagfile")
})
@IdClass(WaveTagId.class)
public class WaveTag {

    @Id
    @Column(name = "tagfile", nullable = false)
    private String tagfile;

    @Id
    @Column(name = "\"order\"", nullable = false)
    private Integer order;

    @Column(name = "x", nullable = false)
    private Integer x;

    @Column(name = "y", nullable = false)
    private Integer y;

    @Column(name = "sz", nullable = false)
    private Integer sz;

    @Column(name = "title")
    private String title;

    @Column(name = "url")
    private String url;
}
