package com.syntaric.openfhir.db.repository.mongodb.migrations;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@ChangeUnit(id = "addIndexes", order = "2", author = "openFHIR")
@Slf4j
public class V2__AddIndexes {

    @Execution
    public void addIndexes(MongoDatabase database) {
        log.info("Starting migration: AddIndexes");

        // fhirConnectContextEntity
        MongoCollection<Document> contextCollection = database.getCollection("fhirConnectContextEntity");
        contextCollection.createIndex(Indexes.ascending("organisation"), new IndexOptions().background(true));
        contextCollection.createIndex(
                Indexes.ascending("organisation", "fhirConnectContext.context.template.id"),
                new IndexOptions().background(true));
        log.info("Created indexes on fhirConnectContextEntity");

        // fhirConnectModelEntity
        MongoCollection<Document> modelCollection = database.getCollection("fhirConnectModelEntity");
        modelCollection.createIndex(Indexes.ascending("organisation"), new IndexOptions().background(true));
        modelCollection.createIndex(
                Indexes.ascending("organisation", "fhirConnectModel.openEhrConfig.archetype"),
                new IndexOptions().background(true));
        modelCollection.createIndex(
                Indexes.ascending("organisation", "fhirConnectModel.metadata.name"),
                new IndexOptions().background(true));
        log.info("Created indexes on fhirConnectModelEntity");

        // optEntity
        MongoCollection<Document> optCollection = database.getCollection("optEntity");
        optCollection.createIndex(Indexes.ascending("organisation"), new IndexOptions().background(true));
        optCollection.createIndex(
                Indexes.ascending("organisation", "templateId"),
                new IndexOptions().background(true));
        log.info("Created indexes on optEntity");

        log.info("Migration completed successfully.");
    }

    @RollbackExecution
    public void rollback(MongoDatabase database) {
        log.info("Rolling back migration: AddIndexes");

        database.getCollection("fhirConnectContextEntity").dropIndex("organisation_1_fhirConnectContext.context.template.id_1");
        database.getCollection("fhirConnectContextEntity").dropIndex("organisation_1");

        database.getCollection("fhirConnectModelEntity").dropIndex("organisation_1_fhirConnectModel.openEhrConfig.archetype_1");
        database.getCollection("fhirConnectModelEntity").dropIndex("organisation_1_fhirConnectModel.metadata.name_1");
        database.getCollection("fhirConnectModelEntity").dropIndex("organisation_1");

        database.getCollection("optEntity").dropIndex("organisation_1_templateId_1");
        database.getCollection("optEntity").dropIndex("organisation_1");

        log.info("Rollback completed.");
    }
}
