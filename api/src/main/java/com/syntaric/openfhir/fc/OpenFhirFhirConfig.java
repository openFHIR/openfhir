
package com.syntaric.openfhir.fc;


import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;


@Data
public class OpenFhirFhirConfig {

    private String resource;
    private List<Condition> condition;

    public OpenFhirFhirConfig copy() {
        final OpenFhirFhirConfig fhirConfig = new OpenFhirFhirConfig();
        fhirConfig.setResource(resource);
        if (condition != null) {
            List<Condition> toAdd = new ArrayList<>();
            for (Condition condition1 : condition) {
                toAdd.add(condition1.copy());
            }
            fhirConfig.setCondition(toAdd);
        }
        return fhirConfig;
    }

    public OpenFhirFhirConfig withCondition(List<Condition> condition) {
        this.condition = condition;
        return this;
    }

    public OpenFhirFhirConfig withResource(String resource) {
        this.resource = resource;
        return this;
    }
}
