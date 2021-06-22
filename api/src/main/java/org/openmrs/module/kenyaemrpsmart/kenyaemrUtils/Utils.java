package org.openmrs.module.kenyaemrpsmart.kenyaemrUtils;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.SmartCardHivTest;
import org.openmrs.module.kenyaemrpsmart.metadata.SmartCardMetadata;
import org.openmrs.util.PrivilegeConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static List<Obs> getNLastObs(Concept concept, Patient patient, Integer nLast) throws Exception {
        List<Obs> obs = Context.getObsService().getObservations(
                Arrays.asList(Context.getPersonService().getPerson(patient.getPersonId())),
                null,
                Arrays.asList(concept),
                null,
                null,
                null,
                null,
                nLast,
                null,
                null,
                null,
                false);
        return obs;
    }

    public static Obs getLatestObs(Patient patient, String conceptIdentifier) {
        Concept concept = Context.getConceptService().getConceptByUuid(conceptIdentifier);
        List<Obs> obs = Context.getObsService().getObservationsByPersonAndConcept(patient, concept);
        if (obs.size() > 0) {
            // these are in reverse chronological order
            return obs.get(0);
        }
        return null;
    }

    /**
     * Finds the last encounter during the program enrollment with the given encounter type
     *
     * @param type the encounter type
     *
     * @return the encounter
     */
    public static Encounter lastEncounter(Patient patient, EncounterType type) {
        List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, null, null, null, null, Collections.singleton(type), null, null, null, false);
        return encounters.size() > 0 ? encounters.get(encounters.size() - 1) : null;
    }

    /**
     * getEncounters(Patient who, Location loc, Date fromDate, Date toDate,
     Collection<Form> enteredViaForms, Collection<EncounterType> encounterTypes, Collection<Provider> providers,
     Collection<VisitType> visitTypes, Collection<Visit> visits, boolean includeVoided);
     * @return
     */


    public static List<Encounter> getEncounters (Patient patient, List<Form> forms) {

        return Context.getEncounterService().getEncounters(patient, null, null, null, forms, null, null, null, null, false);

    }

    public static List<Obs> getEncounterObservationsForQuestions(Person patient, Encounter encounter, List<Concept> questions) {
        /**
         * getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
         List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
         Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs)
         */
        return Context.getObsService().getObservations(Arrays.asList(patient), Arrays.asList(encounter), questions, null, null, null, null, null, null, null, null, false);
    }

    public static Location getDefaultLocation() {
        try {
            Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            String GP_DEFAULT_LOCATION = "kenyaemr.defaultLocation";
            GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(GP_DEFAULT_LOCATION);
            return gp != null ? ((Location) gp.getValue()) : null;
        }
        finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }

    }

    public static String getDefaultLocationMflCode(Location location) {
        String MASTER_FACILITY_CODE = "8a845a89-6aa5-4111-81d3-0af31c45c002";

        if(location == null) {
            location = getDefaultLocation();
        }
        try {
            Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            for (LocationAttribute attr : location.getAttributes()) {
                if (attr.getAttributeType().getUuid().equals(MASTER_FACILITY_CODE) && !attr.isVoided()) {
                    return attr.getValueReference();
                }
            }
        } finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
        return null;
    }


    public static Location getLocationFromMFLCode(String mflCode) {

        String MASTER_FACILITY_CODE = "8a845a89-6aa5-4111-81d3-0af31c45c002";

        try {
            Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            LocationAttributeType facilityMflCode = Context.getLocationService().getLocationAttributeTypeByUuid(MASTER_FACILITY_CODE);
            Map<LocationAttributeType, Object> mflCodeMap = new HashMap<LocationAttributeType, Object>();
            mflCodeMap.put(facilityMflCode, mflCode);

            List<Location> locationForMfl = Context.getLocationService().getLocations(null, null, mflCodeMap, false, null,null);

            return locationForMfl.size() > 0 ? locationForMfl.get(0) : getDefaultLocation();
        }
        finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }
    }

    public static ArrayNode getPatientIdentifiers(Integer patientID) {
        String HEI_UNIQUE_NUMBER = "0691f522-dd67-4eeb-92c8-af5083baf338";
        String NATIONAL_ID = "49af6cdc-7968-4abb-bf46-de10d7f4859f";
        String UNIQUE_PATIENT_NUMBER = "05ee9cf4-7242-4a17-b4d4-00f707265c8a";
        PatientService patientService = Context.getPatientService();
        PatientIdentifierType HEI_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(HEI_UNIQUE_NUMBER);
        PatientIdentifierType CCC_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(UNIQUE_PATIENT_NUMBER);
        PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(NATIONAL_ID);
        PatientIdentifierType SMART_CARD_SERIAL_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.SMART_CARD_SERIAL_NUMBER);
        PatientIdentifierType HTS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.HTS_NUMBER);
        PatientIdentifierType GODS_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SmartCardMetadata._PatientIdentifierType.GODS_NUMBER);
        PatientIdentifierType openmrsIDType = Context.getPatientService().getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76");


        List<PatientIdentifierType> allIdTypes = Arrays.asList(
                HEI_NUMBER_TYPE, CCC_NUMBER_TYPE, NATIONAL_ID_TYPE, SMART_CARD_SERIAL_NUMBER_TYPE, HTS_NUMBER_TYPE, GODS_NUMBER_TYPE, openmrsIDType
        );

        Patient patient = Context.getPatientService().getPatient(patientID);
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ArrayNode idsNode = factory.arrayNode();
        if (patient != null) {

/*(String identifier,
	        List<PatientIdentifierType> patientIdentifierTypes, List<Location> locations, List<Patient> patients,
	        Boolean isPreferred)*/
            List<PatientIdentifier> identifierList = Context.getPatientService().getPatientIdentifiers(
                    null,
                    allIdTypes,
                    null,
                    Arrays.asList(patient),
                    null
            );

            for (PatientIdentifier identifier : identifierList) {
                ObjectNode identifierNode = factory.objectNode();
                identifierNode.put("Type", identifier.getIdentifierType().getName());
                identifierNode.put("Value", identifier.getIdentifier());
                identifierNode.put("Preferred", identifier.isPreferred());
                idsNode.add(identifierNode);
            }
        }

        return idsNode;
    }

    public static ArrayNode getOpenMRSIdentifiers(Integer patientID) {

        PatientService patientService = Context.getPatientService();
        PatientIdentifierType openmrsIDType = patientService.getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76");


        List<PatientIdentifierType> allIdTypes = Arrays.asList(
                openmrsIDType
        );

        Patient patient = Context.getPatientService().getPatient(patientID);
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ArrayNode idsNode = factory.arrayNode();
        if (patient != null) {

/*(String identifier,
	        List<PatientIdentifierType> patientIdentifierTypes, List<Location> locations, List<Patient> patients,
	        Boolean isPreferred)*/
            List<PatientIdentifier> identifierList = Context.getPatientService().getPatientIdentifiers(
                    null,
                    allIdTypes,
                    null,
                    Arrays.asList(patient),
                    null
            );

            for (PatientIdentifier identifier : identifierList) {
                ObjectNode identifierNode = factory.objectNode();
                identifierNode.put("Type", identifier.getIdentifierType().getName());
                identifierNode.put("Value", identifier.getIdentifier());
                identifierNode.put("Preferred", identifier.isPreferred());
                idsNode.add(identifierNode);
            }
        }

        return idsNode;
    }

    public static List<PatientIdentifier> getOpenMRSIdentifiers(Patient patient) {

        PatientService patientService = Context.getPatientService();
        PatientIdentifierType openmrsIDType = patientService.getPatientIdentifierTypeByUuid("dfacd928-0370-4315-99d7-6ec1c9f7ae76");


        List<PatientIdentifierType> allIdTypes = Arrays.asList(
                openmrsIDType
        );

        List<PatientIdentifier> identifiers = new ArrayList<PatientIdentifier>();
        if (patient != null) {

            identifiers = Context.getPatientService().getPatientIdentifiers(
                    null,
                    allIdTypes,
                    null,
                    Arrays.asList(patient),
                    null
            );


        }

        return identifiers;
    }


}
