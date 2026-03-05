package com.syntaric.openfhir.db.repository;

import com.syntaric.openfhir.db.repository.mongodb.BootstrapMongoRepository;
import com.syntaric.openfhir.db.repository.mongodb.FhirConnectContextMongoRepository;
import com.syntaric.openfhir.db.repository.mongodb.FhirConnectModelMongoRepository;
import com.syntaric.openfhir.db.repository.mongodb.OptMongoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnMissingBean(type = "com.syntaric.openfhir.db.repository.RepositoryProducers")
public class DefaultRepositoryProducers {

    private final OptMongoRepository optMongoRepository;
    private final BootstrapMongoRepository bootstrapMongoRepository;
    private final FhirConnectContextMongoRepository fhirConnectContextMongoRepository;
    private final FhirConnectModelMongoRepository fhirConnectMapperMongoRepository;

    @Autowired
    public DefaultRepositoryProducers(@Autowired(required = false) final OptMongoRepository optMongoRepository,
                                      @Autowired(required = false) final BootstrapMongoRepository bootstrapMongoRepository,
                                      @Autowired(required = false) final FhirConnectContextMongoRepository fhirConnectContextMongoRepository,
                                      @Autowired(required = false) final FhirConnectModelMongoRepository fhirConnectMapperMongoRepository) {
        this.optMongoRepository = optMongoRepository;
        this.bootstrapMongoRepository = bootstrapMongoRepository;
        this.fhirConnectContextMongoRepository = fhirConnectContextMongoRepository;
        this.fhirConnectMapperMongoRepository = fhirConnectMapperMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(BootstrapRepository.class)
    public BootstrapRepository mongoBootstrapRepository() {
        return bootstrapMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(OptRepository.class)
    public OptRepository mongoOptRepository() {
        return optMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(FhirConnectContextRepository.class)
    public FhirConnectContextRepository mongoFhirConnectContextRepository() {
        return fhirConnectContextMongoRepository;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(FhirConnectModelRepository.class)
    public FhirConnectModelRepository mongoFhirConnectMapperRepository() {
        return fhirConnectMapperMongoRepository;
    }
}
