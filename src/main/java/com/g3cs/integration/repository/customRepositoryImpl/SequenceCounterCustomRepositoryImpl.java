package com.g3cs.integration.repository.customRepositoryImpl;

import com.g3cs.integration.model.SequenceCounter;
import com.g3cs.integration.repository.SequenceCounterCustomRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class SequenceCounterCustomRepositoryImpl implements SequenceCounterCustomRepository {

    private final MongoTemplate mongoTemplate;

    public SequenceCounterCustomRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SequenceCounter getNextSequenceCounter(String category, String parentCategory) {
        Query query = new Query(Criteria.where("category").is(category).and("parent_category").is(parentCategory));
        Update update = new Update()
                .inc("last_number", 1)
                .inc("version", 1)
                .set("updated_at", Instant.now())
                .setOnInsert("category", category)
                .setOnInsert("parent_category", parentCategory)
                .setOnInsert("created_at", Instant.now())
                .setOnInsert("active", true);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(true), SequenceCounter.class);
    }
}
