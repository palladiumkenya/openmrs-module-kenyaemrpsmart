package org.openmrs.module.kenyaemrpsmart.page.controller;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.ImmunizationWrapper;
import org.openmrs.module.kenyaemrpsmart.kenyaemrUtils.Utils;
import org.openmrs.module.kenyaemrpsmart.metadata.SmartCardMetadata;
import org.openmrs.module.kenyaui.KenyaUiUtils;
import org.openmrs.module.kenyaui.annotation.AppPage;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.UiUtils;
import org.openmrs.ui.framework.annotation.SpringBean;
import org.openmrs.ui.framework.page.PageModel;
import org.openmrs.ui.framework.page.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller class for cohort import page
 */

public class KenyaemrpsmarthomePageController {

    protected static final Log log = LogFactory.getLog(KenyaemrpsmarthomePageController.class);
    PatientService patientService = Context.getPatientService();
    PersonService personService = Context.getPersonService();
    EncounterService encounterService = Context.getEncounterService();
    ObsService obsService = Context.getObsService();
    ConceptService conceptService = Context.getConceptService();
    SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    String HTS_CONFIRMATORY_TEST_FORM_UUID = "b08471f6-0892-4bf7-ab2b-bf79797b8ea4";
    String HTS_INITIAL_TEST_FORM_UUID = "402dc5d7-46da-42d4-b2be-f43ea4ad87b0";
    String IMMUNIZATION_FORM_UUID = "b4f3859e-861c-4a63-bdff-eb7392030d47";

    public void controller(@RequestParam(value="patientId") Patient patient,
                           @SpringBean KenyaUiUtils kenyaUi,
                           UiUtils ui,
                           PageRequest pageRequest,
                           PageModel model){
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("patientId", patient.getId());


        List<SimpleObject> getTests = getHivTests(patient);
        Set<SimpleObject> getImmunizations = extractImmunizationData(patient);
        getImmunizations.addAll(extractImmunizationDataFromPsmartEncounter(patient));
        model.addAttribute("summaries", SimpleObject.create("totalTests", getTests.size(), "totalImmunizations", getImmunizations.size()));
        model.addAttribute("patient", patient);
        model.addAttribute("existingTests", getTests);
        model.addAttribute("existingImmunizations", getImmunizations);


    }

    private Set<SimpleObject> extractImmunizationData(Patient patient) {

        Concept groupingConcept = conceptService.getConcept(1421);
        Concept	vaccineConcept = conceptService.getConcept(984);
        Concept sequenceNumber = conceptService.getConcept(1418);
        Concept dateGiven = conceptService.getConcept(1282);
        Concept dateGivenConcept2 = conceptService.getConcept(1410);

        List<ImmunizationWrapper> getList = new ArrayList<ImmunizationWrapper>();
        // get immunizations from immunization form
        List<Encounter> immunizationEncounters = encounterService.getEncounters(
                patient,
                null,
                null,
                null,
                Arrays.asList(Context.getFormService().getFormByUuid(IMMUNIZATION_FORM_UUID)),
                null,
                null,
                null,
                null,
                false
        );


        List<ImmunizationWrapper> immunizationList = new ArrayList<ImmunizationWrapper>();
        // extract blocks of vaccines organized by grouping concept
        for(Encounter encounter : immunizationEncounters) {
            List<Obs> obs = obsService.getObservations(
                    Arrays.asList(Context.getPersonService().getPerson(patient.getPersonId())),
                    Arrays.asList(encounter),
                    Arrays.asList(groupingConcept),
                    null,
                    null,
                    null,
                    Arrays.asList("obsId"),
                    null,
                    null,
                    null,
                    null,
                    false
            );
            // Iterate through groups
            for(Obs group : obs) {
                ImmunizationWrapper groupWrapper;
                Concept vaccine = null;
                Integer sequence = 1000;
                Date vaccineDate = null;
                Set<Obs> members = group.getGroupMembers();
                // iterate through obs for a particular group
                for (Obs memberObs : members) {
                    if (memberObs.getConcept().equals(vaccineConcept) ) {
                        vaccine = memberObs.getValueCoded();
                    } else if (memberObs.getConcept().equals(sequenceNumber)) {
                        sequence = memberObs.getValueNumeric() != null? memberObs.getValueNumeric().intValue() : 1000; // put 1000 for null
                    } else if (memberObs.getConcept().equals(dateGiven) || memberObs.getConcept().equals(dateGivenConcept2)) {
                        vaccineDate = memberObs.getValueDate();
                    }
                }
                immunizationList.add(new ImmunizationWrapper(vaccine, sequence, vaccineDate));

            }
        }
        Set<SimpleObject> convertedImmunizations = new HashSet<SimpleObject>();

        for(ImmunizationWrapper entry : immunizationList) {
            convertedImmunizations.add(vaccineConverterNode(entry));
        }

        return convertedImmunizations;
    }

