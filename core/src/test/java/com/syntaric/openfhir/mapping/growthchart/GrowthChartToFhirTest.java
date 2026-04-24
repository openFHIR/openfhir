package com.syntaric.openfhir.mapping.growthchart;

import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

public class GrowthChartToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/growth_chart/";
    final String CONTEXT_MAPPING = "/growth_chart/growth-chart.context.yml";
    final String HELPER_LOCATION = "/growth_chart/";
    final String OPT = "Growth chart.opt";
    final String FLAT = "growth_chart_flat.json";

    private OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void growthChartSpecificObservationToFhir() throws IOException {
        final String observation = "{\n" +
                "        \"_type\": \"OBSERVATION\",\n" +
                "        \"archetype_node_id\": \"openEHR-EHR-OBSERVATION.height.v2\",\n" +
                "        \"name\": {\n" +
                "          \"_type\": \"DV_TEXT\",\n" +
                "          \"value\": \"Height/Length\"\n" +
                "        },\n" +
                "        \"archetype_details\": {\n" +
                "          \"archetype_id\": {\n" +
                "            \"value\": \"openEHR-EHR-OBSERVATION.height.v2\"\n" +
                "          },\n" +
                "          \"rm_version\": \"1.0.4\"\n" +
                "        },\n" +
                "        \"encoding\": {\n" +
                "          \"_type\": \"CODE_PHRASE\",\n" +
                "          \"code_string\": \"ISO-10646-UTF-1\",\n" +
                "          \"terminology_id\": {\n" +
                "            \"_type\": \"TERMINOLOGY_ID\",\n" +
                "            \"value\": \"IANA_character-sets\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"guideline_id\": {\n" +
                "          \"_type\": \"OBJECT_REF\",\n" +
                "          \"id\": {\n" +
                "            \"_type\": \"GENERIC_ID\",\n" +
                "            \"value\": \"48b61e73-9545-358b-b442-34db4c2ed3b9\",\n" +
                "            \"scheme\": \"scheme\"\n" +
                "          },\n" +
                "          \"namespace\": \"unknown\",\n" +
                "          \"type\": \"ANY\"\n" +
                "        },\n" +
                "        \"language\": {\n" +
                "          \"_type\": \"CODE_PHRASE\",\n" +
                "          \"code_string\": \"en\",\n" +
                "          \"terminology_id\": {\n" +
                "            \"_type\": \"TERMINOLOGY_ID\",\n" +
                "            \"value\": \"ISO_639-1\"\n" +
                "          }\n" +
                "        },\n" +
                "        \"provider\": {\n" +
                "          \"_type\": \"PARTY_SELF\"\n" +
                "        },\n" +
                "        \"subject\": {\n" +
                "          \"_type\": \"PARTY_SELF\"\n" +
                "        },\n" +
                "        \"workflow_id\": {\n" +
                "          \"_type\": \"OBJECT_REF\",\n" +
                "          \"id\": {\n" +
                "            \"_type\": \"GENERIC_ID\",\n" +
                "            \"value\": \"36c6d06e-cc47-306c-a5c4-66833ced9c2e\",\n" +
                "            \"scheme\": \"scheme\"\n" +
                "          },\n" +
                "          \"namespace\": \"unknown\",\n" +
                "          \"type\": \"ANY\"\n" +
                "        },\n" +
                "        \"data\": {\n" +
                "          \"archetype_node_id\": \"at0001\",\n" +
                "          \"name\": {\n" +
                "            \"_type\": \"DV_TEXT\",\n" +
                "            \"value\": \"history\"\n" +
                "          },\n" +
                "          \"duration\": {\n" +
                "            \"_type\": \"DV_DURATION\",\n" +
                "            \"value\": \"PT0S\"\n" +
                "          },\n" +
                "          \"origin\": {\n" +
                "            \"_type\": \"DV_DATE_TIME\",\n" +
                "            \"value\": \"2022-02-03T04:05:06\"\n" +
                "          },\n" +
                "          \"period\": {\n" +
                "            \"_type\": \"DV_DURATION\",\n" +
                "            \"value\": \"PT0S\"\n" +
                "          },\n" +
                "          \"events\": [\n" +
                "            {\n" +
                "              \"_type\": \"POINT_EVENT\",\n" +
                "              \"archetype_node_id\": \"at0021\",\n" +
                "              \"name\": {\n" +
                "                \"_type\": \"DV_TEXT\",\n" +
                "                \"value\": \"Birth\"\n" +
                "              },\n" +
                "              \"time\": {\n" +
                "                \"_type\": \"DV_DATE_TIME\",\n" +
                "                \"value\": \"2022-02-03T04:05:06\"\n" +
                "              },\n" +
                "              \"data\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0003\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Simple\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0004\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Height/Length\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_QUANTITY\",\n" +
                "                      \"magnitude\": 500.0,\n" +
                "                      \"units\": \"cm\"\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0018\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Comment\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              },\n" +
                "              \"state\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0013\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Tree\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0014\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Position\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_CODED_TEXT\",\n" +
                "                      \"value\": \"Standing\",\n" +
                "                      \"defining_code\": {\n" +
                "                        \"_type\": \"CODE_PHRASE\",\n" +
                "                        \"code_string\": \"at0016\",\n" +
                "                        \"terminology_id\": {\n" +
                "                          \"_type\": \"TERMINOLOGY_ID\",\n" +
                "                          \"value\": \"local\"\n" +
                "                        }\n" +
                "                      }\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0019\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Confounding factors\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              }\n" +
                "            },\n" +
                "            {\n" +
                "              \"_type\": \"INTERVAL_EVENT\",\n" +
                "              \"archetype_node_id\": \"at0002\",\n" +
                "              \"name\": {\n" +
                "                \"_type\": \"DV_TEXT\",\n" +
                "                \"value\": \"Any event\"\n" +
                "              },\n" +
                "              \"math_function\": {\n" +
                "                \"_type\": \"DV_CODED_TEXT\",\n" +
                "                \"value\": \"minimum\",\n" +
                "                \"defining_code\": {\n" +
                "                  \"_type\": \"CODE_PHRASE\",\n" +
                "                  \"code_string\": \"145\",\n" +
                "                  \"terminology_id\": {\n" +
                "                    \"_type\": \"TERMINOLOGY_ID\",\n" +
                "                    \"value\": \"openehr\"\n" +
                "                  }\n" +
                "                }\n" +
                "              },\n" +
                "              \"time\": {\n" +
                "                \"_type\": \"DV_DATE_TIME\",\n" +
                "                \"value\": \"2022-02-03T04:05:06\"\n" +
                "              },\n" +
                "              \"width\": {\n" +
                "                \"_type\": \"DV_DURATION\",\n" +
                "                \"value\": \"PT42H\"\n" +
                "              },\n" +
                "              \"data\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0003\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Simple\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0004\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Height/Length\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_QUANTITY\",\n" +
                "                      \"magnitude\": 500.0,\n" +
                "                      \"units\": \"cm\"\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0018\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Comment\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              },\n" +
                "              \"state\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0013\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Tree\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0014\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Position\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_CODED_TEXT\",\n" +
                "                      \"value\": \"Standing\",\n" +
                "                      \"defining_code\": {\n" +
                "                        \"_type\": \"CODE_PHRASE\",\n" +
                "                        \"code_string\": \"at0016\",\n" +
                "                        \"terminology_id\": {\n" +
                "                          \"_type\": \"TERMINOLOGY_ID\",\n" +
                "                          \"value\": \"local\"\n" +
                "                        }\n" +
                "                      }\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0019\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Confounding factors\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              }\n" +
                "            },\n" +
                "            {\n" +
                "              \"_type\": \"POINT_EVENT\",\n" +
                "              \"archetype_node_id\": \"at0002\",\n" +
                "              \"name\": {\n" +
                "                \"_type\": \"DV_TEXT\",\n" +
                "                \"value\": \"Any event\"\n" +
                "              },\n" +
                "              \"time\": {\n" +
                "                \"_type\": \"DV_DATE_TIME\",\n" +
                "                \"value\": \"2022-02-03T04:05:06\"\n" +
                "              },\n" +
                "              \"data\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0003\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Simple\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0004\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Height/Length\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_QUANTITY\",\n" +
                "                      \"magnitude\": 500.0,\n" +
                "                      \"units\": \"cm\"\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0018\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Comment\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              },\n" +
                "              \"state\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0013\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Tree\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0014\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Position\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_CODED_TEXT\",\n" +
                "                      \"value\": \"Standing\",\n" +
                "                      \"defining_code\": {\n" +
                "                        \"_type\": \"CODE_PHRASE\",\n" +
                "                        \"code_string\": \"at0016\",\n" +
                "                        \"terminology_id\": {\n" +
                "                          \"_type\": \"TERMINOLOGY_ID\",\n" +
                "                          \"value\": \"local\"\n" +
                "                        }\n" +
                "                      }\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0019\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Confounding factors\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              }\n" +
                "            },\n" +
                "            {\n" +
                "              \"_type\": \"INTERVAL_EVENT\",\n" +
                "              \"archetype_node_id\": \"at0002\",\n" +
                "              \"name\": {\n" +
                "                \"_type\": \"DV_TEXT\",\n" +
                "                \"value\": \"Any event\"\n" +
                "              },\n" +
                "              \"math_function\": {\n" +
                "                \"_type\": \"DV_CODED_TEXT\",\n" +
                "                \"value\": \"minimum\",\n" +
                "                \"defining_code\": {\n" +
                "                  \"_type\": \"CODE_PHRASE\",\n" +
                "                  \"code_string\": \"145\",\n" +
                "                  \"terminology_id\": {\n" +
                "                    \"_type\": \"TERMINOLOGY_ID\",\n" +
                "                    \"value\": \"openehr\"\n" +
                "                  }\n" +
                "                }\n" +
                "              },\n" +
                "              \"time\": {\n" +
                "                \"_type\": \"DV_DATE_TIME\",\n" +
                "                \"value\": \"2022-02-03T04:05:06\"\n" +
                "              },\n" +
                "              \"width\": {\n" +
                "                \"_type\": \"DV_DURATION\",\n" +
                "                \"value\": \"PT42H\"\n" +
                "              },\n" +
                "              \"data\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0003\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Simple\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0004\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Height/Length\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_QUANTITY\",\n" +
                "                      \"magnitude\": 500.0,\n" +
                "                      \"units\": \"cm\"\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0018\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Comment\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              },\n" +
                "              \"state\": {\n" +
                "                \"_type\": \"ITEM_TREE\",\n" +
                "                \"archetype_node_id\": \"at0013\",\n" +
                "                \"name\": {\n" +
                "                  \"_type\": \"DV_TEXT\",\n" +
                "                  \"value\": \"Tree\"\n" +
                "                },\n" +
                "                \"items\": [\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0014\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Position\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_CODED_TEXT\",\n" +
                "                      \"value\": \"Standing\",\n" +
                "                      \"defining_code\": {\n" +
                "                        \"_type\": \"CODE_PHRASE\",\n" +
                "                        \"code_string\": \"at0016\",\n" +
                "                        \"terminology_id\": {\n" +
                "                          \"_type\": \"TERMINOLOGY_ID\",\n" +
                "                          \"value\": \"local\"\n" +
                "                        }\n" +
                "                      }\n" +
                "                    }\n" +
                "                  },\n" +
                "                  {\n" +
                "                    \"_type\": \"ELEMENT\",\n" +
                "                    \"archetype_node_id\": \"at0019\",\n" +
                "                    \"name\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Confounding factors\"\n" +
                "                    },\n" +
                "                    \"value\": {\n" +
                "                      \"_type\": \"DV_TEXT\",\n" +
                "                      \"value\": \"Lorem ipsum\"\n" +
                "                    }\n" +
                "                  }\n" +
                "                ]\n" +
                "              }\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      }";
        final com.nedap.archie.rm.composition.Observation unmarshalled = new CanonicalJson().unmarshal(observation, com.nedap.archie.rm.composition.Observation.class);

        Bundle bundle = (Bundle) toFhir.contentItemsToFhir(context, List.of(unmarshalled), webTemplate);
        System.out.println();
    }

    @Test
    public void growthChartToFhir() throws IOException {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             new OPTParser(
                                                                                     operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        Assert.assertEquals(12, bundle.getEntry().size());
        // 3x weight
        // 3x height
        // 3x bmi
        // 3x head

        List<Observation> weights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "weight".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, weights.size());
        final Observation firstWeight = weights.stream().filter(e -> "2022-02-03T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation secondWeight = weights.stream().filter(e -> "2022-02-04T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation thirdWeight = weights.stream().filter(e -> "2022-02-05T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        Assert.assertEquals("501.0", firstWeight.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("kg", firstWeight.getValueQuantity().getUnit());
        Assert.assertEquals("502.0", secondWeight.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("t", secondWeight.getValueQuantity().getUnit());
        Assert.assertEquals("503.0", thirdWeight.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm", thirdWeight.getValueQuantity().getUnit());
        Assert.assertEquals("body_weightLorem ipsum0", firstWeight.getNoteFirstRep().getText());
        Assert.assertEquals("body_weightLorem ipsum1", secondWeight.getNoteFirstRep().getText());
        Assert.assertEquals("body_weightLorem ipsum2", thirdWeight.getNoteFirstRep().getText());

        List<Observation> heights = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "height".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, heights.size());

        List<Observation> bmis = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "bmi".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, bmis.size());

        List<Observation> heads = bundle.getEntry().stream()
                .map(en -> ((Observation) en.getResource()))
                .filter(en -> "head_circumference".equals(en.getCategoryFirstRep().getCodingFirstRep().getCode()))
                .collect(Collectors.toList());
        Assert.assertEquals(3, heads.size());
        final Observation firstHead = heads.stream().filter(e -> "2023-02-03T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation secondHead = heads.stream().filter(e -> "2023-02-04T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        final Observation thirdHead = heads.stream().filter(e -> "2023-02-05T04:05:06".equals(
                        openFhirMapperUtils.dateTimeToString(e.getEffectiveDateTimeType().getValue())))
                .findFirst().orElse(null);
        Assert.assertEquals("50.0", firstHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("cm", firstHead.getValueQuantity().getUnit());
        Assert.assertEquals("51.0", secondHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("mm", secondHead.getValueQuantity().getUnit());
        Assert.assertEquals("52.0", thirdHead.getValueQuantity().getValue().toPlainString());
        Assert.assertEquals("m", thirdHead.getValueQuantity().getUnit());
    }


}
