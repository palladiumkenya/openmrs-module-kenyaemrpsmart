package org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Attributable;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.User;
import org.openmrs.Visit;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.logic.op.In;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.utils.SHRUtils;
import org.openmrs.module.kenyaemrpsmart.kenyaemrUtils.Utils;
import org.openmrs.module.kenyaemrpsmart.metadata.SmartCardMetadata;
import org.openmrs.module.metadatadeploy.MetadataUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class IncomingPatientSHR {

    private Patient patient;
    private PersonService personService;
    private PatientService patientService;
    private ObsService obsService;
    private ConceptService conceptService;
    private AdministrationService administrationService;
    private EncounterService encounterService;
    private String incomingSHR;

    String TELEPHONE_CONTACT = "b2c38640-2603-4629-aebd-3b54f33f1e3a";
    String CIVIL_STATUS_CONCEPT = "1054AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    String PSMART_ENCOUNTER_TYPE_UUID = "9bc15e94-2794-11e8-b467-0ed5f89f718b";
    String HEI_UNIQUE_NUMBER = "0691f522-dd67-4eeb-92c8-af5083baf338";
    String NATIONAL_ID = "49af6cdc-7968-4abb-bf46-de10d7f4859f";
    String UNIQUE_PATIENT_NUMBER = "05ee9cf4-7242-4a17-b4d4-00f707265c8a";
    String ANC_NUMBER = "161655AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    String HTS_CONFIRMATORY_TEST_FORM_UUID = "b08471f6-0892-4bf7-ab2b-bf79797b8ea4";
    String HTS_INITIAL_TEST_FORM_UUID = "402dc5d7-46da-42d4-b2be-f43ea4ad87b0";

    protected Log logger = LogFactory.getLog(getClass());


    public IncomingPatientSHR(String shr) {

        this.patientService = Context.getPatientService();
        this.personService = Context.getPersonService();
        this.obsService = Context.getObsService();
        this.administrationService = Context.getAdministrationService();
        this.conceptService = Context.getConceptService();
        this.encounterService = Context.getEncounterService();
        this.incomingSHR = shr;
    }

    public IncomingPatientSHR(Integer patientID) {

        this.patientService = Context.getPatientService();
        this.personService = Context.getPersonService();
        this.obsService = Context.getObsService();
        this.administrationService = Context.getAdministrationService();
        this.conceptService = Context.getConceptService();
        this.encounterService = Context.getEncounterService();
        this.patient = patientService.getPatient(patientID);
    }

    public String processIncomingSHR() {

        //Patient existingPatient = checkIfPatientExists();
        String msg = "";
        Patient patient = checkIfPatientExists();
        if (patient != null) {
            this.patient = patient;
        } else {
            createOrUpdatePatient();
        }

        savePersonAddresses();
        savePersonAttributes();
        addOtherPatientIdentifiers();


        try {
            patientService.savePatient(this.patient);

            try {
                saveHivTestData();
                try {
                    saveImmunizationData();
                    return "Successfully processed P-Smart Immunization data";
                } catch (Exception e) {
                    return "There was an error processing immunization data";
                }

                //checkinPatient();
            } catch (Exception ex) {
                msg = "There was an error processing P-Smart HIV Test Data";
            }

        } catch (Exception e) {
            e.printStackTrace();
            msg = "There was an error processing patient SHR";
        }

        //}

        return msg;
    }

    public String patientExists() {
        return checkIfPatientExists() != null ? checkIfPatientExists().getGivenName().concat(" ").concat(checkIfPatientExists().getFamilyName()) : "Client doesn't exist in the system";
    }

    private void checkinPatient() {
        Visit newVisit = new Visit();
        newVisit.setPatient(patient);
        newVisit.setStartDatetime(new Date());
        newVisit.setVisitType(MetadataUtils.existing(VisitType.class, SmartCardMetadata._VisitType.OUTPATIENT));
        Context.getVisitService().saveVisit(newVisit);

    }

    public String assignCardSerialIdentifier(String identifier, String encryptedSHR) {
        PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);

        if (identifier != null) {

            // check if no other patient has same identifier
            List<Patient> patientsAssignedId = patientService.getPatients(null, identifier.trim(), Arrays.asList(SMART_CARD_SERIAL_NUMBER_TYPE), false);
            if (patientsAssignedId.size() > 0) {
                return "Identifier already assigned";
            }

            // check if patient already has the identifier
            List<PatientIdentifier> existingIdentifiers = patient.getPatientIdentifiers(SMART_CARD_SERIAL_NUMBER_TYPE);

            boolean found = false;
            for (PatientIdentifier id : existingIdentifiers) {
                if (id.getIdentifier().equals(identifier.trim())) {
                    found = true;
                    return "Client already assigned the card serial";
                }
            }


            if (!found) {
                PatientIdentifier patientIdentifier = new PatientIdentifier();
                patientIdentifier.setIdentifierType(SMART_CARD_SERIAL_NUMBER_TYPE);
                patientIdentifier.setLocation(Utils.getDefaultLocation());
                patientIdentifier.setIdentifier(identifier.trim());
                patient.addIdentifier(patientIdentifier);
                patientService.savePatient(patient);
                OutgoingPatientSHR shr = new OutgoingPatientSHR(patient.getPatientId());
                return shr.patientIdentification().toString();
            }
        }
        return "No identifier provided";
    }

    public Patient checkIfPatientExists() {

        PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
        PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
        PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
        PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
        PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
        PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);

        String shrGodsNumber = SHRUtils.getSHR(incomingSHR).pATIENT_IDENTIFICATION.eXTERNAL_PATIENT_ID.iD;
        List<Patient> patientsAssignedGodsNumber = patientService.getPatients(null, shrGodsNumber.trim(), Arrays.asList(GODS_NUMBER_TYPE), false);
        if (patientsAssignedGodsNumber.size() > 0) {
            return patientsAssignedGodsNumber.get(0);
        }

        for (int x = 0; x < SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID.length; x++) {

            String idType = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].iDENTIFIER_TYPE;
            PatientIdentifierType identifierType = null;

            String identifier = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].iD;

            if (idType.equals("ANC_NUMBER")) {
                // get patient with the identifier

                List<Obs> obs = obsService.getObservations(
                        null,
                        null,
                        Arrays.asList(conceptService.getConceptByUuid(ANC_NUMBER)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false
                );
                for (Obs ancNo : obs) {
                    if (ancNo.getValueText().equals(identifier.trim()))
                        return (Patient) ancNo.getPerson();
                }

            } else {
                if (idType.equals("HEI_NUMBER")) {
                    identifierType = HEI_NUMBER_TYPE;
                } else if (idType.equals("CCC_NUMBER")) {
                    identifierType = CCC_NUMBER_TYPE;
                } else if (idType.equals("NATIONAL_ID")) {
                    identifierType = NATIONAL_ID_TYPE;
                } else if (idType.equals("CARD_SERIAL_NUMBER")) {
                    identifierType = SMART_CARD_SERIAL_NUMBER_TYPE;
                } else if (idType.equals("HTS_NUMBER")) {
                    identifierType = HTS_NUMBER_TYPE;
                }

                List<Patient> patientsAlreadyAssigned = patientService.getPatients(null, identifier.trim(), Arrays.asList(identifierType), false);
                if (patientsAlreadyAssigned.size() > 0) {
                    return patientsAlreadyAssigned.get(0);
                }
            }

        }


        return null;
    }

    public boolean checkIfPatientHasIdentifier(Patient patient, PatientIdentifierType identifierType, String identifier) {

        List<Patient> patientsWithIdentifierList = patientService.getPatients(null, identifier.trim(), Arrays.asList(identifierType), false);
        if (patientsWithIdentifierList.size() > 0) {
            return patientsWithIdentifierList.get(0).equals(patient);
        }

        return false;
    }

    private void createOrUpdatePatient() {

        String fName = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_NAME.fIRST_NAME;
        String mName = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_NAME.mIDDLE_NAME;
        String lName = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_NAME.lAST_NAME;
        String dobString = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.dATE_OF_BIRTH;
        String dobPrecision = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.dATE_OF_BIRTH_PRECISION;
        Date dob = null;
        try {
            dob = new SimpleDateFormat("yyyyMMdd").parse(dobString);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        String gender = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.sEX;

        this.patient = new Patient();
        this.patient.setGender(gender);
        this.patient.addName(new PersonName(fName, mName, lName));
        if (dob != null) {
            this.patient.setBirthdate(dob);
        }

        if (dobPrecision != null && dobPrecision.equals("ESTIMATED")) {
            this.patient.setBirthdateEstimated(true);
        } else if (dobPrecision != null && dobPrecision.equals("EXACT")) {
            this.patient.setBirthdateEstimated(false);
        }

    }

    private void addOpenMRSIdentifier() {
        PatientIdentifier openMRSID = generateOpenMRSID();
        patient.addIdentifier(openMRSID);
    }

    private void addOtherPatientIdentifiers() {

        PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
        PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
        PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
        PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
        PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
        PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);

        // extract GOD's Number
        String shrGodsNumber = SHRUtils.getSHR(incomingSHR).pATIENT_IDENTIFICATION.eXTERNAL_PATIENT_ID.iD;
        if (shrGodsNumber != null) {
            if(!checkIfPatientHasIdentifier(this.patient, GODS_NUMBER_TYPE, shrGodsNumber.trim())) {
                String godsNumberAssigningFacility = SHRUtils.getSHR(incomingSHR).pATIENT_IDENTIFICATION.eXTERNAL_PATIENT_ID.aSSIGNING_FACILITY;
                PatientIdentifier godsNumber = new PatientIdentifier();
                godsNumber.setIdentifierType(GODS_NUMBER_TYPE);
                godsNumber.setIdentifier(shrGodsNumber);
                godsNumber.setLocation(Utils.getDefaultLocation());
                //godsNumber.setLocation(new Location(Integer.parseInt(godsNumberAssigningFacility)));
                patient.addIdentifier(godsNumber);
            }

        }

        // OpenMRS ID
        PatientIdentifierType openmrsIDType = Context.getPatientService().getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76");
        PatientIdentifier existingIdentifier = patient.getPatientIdentifier(openmrsIDType);

        if(existingIdentifier != null) {

        } else {
            addOpenMRSIdentifier();
        }

        // process internal identifiers

        for (int x = 0; x < SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID.length; x++) {

            String idType = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].iDENTIFIER_TYPE;
            PatientIdentifierType identifierType = null;
            String identifier = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].iD;

            if (idType.equals("ANC_NUMBER")) {
                // first save patient
               /* patientService.savePatient(this.patient);
                Obs ancNumberObs = new Obs();
                ancNumberObs.setConcept(conceptService.getConceptByUuid(ANC_NUMBER));
                ancNumberObs.setValueText(identifier);
                ancNumberObs.setPerson(this.patient);
                ancNumberObs.setObsDatetime(new Date());
                obsService.saveObs(ancNumberObs, null);*/

            } else {
                if (idType.equals("HEI_NUMBER")) {
                    identifierType = HEI_NUMBER_TYPE;
                } else if (idType.equals("CCC_NUMBER")) {
                    identifierType = CCC_NUMBER_TYPE;
                } else if (idType.equals("NATIONAL_ID")) {
                    identifierType = NATIONAL_ID_TYPE;
                } else if (idType.equals("CARD_SERIAL_NUMBER")) {
                    identifierType = SMART_CARD_SERIAL_NUMBER_TYPE;
                } else if (idType.equals("HTS_NUMBER")) {
                    identifierType = HTS_NUMBER_TYPE;
                } else {
                    continue;
                }

                if(!checkIfPatientHasIdentifier(this.patient, identifierType, identifier)) {
                    PatientIdentifier patientIdentifier = new PatientIdentifier();
                    String assigningFacility = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.iNTERNAL_PATIENT_ID[x].aSSIGNING_FACILITY;
                    patientIdentifier.setIdentifierType(identifierType);
                    patientIdentifier.setIdentifier(identifier);
                    patientIdentifier.setLocation(Utils.getDefaultLocation());

                    //identifierSet.add(patientIdentifier);
                    patient.addIdentifier(patientIdentifier);

                }
            }

        }
        Iterator<PatientIdentifier> pIdentifiers = patient.getIdentifiers().iterator();
        PatientIdentifier currentIdentifier = null;
        PatientIdentifier preferredIdentifier = null;
        while (pIdentifiers.hasNext()) {
            currentIdentifier = pIdentifiers.next();
            if (currentIdentifier.isPreferred()) {
                if (preferredIdentifier != null) { // if there's a preferred address already exists, make it preferred=false
                    preferredIdentifier.setPreferred(false);
                }
                preferredIdentifier = currentIdentifier;
            }
        }
        if ((preferredIdentifier == null) && (currentIdentifier != null)) { // No preferred address. Make the last address entry as preferred.
            currentIdentifier.setPreferred(true);
        }


    }

    Concept testTypeConverter(String key) {
        Map<String, Concept> testTypeList = new HashMap<String, Concept>();
        testTypeList.put("SCREENING", conceptService.getConcept(162080));
        testTypeList.put("CONFIRMATORY", conceptService.getConcept(162082));
        return testTypeList.get(key);

    }

    String testTypeToStringConverter(Concept key) {
        Map<Concept, String> testTypeList = new HashMap<Concept, String>();
        testTypeList.put(conceptService.getConcept(162080),"SCREENING");
        testTypeList.put(conceptService.getConcept(162082), "CONFIRMATORY");
        return testTypeList.get(key);

    }

    Concept hivStatusConverter(String key) {
        Map<String, Concept> hivStatusList = new HashMap<String, Concept>();
        hivStatusList.put("POSITIVE", conceptService.getConcept(703));
        hivStatusList.put("NEGATIVE", conceptService.getConcept(664));
        hivStatusList.put("INCONCLUSIVE", conceptService.getConcept(1138));
        return hivStatusList.get(key);
    }

    Concept testStrategyConverter(String key) {
        Map<String, Concept> hivTestStrategyList = new HashMap<String, Concept>();
        hivTestStrategyList.put("HP", conceptService.getConcept(164163));
        hivTestStrategyList.put("NP", conceptService.getConcept(164953));
        hivTestStrategyList.put("VI", conceptService.getConcept(164954));
        hivTestStrategyList.put("VS", conceptService.getConcept(164955));
        hivTestStrategyList.put("HB", conceptService.getConcept(159938));
        hivTestStrategyList.put("MO", conceptService.getConcept(159939));
        return hivTestStrategyList.get(key);
    }

    private void savePersonAttributes() {
        String tELEPHONE = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pHONE_NUMBER;
        PersonAttributeType type = personService.getPersonAttributeTypeByUuid(TELEPHONE_CONTACT);
        if (tELEPHONE != null) {
            PersonAttribute attribute = new PersonAttribute(type, tELEPHONE);

            try {
                Object hydratedObject = attribute.getHydratedObject();
                if (hydratedObject == null || "".equals(hydratedObject.toString())) {
                    // if null is returned, the value should be blanked out
                    attribute.setValue("");
                } else if (hydratedObject instanceof Attributable) {
                    attribute.setValue(((Attributable) hydratedObject).serialize());
                } else if (!hydratedObject.getClass().getName().equals(type.getFormat())) {
                    // if the classes doesn't match the format, the hydration failed somehow
                    // TODO change the PersonAttribute.getHydratedObject() to not swallow all errors?
                    throw new APIException();
                }
            }
            catch (APIException e) {
                //.warn("Got an invalid value: " + value + " while setting personAttributeType id #" + paramName, e);
                // setting the value to empty so that the user can reset the value to something else
                attribute.setValue("");
            }
            patient.addAttribute(attribute);
        }

    }

    private void savePersonAddresses() {
        /**
         * county: personAddress.country
         * sub-county: personAddress.stateProvince
         * ward: personAddress.address4
         * landmark: personAddress.address2
         * postal address: personAddress.address1
         */

        String postaladdress = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_ADDRESS.pOSTAL_ADDRESS;
        String vILLAGE = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_ADDRESS.pHYSICAL_ADDRESS.vILLAGE;
        String wARD = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_ADDRESS.pHYSICAL_ADDRESS.wARD;
        String sUBCOUNTY = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_ADDRESS.pHYSICAL_ADDRESS.sUB_COUNTY;
        String cOUNTY = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_ADDRESS.pHYSICAL_ADDRESS.cOUNTY;
        String nEAREST_LANDMARK = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.pATIENT_ADDRESS.pHYSICAL_ADDRESS.nEAREST_LANDMARK;

        Set<PersonAddress> patientAddress = patient.getAddresses();
        //Set<PersonAddress> patientAddress = new TreeSet<PersonAddress>();
        if(patientAddress.size() > 0) {
            for (PersonAddress address : patientAddress) {
                if (cOUNTY != null) {
                    address.setCountry(cOUNTY);
                }
                if (sUBCOUNTY != null) {
                    address.setStateProvince(sUBCOUNTY);
                }
                if (wARD != null) {
                    address.setAddress4(wARD);
                }
                if (nEAREST_LANDMARK != null) {
                    address.setAddress2(nEAREST_LANDMARK);
                }
                if (vILLAGE != null) {
                    address.setAddress2(vILLAGE);
                }
                if (postaladdress != null) {
                    address.setAddress1(postaladdress);
                }
                patient.addAddress(address);
            }
        } else {
            PersonAddress pa = new PersonAddress();
            if (cOUNTY != null) {
                pa.setCountry(cOUNTY);
            }
            if (sUBCOUNTY != null) {
                pa.setStateProvince(sUBCOUNTY);
            }
            if (wARD != null) {
                pa.setAddress4(wARD);
            }
            if (nEAREST_LANDMARK != null) {
                pa.setAddress2(nEAREST_LANDMARK);
            }
            if (vILLAGE != null) {
                pa.setAddress2(vILLAGE);
            }
            if (postaladdress != null) {
                pa.setAddress1(postaladdress);
            }
            patient.addAddress(pa);
        }

    }


    private void saveHivTestData() {

        Integer finalHivTestResultConcept = 159427;
        Integer testTypeConcept = 162084;
        Integer testStrategyConcept = 164956;
        Integer healthProviderConcept = 1473;
        Integer healthFacilityNameConcept = 162724;
        Integer healthProviderIdentifierConcept = 163161;

        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
        List<SmartCardHivTest> existingTests = getHivTests();
        System.out.println("Fooooooooooooooooooooound: " + existingTests.size());
        for (int i = 0; i < SHRUtils.getSHR(this.incomingSHR).hIV_TEST.length; i++) {

            String dateStr = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].dATE;
            String result = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].rESULT;
            String type = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].tYPE;
            String facility = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].fACILITY;
            String strategy = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].sTRATEGY;
            String providerDetails = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].pROVIDER_DETAILS.nAME;
            String providerId = SHRUtils.getSHR(this.incomingSHR).hIV_TEST[i].pROVIDER_DETAILS.iD;

            Date date = null;

            try {
                date = new SimpleDateFormat("yyyyMMdd").parse(dateStr);
            } catch (ParseException ex) {

                ex.printStackTrace();
            }
            // skip all tests done in the facility
            if(Integer.valueOf(facility) == 108900) {//temp value for this facility
                continue;
            }

            boolean exists = false;
            for(SmartCardHivTest hivTest : existingTests) {

                System.out.println("Answers outside ==================== : \n Test Dates: db date: " +
                        df.format(hivTest.getDateTested()) + ", incoming date: " + dateStr + " ,\n Test strategy: incoming: " +
                        testStrategyConverter(strategy) + ", db strategy: " + hivTest.getStrategy() +",\n Type: db: " +
                        hivTest.getType() + ", incoming: " + type +", \n results: " +
                        hivStatusConverter(result) + ", facility:facility =" + Integer.valueOf(facility) + ": " + hivTest.getFacility());

                System.out.println("Comparisons: \n"
                + "facility code: " + (facility.equals(hivTest.getFacility())) + "\n "
                + "test date:" + (dateStr.equals(df.format(hivTest.getDateTested()))) + "\n "
                + "hiv status: " + (hivStatusConverter(result).equals(hivTest.getResult())) + "\n"
                + "strategy: " + (testStrategyConverter(strategy).equals(hivTest.getStrategy())) + "\n"
                + "type: " + (type.trim().equals(hivTest.getType())));

                if(facility.equals(hivTest.getFacility())
                        && dateStr.equals(df.format(hivTest.getDateTested()))
                        && hivStatusConverter(result).equals(hivTest.getResult())
                        && testStrategyConverter(strategy).equals(hivTest.getStrategy())
                        && type.trim().equals(hivTest.getType())) {
                    exists = true;

                    System.out.println("Answers inside ==================== : " + hivTest.getDateTested() + " ," + testStrategyConverter(strategy) + ", " + hivStatusConverter(result));

                    break;

                }
            }

            if(exists) {
                continue;
            }

            Encounter enc = new Encounter();
            Location location = Context.getLocationService().getLocation(1);
            enc.setLocation(location);
            enc.setEncounterType(Context.getEncounterService().getEncounterTypeByUuid(SmartCardMetadata._EncounterType.EXTERNAL_PSMART_DATA));
            enc.setEncounterDatetime(date);
            enc.setPatient(patient);
            enc.addProvider(Context.getEncounterService().getEncounterRole(1), Context.getProviderService().getProvider(1));
            enc.setForm(Context.getFormService().getFormByUuid(SmartCardMetadata._Form.PSMART_HIV_TEST));


            // build observations
            // test result
            Obs o = new Obs();
            o.setConcept(conceptService.getConcept(finalHivTestResultConcept));
            o.setDateCreated(new Date());
            o.setCreator(Context.getUserService().getUser(1));
            o.setLocation(location);
            o.setObsDatetime(date);
            o.setPerson(this.patient);
            o.setValueCoded(hivStatusConverter(result.trim()));

            // test type
            Obs o1 = new Obs();
            o1.setConcept(conceptService.getConcept(testTypeConcept));
            o1.setDateCreated(new Date());
            o1.setCreator(Context.getUserService().getUser(1));
            o1.setLocation(location);
            o1.setObsDatetime(date);
            o1.setPerson(this.patient);
            o1.setValueCoded(testTypeConverter(type.trim()));

            // test strategy
            Obs o2 = new Obs();
            o2.setConcept(conceptService.getConcept(testStrategyConcept));
            o2.setDateCreated(new Date());
            o2.setCreator(Context.getUserService().getUser(1));
            o2.setLocation(location);
            o2.setObsDatetime(date);
            o2.setPerson(this.patient);
            o2.setValueCoded(testStrategyConverter(strategy.trim()));

            // test provider
            Obs o3 = new Obs();
            o3.setConcept(conceptService.getConcept(healthProviderConcept));
            o3.setDateCreated(new Date());
            o3.setCreator(Context.getUserService().getUser(1));
            o3.setLocation(location);
            o3.setObsDatetime(date);
            o3.setPerson(this.patient);
            o3.setValueText(providerDetails.trim());

            // test provider id
            Obs o5 = new Obs();
            o5.setConcept(conceptService.getConcept(healthProviderIdentifierConcept));
            o5.setDateCreated(new Date());
            o5.setCreator(Context.getUserService().getUser(1));
            o5.setLocation(location);
            o5.setObsDatetime(date);
            o5.setPerson(this.patient);
            o5.setValueText(providerId.trim());

            // test facility
            Obs o4 = new Obs();
            o4.setConcept(conceptService.getConcept(healthFacilityNameConcept));
            o4.setDateCreated(new Date());
            o4.setCreator(Context.getUserService().getUser(1));
            o4.setLocation(location);
            o4.setObsDatetime(date);
            o4.setPerson(this.patient);
            o4.setValueText(facility.trim());


            enc.addObs(o);
            enc.addObs(o1);
            enc.addObs(o2);
            enc.addObs(o3);
            enc.addObs(o4);
            enc.addObs(o5);
            encounterService.saveEncounter(enc);

        }
        patientService.savePatient(patient);
    }

    private void saveImmunizationData() {
        //List<ImmunizationWrapper> immunizationData = processImmunizationData();
        //saveImmunizationData(immunizationData);
    }

    private void saveImmunizationData(List<ImmunizationWrapper> data) {

        EncounterType pSmartDataEncType = encounterService.getEncounterTypeByUuid(SmartCardMetadata._EncounterType.EXTERNAL_PSMART_DATA);
        Form pSmartImmunizationForm = Context.getFormService().getFormByUuid(SmartCardMetadata._Form.PSMART_IMMUNIZATION);
        //enc.addProvider(Context.getEncounterService().getEncounterRole(1), Context.getProviderService().getProvider(1));

        // organize data according to date
        Map<Date, List<ImmunizationWrapper>> organizedImmunizations = new HashMap<Date, List<ImmunizationWrapper>>();
        for (ImmunizationWrapper immunization : data) {
            Date vaccineDate = immunization.getVaccineDate();
            if (organizedImmunizations.containsKey(vaccineDate)) {
                organizedImmunizations.get(vaccineDate).add(immunization);
            } else {
                organizedImmunizations.put(vaccineDate, Arrays.asList(immunization));
            }
        }

        // loop through different dates
        for (Date immunizationDate : organizedImmunizations.keySet()) {

            List<ImmunizationWrapper> immunizationList = organizedImmunizations.get(immunizationDate);
            // build encounter
            Encounter enc = new Encounter();
            Location location = Context.getLocationService().getLocation(1);
            enc.setLocation(location);
            enc.setEncounterType(pSmartDataEncType);
            enc.setEncounterDatetime(immunizationDate);
            enc.setPatient(patient);
            enc.addProvider(Context.getEncounterService().getEncounterRole(1), Context.getProviderService().getProvider(1));
            enc.setForm(pSmartImmunizationForm);

            // build obs and add to encounter
            for (ImmunizationWrapper entry : immunizationList) {
                Set<Obs> obs = createImmunizationObs(entry);
                enc.setObs(obs);
            }
            encounterService.saveEncounter(enc);

        }
        patientService.savePatient(patient);

    }

    private Set<Obs> createImmunizationObs(ImmunizationWrapper entry) {

        Concept groupingConcept = conceptService.getConcept(1421);
        Concept vaccineConcept = conceptService.getConcept(984);
        Concept sequenceNumber = conceptService.getConcept(1418);
        Set<Obs> immunizationObs = new HashSet<Obs>();

        Obs obsGroup = new Obs();
        obsGroup.setConcept(groupingConcept);
        obsGroup.setObsDatetime(entry.getVaccineDate());
        obsGroup.setPerson(patient);

        Obs immunization = new Obs();
        immunization.setConcept(vaccineConcept);
        immunization.setValueCoded(entry.getVaccine());
        immunization.setObsDatetime(entry.getVaccineDate());
        immunization.setPerson(patient);
        immunization.setObsGroup(obsGroup);

        immunizationObs.addAll(Arrays.asList(obsGroup, immunization));

        if (entry.getSequenceNumber() != null) {
            Obs immunizationSequenceNumber = new Obs();
            immunizationSequenceNumber.setConcept(sequenceNumber);
            immunizationSequenceNumber.setValueNumeric(Double.valueOf(entry.getSequenceNumber()));
            immunizationSequenceNumber.setPerson(patient);
            immunizationSequenceNumber.setObsGroup(obsGroup);
            immunizationObs.add(immunizationSequenceNumber);
        }


        return immunizationObs;
    }


    private List<ImmunizationWrapper> processImmunizationData() {

        Concept BCG = conceptService.getConcept(886);
        Concept OPV = conceptService.getConcept(783);
        Concept IPV = conceptService.getConcept(1422);
        Concept DPT = conceptService.getConcept(781);
        Concept PCV = conceptService.getConcept(162342);
        Concept ROTA = conceptService.getConcept(83531);
        Concept MEASLESorRUBELLA = conceptService.getConcept(162586);
        Concept MEASLES = conceptService.getConcept(36);
        Concept YELLOW_FEVER = conceptService.getConcept(5864);

        List<ImmunizationWrapper> shrData = new ArrayList<ImmunizationWrapper>();
        for (int i = 0; i < SHRUtils.getSHR(this.incomingSHR).iMMUNIZATION.length; i++) {

            String name = SHRUtils.getSHR(this.incomingSHR).iMMUNIZATION[i].nAME;
            String dateAministered = SHRUtils.getSHR(this.incomingSHR).iMMUNIZATION[i].dATE_ADMINISTERED;
            Date date = null;
            try {
                date = new SimpleDateFormat("yyyyMMdd").parse(dateAministered);
            } catch (ParseException ex) {

                ex.printStackTrace();
            }
            ImmunizationWrapper entry = new ImmunizationWrapper();

            if (name.trim().equals("BCG")) {
                entry.setVaccine(BCG);
                entry.setSequenceNumber(null);
            } else if (name.trim().equals("OPV_AT_BIRTH")) {
                entry.setVaccine(OPV);
                entry.setSequenceNumber(0);
            } else if (name.trim().equals("OPV1")) {
                entry.setVaccine(OPV);
                entry.setSequenceNumber(1);
            } else if (name.trim().equals("OPV2")) {
                entry.setVaccine(OPV);
                entry.setSequenceNumber(2);
            } else if (name.trim().equals("OPV3")) {
                entry.setVaccine(OPV);
                entry.setSequenceNumber(3);
            } else if (name.trim().equals("PCV10-1")) {
                entry.setVaccine(PCV);
                entry.setSequenceNumber(1);
            } else if (name.trim().equals("PCV10-2")) {
                entry.setVaccine(PCV);
                entry.setSequenceNumber(2);
            } else if (name.trim().equals("PCV10-3")) {
                entry.setVaccine(PCV);
                entry.setSequenceNumber(3);
            } else if (name.trim().equals("ROTA1")) {
                entry.setVaccine(ROTA);
                entry.setSequenceNumber(1);
            } else if (name.trim().equals("ROTA2")) {
                entry.setVaccine(ROTA);
                entry.setSequenceNumber(2);
            } else if (name.trim().equals("MEASLES6")) {
                entry.setVaccine(MEASLES);
                entry.setSequenceNumber(1);
            } else if (name.trim().equals("MEASLES9")) {
                entry.setVaccine(MEASLESorRUBELLA);
                entry.setSequenceNumber(1);
            } else if (name.trim().equals("MEASLES18")) {
                entry.setVaccine(MEASLESorRUBELLA);
                entry.setSequenceNumber(2);
            }
            entry.setVaccineDate(date);
            shrData.add(entry);

        }
        return shrData;
    }

    /**
     * saves the first next of kin details. The system does not support multiple
     */
    private void saveNextOfKinDetails() {

        String NEXT_OF_KIN_ADDRESS = "7cf22bec-d90a-46ad-9f48-035952261294";
        String NEXT_OF_KIN_CONTACT = "342a1d39-c541-4b29-8818-930916f4c2dc";
        String NEXT_OF_KIN_NAME = "830bef6d-b01f-449d-9f8d-ac0fede8dbd3";
        String NEXT_OF_KIN_RELATIONSHIP = "d0aa9fd1-2ac5-45d8-9c5e-4317c622c8f5";
        Set<PersonAttribute> attributes = new TreeSet<PersonAttribute>();
        if (SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN != null && SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN.length > 0) {
            PersonAttributeType nextOfKinNameAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_NAME);
            PersonAttributeType nextOfKinAddressAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_ADDRESS);
            PersonAttributeType nextOfKinPhoneContactAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_CONTACT);
            PersonAttributeType nextOfKinRelationshipAttrType = personService.getPersonAttributeTypeByUuid(NEXT_OF_KIN_RELATIONSHIP);

            String nextOfKinName = SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].nOK_NAME.fIRST_NAME.concat(
                    SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].nOK_NAME.mIDDLE_NAME != "" ? SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].nOK_NAME.mIDDLE_NAME : ""
            ).concat(
                    SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].nOK_NAME.lAST_NAME != "" ? SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].nOK_NAME.lAST_NAME : ""
            );

            String nextOfKinAddress = SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].aDDRESS;
            String nextOfKinPhoneContact = SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].pHONE_NUMBER;
            String nextOfKinRelationship = SHRUtils.getSHR(this.incomingSHR).nEXT_OF_KIN[0].rELATIONSHIP;

            if (nextOfKinName != null) {
                PersonAttribute kinName = new PersonAttribute();
                kinName.setAttributeType(nextOfKinNameAttrType);
                kinName.setValue(nextOfKinName.trim());
                attributes.add(kinName);
            }

            if (nextOfKinAddress != null) {
                PersonAttribute kinAddress = new PersonAttribute();
                kinAddress.setAttributeType(nextOfKinAddressAttrType);
                kinAddress.setValue(nextOfKinAddress.trim());
                attributes.add(kinAddress);
            }

            if (nextOfKinPhoneContact != null) {
                PersonAttribute kinPhoneContact = new PersonAttribute();
                kinPhoneContact.setAttributeType(nextOfKinPhoneContactAttrType);
                kinPhoneContact.setValue(nextOfKinPhoneContact.trim());
                attributes.add(kinPhoneContact);
            }

            if (nextOfKinRelationship != null) {
                PersonAttribute kinRelationship = new PersonAttribute();
                kinRelationship.setAttributeType(nextOfKinRelationshipAttrType);
                kinRelationship.setValue(nextOfKinRelationship.trim());
                attributes.add(kinRelationship);
            }
            patient.setAttributes(attributes);
        }


    }

    private void saveMotherDetails() {

    }

    private void saveObsData() {

        String cIVIL_STATUS = SHRUtils.getSHR(this.incomingSHR).pATIENT_IDENTIFICATION.mARITAL_STATUS;
        if (cIVIL_STATUS != null) {

        }
    }

    /**
     * Can't save patients unless they have required OpenMRS IDs
     */
    private PatientIdentifier generateOpenMRSID() {
        PatientIdentifierType openmrsIDType = Context.getPatientService().getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76");
        String generated = Context.getService(IdentifierSourceService.class).generateIdentifier(openmrsIDType, "Registration");
        /*PatientIdentifier existingIdentifier = patient.getPatientIdentifier(openmrsIDType);
        if(existingIdentifier != null) {
            logger.info("Identifier exists");
            System.out.println("Identifier exists");
            return existingIdentifier;
        }*/
        PatientIdentifier identifier = new PatientIdentifier(generated, openmrsIDType, Utils.getDefaultLocation());
        logger.info("New identifier generated");
        System.out.println("New identifier generated");
        return identifier;
    }

    private List<SmartCardHivTest> getHivTests() {

        // test concepts
        Concept finalHivTestResultConcept = conceptService.getConcept(159427);
        Concept	testTypeConcept = conceptService.getConcept(162084);
        Concept testStrategyConcept = conceptService.getConcept(164956);
        Concept testFacilityCodeConcept = conceptService.getConcept(162724);




        Form HTS_INITIAL_FORM = Context.getFormService().getFormByUuid(HTS_INITIAL_TEST_FORM_UUID);
        Form HTS_CONFIRMATORY_FORM = Context.getFormService().getFormByUuid(HTS_CONFIRMATORY_TEST_FORM_UUID);

        EncounterType smartCardHTSEntry = Context.getEncounterService().getEncounterTypeByUuid(SmartCardMetadata._EncounterType.EXTERNAL_PSMART_DATA);
        Form SMART_CARD_HTS_FORM = Context.getFormService().getFormByUuid(SmartCardMetadata._Form.PSMART_HIV_TEST);


        List<Encounter> htsEncounters = Utils.getEncounters(patient, Arrays.asList(HTS_CONFIRMATORY_FORM, HTS_INITIAL_FORM));
        List<Encounter> processedIncomingTests = Utils.getEncounters(patient, Arrays.asList(SMART_CARD_HTS_FORM));

        List<SmartCardHivTest> testList = new ArrayList<SmartCardHivTest>();
        // loop through encounters and extract hiv test information
        for(Encounter encounter : htsEncounters) {
            List<Obs> obs = Utils.getEncounterObservationsForQuestions(patient, encounter, Arrays.asList(finalHivTestResultConcept, testTypeConcept, testStrategyConcept));
            testList.add(extractHivTestInformation(obs));
        }

        // append processed tests from card
        for(Encounter encounter : processedIncomingTests) {
            List<Obs> obs = Utils.getEncounterObservationsForQuestions(patient, encounter, Arrays.asList(finalHivTestResultConcept, testTypeConcept, testStrategyConcept, testFacilityCodeConcept));
            testList.add(extractHivTestInformation(obs));
        }

        return testList;
    }

    private SmartCardHivTest extractHivTestInformation (List<Obs> obsList) {

        Integer finalHivTestResultConcept = 159427;
        Integer	testTypeConcept = 162084;
        Integer testStrategyConcept = 164956;
        Integer testFacilityCodeConcept = 162724;

        Date testDate= obsList.get(0).getObsDatetime();
        User provider = obsList.get(0).getCreator();
        Concept testResult = null;
        String testType = null;
        String testFacility = null;
        Concept testStrategy = null;

        for(Obs obs:obsList) {

            if(obs.getEncounter().getForm().getUuid().equals(HTS_CONFIRMATORY_TEST_FORM_UUID)) {
                testType = "CONFIRMATORY";
            } else if(obs.getEncounter().getForm().getUuid().equals(HTS_INITIAL_TEST_FORM_UUID)) {
                testType = "INITIAL";
            }

            if (obs.getConcept().getConceptId().equals(testTypeConcept)) {
                testType = testTypeToStringConverter(obs.getValueCoded());
            }

            if (obs.getConcept().getConceptId().equals(finalHivTestResultConcept) ) {
                testResult = obs.getValueCoded();
            } /*else if (obs.getConcept().getConceptId().equals(testTypeConcept )) {
                testType = testTypeConverter(obs.getValueCoded());
            }*/ else if (obs.getConcept().getConceptId().equals(testStrategyConcept) ) {
                testStrategy = obs.getValueCoded();
            } else if(obs.getConcept().getConceptId().equals(testFacilityCodeConcept)) {
                testFacility = obs.getValueText();
                System.out.println("Facility code found here: =========== converted: " + testFacility + ", Original :" + obs.getValueText());
            }
        }
        return new SmartCardHivTest(testResult, testFacility, testStrategy, testDate, testType);

    }


}