    private Set<SimpleObject> extractImmunizationDataFromPsmartEncounter(Patient patient) {

        Concept groupingConcept = conceptService.getConcept(1421);
        Concept	vaccineConcept = conceptService.getConcept(984);
        Concept sequenceNumber = conceptService.getConcept(1418);

        List<ImmunizationWrapper> getList = new ArrayList<ImmunizationWrapper>();
        List<ImmunizationWrapper> immunizationList = new ArrayList<ImmunizationWrapper>();

        // get immunizations from immunization form
        List<Encounter> immunizationEncounters = encounterService.getEncounters(
                patient,
                null,
                null,
                null,
                Arrays.asList(Context.getFormService().getFormByUuid(SmartCardMetadata._Form.PSMART_IMMUNIZATION)),
                null,
                null,
                null,
                null,
                false
        );

        // extract blocks of vaccines organized by grouping concept
        for(Encounter encounter : immunizationEncounters) {
            List<Obs> obs = obsService.getObservations(
                    Arrays.asList(Context.getPersonService().getPerson(patient.getPersonId())),
                    Arrays.asList(encounter),
                    Arrays.asList(groupingConcept),
                    null,
                    null,
                    null,
                    Arrays.asList("obsId"),
                    null,
                    null,
                    null,
                    null,
                    false
            );
            // Iterate through groups
            for(Obs group : obs) {
                ImmunizationWrapper groupWrapper;
                Concept vaccine = null;
                Integer sequence = 1000;
                Date vaccineDate = group.getObsDatetime();
                Set<Obs> members = group.getGroupMembers();
                // iterate through obs for a particular group
                for (Obs memberObs : members) {
                    if (memberObs.getConcept().equals(vaccineConcept) ) {
                        vaccine = memberObs.getValueCoded();
                    } else if (memberObs.getConcept().equals(sequenceNumber)) {
                        sequence = memberObs.getValueNumeric() != null? memberObs.getValueNumeric().intValue() : 1000; // put 1000 for null
                    }
                }
                immunizationList.add(new ImmunizationWrapper(vaccine, sequence, vaccineDate));

            }
        }
        Set<SimpleObject> convertedImmunizations = new HashSet<SimpleObject>();

        for(ImmunizationWrapper entry : immunizationList) {
            convertedImmunizations.add(vaccineConverterNode(entry));
        }

        return convertedImmunizations;
    }

