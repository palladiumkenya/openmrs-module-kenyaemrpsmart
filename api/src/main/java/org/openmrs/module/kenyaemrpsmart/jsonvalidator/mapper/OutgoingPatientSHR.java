package org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.JSONPObject;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.RelationshipType;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemrpsmart.PsmartStore;
import org.openmrs.module.kenyaemrpsmart.api.PsmartService;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.utils.SHRUtils;
import org.openmrs.module.kenyaemrpsmart.kenyaemrUtils.Utils;
import org.openmrs.module.kenyaemrpsmart.metadata.SmartCardMetadata;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class OutgoingPatientSHR {

    private Integer patientID;
    private Patient patient;
    private PersonService personService;
    private PatientService patientService;
    private ObsService obsService;
    private ConceptService conceptService;
    private AdministrationService administrationService;
    private EncounterService encounterService;
    private String patientIdentifier;
    private PsmartService psmartService;

    String TELEPHONE_CONTACT = "b2c38640-2603-4629-aebd-3b54f33f1e3a";
    String CIVIL_STATUS_CONCEPT = "1054AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    String IMMUNIZATION_FORM_UUID = "b4f3859e-861c-4a63-bdff-eb7392030d47";
    String HTS_INITIAL_TEST_FORM_UUID = "402dc5d7-46da-42d4-b2be-f43ea4ad87b0";
    String HTS_CONFIRMATORY_TEST_FORM_UUID = "b08471f6-0892-4bf7-ab2b-bf79797b8ea4";
    String HEI_UNIQUE_NUMBER = "0691f522-dd67-4eeb-92c8-af5083baf338";
    String NATIONAL_ID = "49af6cdc-7968-4abb-bf46-de10d7f4859f";
    String UNIQUE_PATIENT_NUMBER = "05ee9cf4-7242-4a17-b4d4-00f707265c8a";
    String ANC_NUMBER = "161655AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    public OutgoingPatientSHR() {
        this.psmartService = Context.getService(PsmartService.class);
    }

    public OutgoingPatientSHR(Integer patientID) {
        this.patientID = patientID;
        this.patientService = Context.getPatientService();
        this.patient = patientService.getPatient(patientID);
        this.personService = Context.getPersonService();

        this.obsService = Context.getObsService();
        this.administrationService = Context.getAdministrationService();
        this.conceptService = Context.getConceptService();
        this.encounterService = Context.getEncounterService();

    }

    public OutgoingPatientSHR(String patientIdentifier) {
        this.patientIdentifier = patientIdentifier;
        this.patientService = Context.getPatientService();
        this.personService = Context.getPersonService();
        this.obsService = Context.getObsService();
        this.administrationService = Context.getAdministrationService();
        this.conceptService = Context.getConceptService();
        this.encounterService = Context.getEncounterService();
        setPatientUsingIdentifier();
    }

    private JsonNodeFactory getJsonNodeFactory() {
        final JsonNodeFactory factory = JsonNodeFactory.instance;
        return factory;
    }

    private ObjectNode getPatientName() {
        PersonName pn = patient.getPersonName();
        ObjectNode nameNode = getJsonNodeFactory().objectNode();
        nameNode.put("FIRST_NAME", pn.getGivenName());
        nameNode.put("MIDDLE_NAME", pn.getMiddleName());
        nameNode.put("LAST_NAME", pn.getFamilyName());
        return nameNode;
    }

    private String getSHRDateFormat() {
        return "yyyyMMdd";
    }

    private SimpleDateFormat getSimpleDateFormat(String pattern) {
        return new SimpleDateFormat(pattern);
    }

    private String getPatientPhoneNumber() {
        PersonAttributeType phoneNumberAttrType = personService.getPersonAttributeTypeByUuid(TELEPHONE_CONTACT);
        return patient.getAttribute(phoneNumberAttrType) != null ? patient.getAttribute(phoneNumberAttrType).getValue() : "";
    }

    public void setPatientUsingIdentifier() {

        if (patientIdentifier != null) {
            PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
            PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
            PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
            PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
            PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
            PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);

            List<Patient> patientsListWithIdentifier = patientService.getPatients(null, patientIdentifier.trim(),
                    Arrays.asList(GODS_NUMBER_TYPE, HEI_NUMBER_TYPE, CCC_NUMBER_TYPE, NATIONAL_ID_TYPE, SMART_CARD_SERIAL_NUMBER_TYPE, HTS_NUMBER_TYPE, GODS_NUMBER_TYPE), false);
            if (patientsListWithIdentifier.size() > 0) {
                this.patient = patientsListWithIdentifier.get(0);
            }

        }
    }

    private ArrayNode getHivTests() {

        // test concepts
        Concept finalHivTestResultConcept = conceptService.getConcept(159427);
        Concept testTypeConcept = conceptService.getConcept(162084);
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

        ArrayNode testList = getJsonNodeFactory().arrayNode();
        // loop through encounters and extract hiv test information
        for (Encounter encounter : htsEncounters) {
            List<Obs> obs = Utils.getEncounterObservationsForQuestions(patient, encounter, Arrays.asList(finalHivTestResultConcept, testTypeConcept, testStrategyConcept));
            testList.add(extractHivTestInformation(obs));
        }

        // append processed tests from card
        for (Encounter encounter : processedIncomingTests) {
            List<Obs> obs = Utils.getEncounterObservationsForQuestions(patient, encounter, Arrays.asList(finalHivTestResultConcept, testTypeConcept, testStrategyConcept, testFacilityCodeConcept, healthProviderConcept, healthProviderIdentifierConcept));
            testList.add(extractHivTestInformation(obs));
        }

        return testList;
    }

    private String getMaritalStatus() {
        Obs maritalStatus = Utils.getLatestObs(this.patient, CIVIL_STATUS_CONCEPT);
        String statusString = "";
        if (maritalStatus != null) {
            String MARRIED_MONOGAMOUS = "5555AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
            String MARRIED_POLYGAMOUS = "159715AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
            String DIVORCED = "1058AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
            String WIDOWED = "1059AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
            String LIVING_WITH_PARTNER = "1060AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
            String NEVER_MARRIED = "1057AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

            if (maritalStatus.getValueCoded().equals(MARRIED_MONOGAMOUS)) {
                statusString = "Married Monogamous";
            } else if (maritalStatus.getValueCoded().equals(MARRIED_POLYGAMOUS)) {
                statusString = "Married Polygamous";
            } else if (maritalStatus.getValueCoded().equals(DIVORCED)) {
                statusString = "Divorced";
            } else if (maritalStatus.getValueCoded().equals(WIDOWED)) {
                statusString = "Widowed";
            } else if (maritalStatus.getValueCoded().equals(LIVING_WITH_PARTNER)) {
                statusString = "Living with Partner";
            } else if (maritalStatus.getValueCoded().equals(NEVER_MARRIED)) {
                statusString = "Single";
            }

        }

        return statusString;
    }

    private ObjectNode getPatientAddress() {

        /**
         * county: personAddress.country
         * sub-county: personAddress.stateProvince
         * ward: personAddress.address4
         * landmark: personAddress.address2
         * postal address: personAddress.address1
         */

        Set<PersonAddress> addresses = patient.getAddresses();
        //patient address
        ObjectNode patientAddressNode = getJsonNodeFactory().objectNode();
        ObjectNode physicalAddressNode = getJsonNodeFactory().objectNode();
        String postalAddress = "";
        String county = "";
        String sub_county = "";
        String ward = "";
        String landMark = "";

        for (PersonAddress address : addresses) {
            if (address.getAddress1() != null) {
                postalAddress = address.getAddress1();
            }
            if (address.getCountry() != null) {
                county = address.getCountry() != null ? address.getCountry() : "";
            }

            if (address.getCountyDistrict() != null) {
                county = address.getCountyDistrict() != null ? address.getCountyDistrict() : "";
            }

            if (address.getStateProvince() != null) {
                sub_county = address.getStateProvince() != null ? address.getStateProvince() : "";
            }

            if (address.getAddress4() != null) {
                ward = address.getAddress4() != null ? address.getAddress4() : "";
            }
            if (address.getAddress2() != null) {
                landMark = address.getAddress2() != null ? address.getAddress2() : "";
            }

        }

        physicalAddressNode.put("COUNTY", county);
        physicalAddressNode.put("SUB_COUNTY", sub_county);
        physicalAddressNode.put("WARD", ward);
        physicalAddressNode.put("NEAREST_LANDMARK", landMark);

        //combine all addresses
        patientAddressNode.put("PHYSICAL_ADDRESS", physicalAddressNode);
        patientAddressNode.put("POSTAL_ADDRESS", postalAddress);

        return patientAddressNode;
    }


    public ObjectNode patientIdentification() {

        JsonNodeFactory factory = getJsonNodeFactory();
        ObjectNode patientSHR = factory.objectNode();
        if (patient != null) {

            String HEI_UNIQUE_NUMBER = "0691f522-dd67-4eeb-92c8-af5083baf338";
            String NATIONAL_ID = "49af6cdc-7968-4abb-bf46-de10d7f4859f";
            String UNIQUE_PATIENT_NUMBER = "05ee9cf4-7242-4a17-b4d4-00f707265c8a";
            String ANC_NUMBER = "161655AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

            PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
            PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
            PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
            PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
            PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
            PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);


            List<PatientIdentifier> identifierList = patientService.getPatientIdentifiers(null, Arrays.asList(HEI_NUMBER_TYPE, CCC_NUMBER_TYPE, NATIONAL_ID_TYPE, SMART_CARD_SERIAL_NUMBER_TYPE, HTS_NUMBER_TYPE, GODS_NUMBER_TYPE), null, Arrays.asList(this.patient), null);
            Map<String, String> patientIdentifiers = new HashMap<String, String>();
            //String facilityMFL = getFacilityMFLForIdentifiers();

            ObjectNode patientIdentificationNode = factory.objectNode();
            ArrayNode internalIdentifiers = factory.arrayNode();
            ObjectNode externalIdentifiers = factory.objectNode();

            for (PatientIdentifier identifier : identifierList) {
                PatientIdentifierType identifierType = identifier.getIdentifierType();

                ObjectNode element = factory.objectNode();
                if (identifierType.equals(HEI_NUMBER_TYPE)) {
                    patientIdentifiers.put("HEI_NUMBER", identifier.getIdentifier());

                    element.put("ID", identifier.getIdentifier());
                    element.put("IDENTIFIER_TYPE", "HEI_NUMBER");
                    element.put("ASSIGNING_AUTHORITY", "MCH");
                    element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

                } else if (identifierType.equals(CCC_NUMBER_TYPE)) {
                    patientIdentifiers.put("CCC_NUMBER", identifier.getIdentifier());
                    element.put("ID", identifier.getIdentifier());
                    element.put("IDENTIFIER_TYPE", "CCC_NUMBER");
                    element.put("ASSIGNING_AUTHORITY", "CCC");
                    element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

                } else if (identifierType.equals(NATIONAL_ID_TYPE)) {
                    patientIdentifiers.put("NATIONAL_ID", identifier.getIdentifier());
                    element.put("ID", identifier.getIdentifier());
                    element.put("IDENTIFIER_TYPE", "NATIONAL_ID");
                    element.put("ASSIGNING_AUTHORITY", "GOK");
                    element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

                } else if (identifierType.equals(SMART_CARD_SERIAL_NUMBER_TYPE)) {
                    patientIdentifiers.put("CARD_SERIAL_NUMBER", identifier.getIdentifier());
                    element.put("ID", identifier.getIdentifier());
                    element.put("IDENTIFIER_TYPE", "CARD_SERIAL_NUMBER");
                    element.put("ASSIGNING_AUTHORITY", "CARD_REGISTRY");
                    element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

                } else if (identifierType.equals(HTS_NUMBER_TYPE)) {
                    patientIdentifiers.put("HTS_NUMBER", identifier.getIdentifier());
                    element.put("ID", identifier.getIdentifier());
                    element.put("IDENTIFIER_TYPE", "HTS_NUMBER");
                    element.put("ASSIGNING_AUTHORITY", "HTS");
                    element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));
                }
                if (!element.isEmpty(null)) {
                    internalIdentifiers.add(element);
                }
                if (identifierType.equals(GODS_NUMBER_TYPE)) {
                    patientIdentifiers.put("GODS_NUMBER", identifier.getIdentifier());
                    externalIdentifiers.put("ID", identifier.getIdentifier());
                    externalIdentifiers.put("IDENTIFIER_TYPE", "GODS_NUMBER");
                    externalIdentifiers.put("ASSIGNING_AUTHORITY", "MPI");
                    externalIdentifiers.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));
                }

            }

            List<Obs> ancNumberObs = obsService.getObservationsByPersonAndConcept(patient, Context.getConceptService().getConceptByUuid(ANC_NUMBER));
            Obs ancNumber = null;
            if (ancNumberObs != null && !ancNumberObs.isEmpty())
                ancNumber = ancNumberObs.get(0);
            if (ancNumber != null) {
                //TODO: to look at this
                /*ObjectNode element = factory.objectNode();
                patientIdentifiers.put("ANC_NUMBER", ancNumber.getValueText());
                element.put("ID", ancNumber.getValueText());
                element.put("IDENTIFIER_TYPE", "ANC_NUMBER");
                element.put("ASSIGNING_AUTHORITY", "ANC");
                element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));
                internalIdentifiers.add(element);*/
            }

            // get other patient details

            String dob = getSimpleDateFormat(getSHRDateFormat()).format(this.patient.getBirthdate());
            String dobPrecision = patient.getBirthdateEstimated() ? "ESTIMATED" : "EXACT";
            String sex = patient.getGender();

            // get death details
            String deathDate;
            String deathIndicator;
            if (patient.getDeathDate() != null) {
                deathDate = getSimpleDateFormat(getSHRDateFormat()).format(patient.getDeathDate());
                deathIndicator = "Y";
            } else {
                deathDate = "";
                deathIndicator = "N";
            }


            patientIdentificationNode.put("INTERNAL_PATIENT_ID", internalIdentifiers);
            patientIdentificationNode.put("EXTERNAL_PATIENT_ID", externalIdentifiers);
            patientIdentificationNode.put("PATIENT_NAME", getPatientName());
            patientIdentificationNode.put("DATE_OF_BIRTH", dob);
            patientIdentificationNode.put("DATE_OF_BIRTH_PRECISION", dobPrecision);
            patientIdentificationNode.put("SEX", sex);
            patientIdentificationNode.put("DEATH_DATE", deathDate);
            patientIdentificationNode.put("DEATH_INDICATOR", deathIndicator);
            patientIdentificationNode.put("PATIENT_ADDRESS", getPatientAddress());
            patientIdentificationNode.put("PHONE_NUMBER", getPatientPhoneNumber());
            patientIdentificationNode.put("MARITAL_STATUS", getMaritalStatus());
            patientIdentificationNode.put("MOTHER_DETAILS", getMotherDetails());
            patientSHR.put("VERSION", "1.0.0");
            // append card details section
            ObjectNode value = factory.objectNode();
            value.put("STATUS", "ACTIVE");
            value.put("REASON", "");
            value.put("LAST_UPDATED", getSimpleDateFormat(getSHRDateFormat()).format(new Date()));
            value.put("LAST_UPDATED_FACILITY", Utils.getDefaultLocationMflCode(Utils.getDefaultLocation()));
            patientSHR.put("CARD_DETAILS", value);
            patientSHR.put("PATIENT_IDENTIFICATION", patientIdentificationNode);
            patientSHR.put("HIV_TEST", getHivTests());
            patientSHR.put("IMMUNIZATION", extractImmunizationInformation());
            patientSHR.put("NEXT_OF_KIN", getNextOfKinDetails());

            return patientSHR;
        } else {
            return patientSHR;
        }
    }

    private ArrayNode getNextOfKinDetails() {

        ArrayNode nextOfKinNode = getJsonNodeFactory().arrayNode();

        /**
         * TODO:
         * Skipping this for now. Next of kin details are persisted differently in the EMR. Further discussions required
        String NEXT_OF_KIN_ADDRESS = "7cf22bec-d90a-46ad-9f48-035952261294";
        String NEXT_OF_KIN_CONTACT = "342a1d39-c541-4b29-8818-930916f4c2dc";
        String NEXT_OF_KIN_NAME = "830bef6d-b01f-449d-9f8d-ac0fede8dbd3";
        String NEXT_OF_KIN_RELATIONSHIP = "d0aa9fd1-2ac5-45d8-9c5e-4317c622c8f5";

        PersonAttributeType nextOfKinNameAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_NAME);
        PersonAttributeType nextOfKinAddressAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_ADDRESS);
        PersonAttributeType nextOfKinPhoneContactAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_CONTACT);
        PersonAttributeType nextOfKinRelationshipAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_RELATIONSHIP);

        Set<PersonAttribute> existingAttributes = patient.getAttributes();*/


        return nextOfKinNode;

    }

    public ArrayNode getMotherIdentifiers(Patient patient) {

        String HEI_UNIQUE_NUMBER = "0691f522-dd67-4eeb-92c8-af5083baf338";
        String NATIONAL_ID = "49af6cdc-7968-4abb-bf46-de10d7f4859f";
        String UNIQUE_PATIENT_NUMBER = "05ee9cf4-7242-4a17-b4d4-00f707265c8a";
        String ANC_NUMBER = "161655AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
        PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
        PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
        PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
        PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
        PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);

        List<PatientIdentifier> identifierList = patientService.getPatientIdentifiers(null, Arrays.asList(CCC_NUMBER_TYPE, NATIONAL_ID_TYPE, SMART_CARD_SERIAL_NUMBER_TYPE, HTS_NUMBER_TYPE, GODS_NUMBER_TYPE), null, Arrays.asList(patient), null);
        Map<String, String> patientIdentifiers = new HashMap<String, String>();

        JsonNodeFactory factory = getJsonNodeFactory();
        ArrayNode internalIdentifiers = factory.arrayNode();

        for (PatientIdentifier identifier : identifierList) {
            PatientIdentifierType identifierType = identifier.getIdentifierType();
            ObjectNode element = factory.objectNode();

            if (identifierType.equals(CCC_NUMBER_TYPE)) {
                patientIdentifiers.put("CCC_NUMBER", identifier.getIdentifier());
                element.put("ID", identifier.getIdentifier());
                element.put("IDENTIFIER_TYPE", "CCC_NUMBER");
                element.put("ASSIGNING_AUTHORITY", "CCC");
                element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

            } else if (identifierType.equals(NATIONAL_ID_TYPE)) {
                patientIdentifiers.put("NATIONAL_ID", identifier.getIdentifier());
                element.put("ID", identifier.getIdentifier());
                element.put("IDENTIFIER_TYPE", "NATIONAL_ID");
                element.put("ASSIGNING_AUTHORITY", "GOK");
                element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

            } else if (identifierType.equals(SMART_CARD_SERIAL_NUMBER_TYPE)) {
                patientIdentifiers.put("CARD_SERIAL_NUMBER", identifier.getIdentifier());
                element.put("ID", identifier.getIdentifier());
                element.put("IDENTIFIER_TYPE", "CARD_SERIAL_NUMBER");
                element.put("ASSIGNING_AUTHORITY", "CARD_REGISTRY");
                element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

            } else if (identifierType.equals(HTS_NUMBER_TYPE)) {
                patientIdentifiers.put("HTS_NUMBER", identifier.getIdentifier());
                element.put("ID", identifier.getIdentifier());
                element.put("IDENTIFIER_TYPE", "HTS_NUMBER");
                element.put("ASSIGNING_AUTHORITY", "HTS");
                element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));

            } else if (identifierType.equals(GODS_NUMBER_TYPE)) {
                patientIdentifiers.put("GODS_NUMBER", identifier.getIdentifier());
                element.put("ID", identifier.getIdentifier());
                element.put("IDENTIFIER_TYPE", "GODS_NUMBER");
                element.put("ASSIGNING_AUTHORITY", "MPI");
                element.put("ASSIGNING_FACILITY", getFacilityMFLForIdentifiers(identifier.getLocation()));
            }

            internalIdentifiers.add(element);

        }

        List<Obs> ancNumberObs = obsService.getObservationsByPersonAndConcept(patient, Context.getConceptService().getConceptByUuid(ANC_NUMBER));
        Obs ancNumber = null;
        if (ancNumberObs != null && !ancNumberObs.isEmpty())
            ancNumber = ancNumberObs.get(0);
        if (ancNumber != null) {
            //TODO: TO LOOK AT IT
            /*ObjectNode element = factory.objectNode();
            patientIdentifiers.put("ANC_NUMBER", ancNumber.getValueText());
            element.put("ID", ancNumber.getValueText());
            element.put("IDENTIFIER_TYPE", "GODS_NUMBER");
            element.put("ASSIGNING_AUTHORITY", "MPI");
            element.put("ASSIGNING_FACILITY", facilityMFL);
            internalIdentifiers.add(element);*/
        }

        return internalIdentifiers;
    }

    private ObjectNode getMotherDetails() {

        // get relationships
        // mother name
        String motherName = "";
        ObjectNode mothersNameNode = getJsonNodeFactory().objectNode();
        ObjectNode motherDetails = getJsonNodeFactory().objectNode();
        ArrayNode motherIdenfierNode = getJsonNodeFactory().arrayNode();
        RelationshipType type = getParentChildType();

        List<Relationship> parentChildRel = personService.getRelationships(null, patient, getParentChildType());
        if (parentChildRel.isEmpty() && parentChildRel.size() == 0) {
            // try getting this from person attribute
            if (patient.getAttribute(4) != null) {
                motherName = patient.getAttribute(4).getValue();
                mothersNameNode.put("FIRST_NAME", motherName);
                mothersNameNode.put("MIDDLE_NAME", "");
                mothersNameNode.put("LAST_NAME", "");
            } else {
                mothersNameNode.put("FIRST_NAME", "");
                mothersNameNode.put("MIDDLE_NAME", "");
                mothersNameNode.put("LAST_NAME", "");
            }

        }

        // check if it is mothers
        Person mother = null;
        // a_is_to_b = 'Parent' and b_is_to_a = 'Child'
        for (Relationship relationship : parentChildRel) {

            if (patient.equals(relationship.getPersonB())) {
                if (relationship.getPersonA().getGender().equals("F")) {
                    mother = relationship.getPersonA();
                    break;
                }
            } else if (patient.equals(relationship.getPersonA())) {
                if (relationship.getPersonB().getGender().equals("F")) {
                    mother = relationship.getPersonB();
                    break;
                }
            }
        }
        if (mother != null) {
            //get mother name
            mothersNameNode.put("FIRST_NAME", mother.getGivenName());
            mothersNameNode.put("MIDDLE_NAME", mother.getMiddleName());
            mothersNameNode.put("LAST_NAME", mother.getFamilyName());

            // get identifiers
            motherIdenfierNode = getMotherIdentifiers(patientService.getPatient(mother.getPersonId()));
        }

        motherDetails.put("MOTHER_NAME", mothersNameNode);
        motherDetails.put("MOTHER_IDENTIFIER", motherIdenfierNode);

        return motherDetails;
    }

    protected RelationshipType getParentChildType() {
        return personService.getRelationshipTypeByUuid("8d91a210-c2cc-11de-8d13-0010c6dffd0f");

    }

    private JSONPObject getPatientIdentifiers() {
        return null;
    }

    private JSONPObject getHivTestDetails() {
        return null;
    }

    private String getFacilityMFLForIdentifiers(Location location) {
        return Utils.getDefaultLocationMflCode(location);
    }

    private JSONPObject getImmunizationDetails() {
        return null;
    }

    public int getPatientID() {
        return patientID;
    }

    public void setPatientID(int patientID) {
        this.patientID = patientID;
    }

    public String getPatientIdentifier() {
        return patientIdentifier;
    }

    public void setPatientIdentifier(String patientIdentifier) {
        this.patientIdentifier = patientIdentifier;
    }

    private ObjectNode extractHivTestInformation(List<Obs> obsList) {
        /**
         * "HIV_TEST": [
         {
         "DATE": "20180101",
         "RESULT": "POSITIVE/NEGATIVE/INCONCLUSIVE",
         "TYPE": "SCREENING/CONFIRMATORY",
         "FACILITY": "10829",
         "STRATEGY": "HP/NP/VI/VS/HB/MO/O", {164956: PITC(164163), Non PITC(164953), Integrated VCT(164954), Standalone vct(164955), Home Based testing(159938), Mobile outreach(159939)}
         "PROVIDER_DETAILS": {
         "NAME": "MATTHEW NJOROGE, MD",
         "ID": "12345-67890-abcde"
         }
         }
         ]
         */

        Integer finalHivTestResultConcept = 159427;
        Integer testTypeConcept = 162084;
        Integer testStrategyConcept = 164956;
        Integer testFacilityCodeConcept = 162724;
        Integer healthProviderConcept = 1473;
        Integer healthProviderIdentifierConcept = 163161;

        Date testDate = obsList.get(0).getObsDatetime();
        User provider = obsList.get(0).getCreator();
        String testResult = "";
        String testType = "";
        String testStrategy = "";
        String testFacility = null;
        String providerName = null;
        String providerId = null;

        ObjectNode testNode = getJsonNodeFactory().objectNode();

        for (Obs obs : obsList) {

            if (obs.getEncounter().getForm().getUuid().equals(HTS_CONFIRMATORY_TEST_FORM_UUID)) {
                testType = "CONFIRMATORY";
            } else if (obs.getEncounter().getForm().getUuid().equals(HTS_INITIAL_TEST_FORM_UUID)) {
                testType = "SCREENING";
            }

            if (obs.getConcept().getConceptId().equals(testTypeConcept)) {
                testType = testTypeToStringConverter(obs.getValueCoded());
            }

            if (obs.getConcept().getConceptId().equals(finalHivTestResultConcept)) {
                testResult = hivStatusConverter(obs.getValueCoded());
            } /*else if (obs.getConcept().getConceptId().equals(testTypeConcept )) {
                testType = testTypeConverter(obs.getValueCoded());
            }*/ else if (obs.getConcept().getConceptId().equals(testStrategyConcept)) {
                testStrategy = testStrategyConverter(obs.getValueCoded());
            } else if (obs.getConcept().getConceptId().equals(testFacilityCodeConcept)) {
                testFacility = obs.getValueText();
            } else if (obs.getConcept().getConceptId().equals(healthProviderConcept)) {
                providerName = obs.getValueText();
            } else if (obs.getConcept().getConceptId().equals(healthProviderIdentifierConcept)) {
                providerId = obs.getValueText();
            }
        }
        testNode.put("DATE", getSimpleDateFormat(getSHRDateFormat()).format(testDate));
        testNode.put("RESULT", testResult);
        testNode.put("TYPE", testType);
        testNode.put("STRATEGY", testStrategy);
        testNode.put("FACILITY", Utils.getDefaultLocationMflCode(Utils.getLocationFromMFLCode(testFacility)));
        testNode.put("PROVIDER_DETAILS", (providerName !=null && providerId != null) ?  getProviderDetails(null, providerName, providerId) :  getProviderDetails(provider, null, null));

        return testNode;

    }

    String testTypeToStringConverter(Concept key) {
        Map<Concept, String> testTypeList = new HashMap<Concept, String>();
        testTypeList.put(conceptService.getConcept(162080), "SCREENING");
        testTypeList.put(conceptService.getConcept(162082), "CONFIRMATORY");
        return testTypeList.get(key);

    }
    String testStrategyConverter(Concept key) {
        Map<Concept, String> hivTestStrategyList = new HashMap<Concept, String>();
        hivTestStrategyList.put(conceptService.getConcept(164163), "HP");
        hivTestStrategyList.put(conceptService.getConcept(164953), "NP");
        hivTestStrategyList.put(conceptService.getConcept(164954), "VI");
        hivTestStrategyList.put(conceptService.getConcept(164955), "VS");
        hivTestStrategyList.put(conceptService.getConcept(159938), "HB");
        hivTestStrategyList.put(conceptService.getConcept(159939), "MO");
        return hivTestStrategyList.get(key);
    }

    /**
     * comparison with 1000 denote when a vaccine did not have sequence number documented as required
     *
     * @param wrapper
     * @return node for a vaccine
     */
    ObjectNode vaccineConverterNode(ImmunizationWrapper wrapper) {

        Concept BCG = conceptService.getConcept(886);
        Concept OPV = conceptService.getConcept(783);
        Concept IPV = conceptService.getConcept(1422);
        Concept DPT = conceptService.getConcept(781);
        Concept PCV = conceptService.getConcept(162342);
        Concept ROTA = conceptService.getConcept(83531);
        Concept MEASLESorRUBELLA = conceptService.getConcept(162586);
        Concept MEASLES = conceptService.getConcept(36);
        Concept YELLOW_FEVER = conceptService.getConcept(5864);

        ObjectNode node = getJsonNodeFactory().objectNode();
        if (wrapper.getVaccine().equals(BCG)) {
            node.put("NAME", "BCG");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 0) {
            node.put("NAME", "OPV_AT_BIRTH");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 1000) {
            node.put("NAME", "OPV");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 1) {
            node.put("NAME", "OPV1");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 2) {
            node.put("NAME", "OPV2");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(OPV) && wrapper.getSequenceNumber() == 3) {
            node.put("NAME", "OPV3");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(IPV) && wrapper.getSequenceNumber() == 1000) {
            node.put("NAME", "IPV");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(IPV) && wrapper.getSequenceNumber() == 1) {
            node.put("NAME", "IPV");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(DPT) && wrapper.getSequenceNumber() == 1000) {
            node.put("NAME", "DPT/Hep_B/Hib");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(DPT) && wrapper.getSequenceNumber() == 1) {
            node.put("NAME", "DPT/Hep_B/Hib_1");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(DPT) && wrapper.getSequenceNumber() == 2) {
            node.put("NAME", "DPT/Hep_B/Hib_2");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(DPT) && wrapper.getSequenceNumber() == 3) {
            node.put("NAME", "DPT/Hep_B/Hib_3");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 1000) {
            node.put("NAME", "PCV10");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 1) {
            node.put("NAME", "PCV10-1");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 2) {
            node.put("NAME", "PCV10-2");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(PCV) && wrapper.getSequenceNumber() == 3) {
            node.put("NAME", "PCV10-3");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(ROTA) && wrapper.getSequenceNumber() == 1000) {
            node.put("NAME", "ROTA");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(ROTA) && wrapper.getSequenceNumber() == 1) {
            node.put("NAME", "ROTA1");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(ROTA) && wrapper.getSequenceNumber() == 2) {
            node.put("NAME", "ROTA2");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(MEASLES) && (wrapper.getSequenceNumber() == 1 || wrapper.getSequenceNumber() == 1000)) {
            node.put("NAME", "MEASLES6");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(MEASLESorRUBELLA) && wrapper.getSequenceNumber() == 1000) {
            node.put("NAME", "MEASLES9");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(MEASLESorRUBELLA) && wrapper.getSequenceNumber() == 1) {
            node.put("NAME", "MEASLES9");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(MEASLESorRUBELLA) && wrapper.getSequenceNumber() == 2) {
            node.put("NAME", "MEASLES18");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        } else if (wrapper.getVaccine().equals(YELLOW_FEVER) && (wrapper.getSequenceNumber() == 1 || wrapper.getSequenceNumber() == 1000)) {
            node.put("NAME", "YELLOW_FEVER");
            node.put("DATE_ADMINISTERED", getSimpleDateFormat(getSHRDateFormat()).format(wrapper.getVaccineDate()));
        }

        return node;
    }

    String testTypeConverter(Concept key) {
        Map<Concept, String> testTypeList = new HashMap<Concept, String>();
        testTypeList.put(conceptService.getConcept(162080), "SCREENING");
        testTypeList.put(conceptService.getConcept(162082), "CONFIRMATORY");
        return testTypeList.get(key);

    }

    String hivStatusConverter(Concept key) {
        Map<Concept, String> hivStatusList = new HashMap<Concept, String>();
        hivStatusList.put(conceptService.getConcept(703), "POSITIVE");
        hivStatusList.put(conceptService.getConcept(664), "NEGATIVE");
        hivStatusList.put(conceptService.getConcept(1138), "INCONCLUSIVE");
        return hivStatusList.get(key);
    }

    private ObjectNode getProviderDetails(User user, String username, String userId) {
        ObjectNode providerNameNode = getJsonNodeFactory().objectNode();
        if(user != null) {

            providerNameNode.put("NAME", user.getPersonName().getFullName());
            providerNameNode.put("ID", user.getSystemId());
            return providerNameNode;
        } else if(username != null && userId != null) {
            providerNameNode.put("NAME", username);
            providerNameNode.put("ID", userId);
            return providerNameNode;
        }
        providerNameNode.put("NAME", "");
        providerNameNode.put("ID", "");
        return providerNameNode;
    }

    private ArrayNode extractImmunizationInformation() {

        Concept groupingConcept = conceptService.getConcept(1421);
        Concept vaccineConcept = conceptService.getConcept(984);
        Concept sequenceNumber = conceptService.getConcept(1418);
        Concept dateGiven = conceptService.getConcept(1282);
        Concept dateGivenConcept2 = conceptService.getConcept(1410);

        ArrayNode immunizationNode = getJsonNodeFactory().arrayNode();
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
        for (Encounter encounter : immunizationEncounters) {
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
            for (Obs group : obs) {
                ImmunizationWrapper groupWrapper;
                Concept vaccine = null;
                Integer sequence = 1000;
                Date vaccineDate = null; //obs.get(0).getObsDatetime();
                Set<Obs> members = group.getGroupMembers();
                // iterate through obs for a particular group
                for (Obs memberObs : members) {
                    if (memberObs.getConcept().equals(vaccineConcept)) {
                        vaccine = memberObs.getValueCoded();
                    } else if (memberObs.getConcept().equals(sequenceNumber)) {
                        sequence = memberObs.getValueNumeric() != null ? memberObs.getValueNumeric().intValue() : 1000; // put 1000 for null
                    } else if (memberObs.getConcept().equals(dateGiven) || memberObs.getConcept().equals(dateGivenConcept2)) {
                        vaccineDate = memberObs.getValueDate();
                    }
                }
                immunizationList.add(new ImmunizationWrapper(vaccine, sequence, vaccineDate));
            }
        }

        for (ImmunizationWrapper thisWrapper : immunizationList) {
            immunizationNode.add(vaccineConverterNode(thisWrapper));
        }
        return immunizationNode;
    }

    public PsmartStore saveRegistryEntry(PsmartStore psmartStore) {
        return psmartService.savePsmartStoreObject(psmartStore);
    }

    public PsmartStore getRegistryEntry(String uuid) {
        return psmartService.getPsmartStoreByUuid(uuid);
    }

}
