package au.org.aodn.oceancurrent.repository;

import au.org.aodn.oceancurrent.model.WaveTag;
import au.org.aodn.oceancurrent.model.WaveTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaveTagRepository extends JpaRepository<WaveTag, WaveTagId> {

    /**
     * Find all tags by tagfile ordered by order field
     */
    List<WaveTag> findByTagfileOrderByOrder(String tagfile);

    /**
     * Find distinct tagfiles
     */
    @Query("SELECT DISTINCT w.tagfile FROM WaveTag w ORDER BY w.tagfile")
    List<String> findDistinctTagfiles();

    /**
     * Check if any data exists for the given tagfile
     */
    boolean existsByTagfile(String tagfile);

    /**
     * Count total number of tags
     */
    @Query("SELECT COUNT(w) FROM WaveTag w")
    long countAllTags();

    /**
     * Count number of distinct tagfiles
     */
    @Query("SELECT COUNT(DISTINCT w.tagfile) FROM WaveTag w")
    long countDistinctTagfiles();
}
