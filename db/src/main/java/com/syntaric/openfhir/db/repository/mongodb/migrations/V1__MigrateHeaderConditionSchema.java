package com.syntaric.openfhir.db.repository.mongodb.migrations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@ChangeUnit(id = "headerConditionMigration", order = "1", author = "openFHIR")
@Slf4j
public class V1__MigrateHeaderConditionSchema {

    @Execution
    public void addHeaderConditionCollection(MongoDatabase database) {
        log.info("Starting migration: MigrateHeaderConditionSchema");

        // Access the collection
        MongoCollection<Document> collection = database.getCollection("fhirConnectModelEntity");

        // Fetch all documents
        List<Document> documents = collection.find().into(new ArrayList<>());

        for (Document doc : documents) {
            String id = doc.getObjectId("_id").toString();
            log.info("Updating BLOB for ID: {}", id);

            // Convert the BSON document to a JSON object
            JsonObject oldJson = new Gson().fromJson(doc.toJson(), JsonObject.class);
            String updatedJson = adjustYamlOrNull(oldJson);

            if (updatedJson == null) {
                log.info("Nothing to migrate, {} doesn't have fhirCondition in spec.fhirConfig", id);
                continue;
            }

            // Convert the updated JSON string back to a BSON document
            Document updatedDocument = Document.parse(updatedJson);

            // Update the document in the database
            collection.replaceOne(new Document("_id", doc.getObjectId("_id")), updatedDocument);

            log.info("Updated document for ID: {}", id);
        }

        log.info("Migration completed successfully.");

    }

    private String adjustYamlOrNull(final JsonObject oldObject) {
        final JsonObject oldFhirConnectModel = oldObject.getAsJsonObject("fhirConnectModel");
        final JsonObject spec = oldFhirConnectModel.getAsJsonObject("spec");
        if (spec == null) {
            return null;
        }
        final JsonObject fhirConfig = spec.getAsJsonObject("fhirConfig");
        if (fhirConfig == null) {
            return null;
        }
        final JsonArray condition = fhirConfig.getAsJsonArray("condition");
        if (condition == null) {
            return null;
        }
        final JsonObject preprocessorObject = new JsonObject();
        oldFhirConnectModel.add("preprocessor", preprocessorObject);
        preprocessorObject.add("fhirConditions", condition);
        fhirConfig.remove("condition");
        return new Gson().toJson(oldObject);
    }

    @RollbackExecution
    public void doNothing() {

    }

}