    private List<SimpleObject> getHivTests(Patient patient) {

        // test concepts
        Concept finalHivTestResultConcept = conceptService.getConcept(159427);
        Concept	testTypeConcept = conceptService.getConcept(162084);
        Concept testStrategyConcept = conceptService.getConcept(164956);
        Concept testFacilityCodeConcept = conceptService.getConcept(162724);
        Concept healthProviderConcept = conceptService.getConcept(1473);
        Concept healthProviderIdentifierConcept = conceptService.getConcept(163161);



        Form HTS_INITIAL_FORM = Context.getFormService().getFormByUuid(HTS_INITIAL_TEST_FORM_UUID);
        Form HTS_CONFIRMATORY_FORM = Context.getFormService().getFormByUuid(HTS_CONFIRMATORY_TEST_FORM_UUID);

        EncounterType smartCardHTSEntry = Context.getEncounterService().getEncounterTypeByUuid(SmartCardMetadata._EncounterType.EXTERNAL_PSMART_DATA);
        Form SMART_CARD_HTS_FORM = Context.getFormService().getFormByUuid(SmartCardMetadata._Form.PSMART_HIV_TEST);


        List<Encounter> htsEncounters = Utils.getEncounters(patient, Arrays.asList(HTS_CONFIRMATORY_FORM, HTS_INITIAL_FORM));
        List<Encounter> processedIncomingTests = Utils.getEncounters(patient, Arrays.asList(SMART_CARD_HTS_FORM));

        List<SimpleObject> testList = new ArrayList<SimpleObject>();
        // loop through encounters and extract hiv test information
        for(Encounter encounter : htsEncounters) {
            List<Obs> obs = Utils.getEncounterObservationsForQuestions(patient, encounter, Arrays.asList(finalHivTestResultConcept, testTypeConcept, testStrategyConcept));
            testList.add(extractHivTestInformation(obs));
        }

        // append processed tests from card
        for(Encounter encounter : processedIncomingTests) {
            List<Obs> obs = Utils.getEncounterObservationsForQuestions(patient, encounter, Arrays.asList(finalHivTestResultConcept, testTypeConcept, testStrategyConcept, testFacilityCodeConcept, healthProviderConcept, healthProviderIdentifierConcept));
            testList.add(extractHivTestInformation(obs));
        }

        return testList;
    }

