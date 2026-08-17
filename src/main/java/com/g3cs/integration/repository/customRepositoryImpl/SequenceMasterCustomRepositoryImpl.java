package com.g3cs.integration.repository.customRepositoryImpl;

import com.g3cs.integration.model.SequenceMaster;
import com.g3cs.integration.repository.SequenceMasterCustomRepository;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class SequenceMasterCustomRepositoryImpl implements SequenceMasterCustomRepository {

    private final MongoTemplate mongoTemplate;

    public SequenceMasterCustomRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public SequenceMaster getTemplate(String category) {
        Query query = new Query(Criteria.where("category").is(category).and("active").is(true));
        return mongoTemplate.findOne(query, SequenceMaster.class);
    }

    @Override
    public SequenceMaster getNextGlobalSequence(String category) {
        Query query = new Query(Criteria.where("category").is(category).and("active").is(true));
        Update update = new Update()
                .inc("last_number", 1)
                .set("updated_at", Instant.now())
                .inc("version", 1);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(false), SequenceMaster.class);
    }
}
