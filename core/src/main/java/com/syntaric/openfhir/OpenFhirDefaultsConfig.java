package com.syntaric.openfhir;

import com.syntaric.openfhir.mapping.tofhir.ToFhirPrePostProcessor;
import com.syntaric.openfhir.mapping.tofhir.ToFhirPrePostProcessorInterface;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrPrePostProcessor;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrPrePostProcessorInterface;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.producers.NoOpUserContextProducer;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;
import com.syntaric.openfhir.util.NoOpPrePostFhirInstancePopulator;
import com.syntaric.openfhir.util.NoOpPrePostOpenEhrPopulator;
import com.syntaric.openfhir.util.PrePostFhirInstancePopulatorInterface;
import com.syntaric.openfhir.util.PrePostOpenEhrPopulatorInterface;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFhirDefaultsConfig {

    @Bean
    @ConditionalOnMissingBean(UserContextProducerInterface.class)
    public UserContextProducerInterface userContextProducer() {
        return new NoOpUserContextProducer();
    }

    @Bean
    @ConditionalOnMissingBean(TerminologyTranslatorInterface.class)
    public TerminologyTranslatorInterface terminologyTranslator() {
        return new NoOpTerminologyTranslator();
    }

    @Bean
    @ConditionalOnMissingBean(ToFhirPrePostProcessorInterface.class)
    public ToFhirPrePostProcessorInterface toFhirPrePostProcessor(final FhirContextRegistry fhirContextRegistry) {
        return new ToFhirPrePostProcessor(fhirContextRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(ToOpenEhrPrePostProcessorInterface.class)
    public ToOpenEhrPrePostProcessorInterface toOpenEhrPrePostProcessor(final FhirContextRegistry fhirContextRegistry) {
        return new ToOpenEhrPrePostProcessor(fhirContextRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(PrePostFhirInstancePopulatorInterface.class)
    public PrePostFhirInstancePopulatorInterface prePostFhirInstancePopulator() {
        return new NoOpPrePostFhirInstancePopulator();
    }

    @Bean
    @ConditionalOnMissingBean(PrePostOpenEhrPopulatorInterface.class)
    public PrePostOpenEhrPopulatorInterface prePostOpenEhrPopulator() {
        return new NoOpPrePostOpenEhrPopulator();
    }
}