    SimpleObject vaccineConverterNode (ImmunizationWrapper wrapper) {

        Concept BCG = conceptService.getConcept(886);
        Concept OPV = conceptService.getConcept(783);
        Concept IPV = conceptService.getConcept(1422);
        Concept DPT = conceptService.getConcept(781);
        Concept PCV = conceptService.getConcept(162342);
        Concept ROTA = conceptService.getConcept(83531);
        Concept MEASLESorRUBELLA = conceptService.getConcept(162586);
        Concept MEASLES = conceptService.getConcept(36);
        Concept YELLOW_FEVER = conceptService.getConcept(5864);

        String vaccination = null;
        String vaccineDate = null;
        SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
        
        if (wrapper.getVaccine().equals(BCG)) {
            vaccination = "BCG";
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 0) {
            vaccination = "OPV_AT_BIRTH";
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 1000) {
            vaccination = "OPV";
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 1) {
            vaccination = "OPV1";
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 2) {
            vaccination = "OPV2";
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 3) {
            vaccination = "OPV3";
        } else if (wrapper.getVaccine().equals(IPV) && wrapper.getSequenceNumber() == 1000) {
            vaccination = "IPV";
        } else if (wrapper.getVaccine().equals(IPV) && wrapper.getSequenceNumber() == 1) {
            vaccination = "IPV";
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 1000) {
            vaccination = "PCV10";
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 1) {
            vaccination = "PCV10-1";
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 2) {
            vaccination = "PCV10-2";
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 3) {
            vaccination = "PCV10-3";
        } else if (wrapper.getVaccine().equals(ROTA) && wrapper.getSequenceNumber() == 1000) {
            vaccination = "ROTA";
        } else if (wrapper.getVaccine().equals(ROTA) && wrapper.getSequenceNumber() == 1) {
            vaccination = "ROTA1";
        } else if (wrapper.getVaccine().equals(ROTA) && wrapper.getSequenceNumber() == 2) {
            vaccination = "ROTA2";
        } else if (wrapper.getVaccine().equals(MEASLES) && (wrapper.getSequenceNumber() == 1 || wrapper.getSequenceNumber() == 1000)) {
            vaccination = "MEASLES6";
        } else if (wrapper.getVaccine().equals(MEASLESorRUBELLA) && wrapper.getSequenceNumber() == 1000) {
            vaccination = "MEASLES9";
        } else if (wrapper.getVaccine().equals(MEASLESorRUBELLA) && wrapper.getSequenceNumber() == 1) {
            vaccination = "MEASLES9";
        } else if (wrapper.getVaccine().equals(MEASLESorRUBELLA) && wrapper.getSequenceNumber() == 2) {
            vaccination = "MEASLES18";
        } else if (wrapper.getVaccine().equals(YELLOW_FEVER) && (wrapper.getSequenceNumber() == 1 || wrapper.getSequenceNumber() == 1000)) {
            vaccination = "YELLOW_FEVER";
        }

        vaccineDate = df.format(wrapper.getVaccineDate());
        return SimpleObject.create(
                "vaccination", vaccination,
                "vaccinationDate", vaccineDate
        );
    }

    
    private SimpleObject extractHivTestInformation (List<Obs> obsList) {

        Integer finalHivTestResultConcept = 159427;
        Integer	testTypeConcept = 162084;
        Integer testStrategyConcept = 164956;
        Integer testFacilityCodeConcept = 162724;
        Integer healthProviderConcept = 1473;
        Integer healthProviderIdentifierConcept = 163161;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        Date testDate= obsList.get(0).getObsDatetime();
        User provider = obsList.get(0).getCreator();
        Concept testResult = null;
        String testType = null;
        String testFacility = null;
        Concept testStrategy = null;
        String providerName = null;
        String providerId = null;

        for(Obs obs:obsList) {

            if(obs.getEncounter().getForm().getUuid().equals(HTS_CONFIRMATORY_TEST_FORM_UUID)) {
                testType = "CONFIRMATORY";
            } else if(obs.getEncounter().getForm().getUuid().equals(HTS_INITIAL_TEST_FORM_UUID)) {
                testType = "SCREENING";
            }

            if (obs.getConcept().getConceptId().equals(testTypeConcept)) {
                testType = testTypeToStringConverter(obs.getValueCoded());
            }

            if (obs.getConcept().getConceptId().equals(finalHivTestResultConcept) ) {
                testResult = obs.getValueCoded();
            } else if (obs.getConcept().getConceptId().equals(testStrategyConcept) ) {
                testStrategy = obs.getValueCoded();
            } else if(obs.getConcept().getConceptId().equals(testFacilityCodeConcept)) {
                testFacility = obs.getValueText();
            } else if (obs.getConcept().getConceptId().equals(healthProviderConcept) ) {
                providerName = obs.getValueText();
            } else if(obs.getConcept().getConceptId().equals(healthProviderIdentifierConcept)) {
                providerId = obs.getValueText();
            }
        }
        return SimpleObject.create(
                "dateTested", dateFormat.format(testDate),
                "result", hivStatusConverter(testResult),
                "type", testType,
                "strategy" , testStrategyConverter(testStrategy),
                "facility", Utils.getLocationFromMFLCode(testFacility).getName()
        );
    }

    String testStrategyConverter (Concept key) {
        Map<Concept, String> hivTestStrategyList = new HashMap<Concept, String>();
        hivTestStrategyList.put(conceptService.getConcept(164163), "HP");
        hivTestStrategyList.put(conceptService.getConcept(164953), "NP");
        hivTestStrategyList.put(conceptService.getConcept(164954), "VI");
        hivTestStrategyList.put(conceptService.getConcept(164955), "VS");
        hivTestStrategyList.put(conceptService.getConcept(159938), "HB");
        hivTestStrategyList.put(conceptService.getConcept(159939), "MO");
        return hivTestStrategyList.get(key);
    }

    String testTypeToStringConverter(Concept key) {
        Map<Concept, String> testTypeList = new HashMap<Concept, String>();
        testTypeList.put(conceptService.getConcept(162080),"SCREENING");
        testTypeList.put(conceptService.getConcept(162082), "CONFIRMATORY");
        return testTypeList.get(key);

    }

    String hivStatusConverter (Concept key) {
        Map<Concept, String> hivStatusList = new HashMap<Concept, String>();
        hivStatusList.put(conceptService.getConcept(703), "POSITIVE");
        hivStatusList.put(conceptService.getConcept(664), "NEGATIVE");
        hivStatusList.put(conceptService.getConcept(1138), "INCONCLUSIVE");
        return hivStatusList.get(key);
    }

}
