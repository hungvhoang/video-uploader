package org.example.uploader.repos;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.example.uploader.models.Video;

import java.time.Instant;
import java.util.List;

public interface VideoRepository extends MongoRepository<Video, String> {

    @Query(value = "{ 'status': 'READY' }", sort = "{ 'createdAt': -1 }")
    List<Video> findReadyNewestFirst();

    @Query("{ 'status': 'INITIATED', 'createdAt': { '$lt': ?0 } }")
    List<Video> findStaleInitiatedBefore(Instant timestamp);
}
