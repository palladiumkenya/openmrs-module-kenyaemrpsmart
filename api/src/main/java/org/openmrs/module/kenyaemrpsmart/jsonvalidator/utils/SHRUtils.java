/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openmrs.module.kenyaemrpsmart.jsonvalidator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemr.HivConstants;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.kenyaemr.util.EmrUtils;
import org.openmrs.module.kenyaemr.util.HtsConstants;
import org.openmrs.module.kenyaemr.util.ServerInformation;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.SHR;
import org.openmrs.module.reporting.report.ReportRequest;
import org.openmrs.module.reporting.report.definition.ReportDefinition;
import org.openmrs.module.reporting.report.service.ReportService;
import org.openmrs.util.OpenmrsUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author tedb19
 */
public class SHRUtils {


    public static SHR getSHR(String SHRStr) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SHR shr = null;
        try {
            shr = mapper.readValue(SHRStr, new TypeReference<SHR>() {
            });
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
        return shr;
    }

    public static String getJSON(SHR shr) {
        ObjectMapper mapper = new ObjectMapper();
        String JSONStr = "";
        try {
            JSONStr = mapper.writeValueAsString(shr);
        } catch (JsonProcessingException ex) {
            System.out.println(ex.getMessage());
        }
        return JSONStr;
    }

    public static String getHivTestSampleData () {
        return "'HIV_TEST':[{'DATE':'20180101','RESULT':'POSITIVE/NEGATIVE/INCONCLUSIVE','TYPE':" +
                "'SCREENING/CONFIRMATORY','FACILITY':'10829','STRATEGY':'HP/NP/VI/VS/HB/MO/O'," +
                "'PROVIDER_DETAILS':{'NAME':'AFYA JIJINI'}}]";
    }

    public static String getPatientDemographicsSampleData () {
        return  "{\n" +
                "\t\"DEMOGRAPHICS\": {\n" +
                "\t\t\"FIRST_NAME\": \"JOHN\",\n" +
                "\t\t\"MIDDLE_NAME\": \"PARKER\",\n" +
                "\t\t\"LAST_NAME\": \"DBOO\",\n" +
                "\t\t\"DATE_OF_BIRTH\": \"20121010\",\n" +
                "\t\t\"SEX\": \"F\",\n" +
                "\t\t\"PHONE_NUMBER\": \"254720278654\",\n" +
                "\t\t\"MARITAL_STATUS\": \"SINGLE\"\n" +
                "\t}\n" +
                "}";


    }

    public static String getPatientIdentifiersSampleData () {
        return "{\"IDENTIFIERS\":[{\"ID\":\"12345678-ADFGHJY-0987654-NHYI890\",\"TYPE\":\"CARD\",\"FACILITY\":\"40829\"},{\"ID\":\"37645678\",\"TYPE\":\"HEI\",\"FACILITY\":\"10829\"},{\"ID\":\"12345678\",\"TYPE\":\"CCC\",\"FACILITY\":\"10829\"},{\"ID\":\"001\",\"TYPE\":\"HTS\",\"FACILITY\":\"10829\"}]}";
    }

    public static String getPatientAddressSampleData () {
        return "'ADDRESS': {\n" +
                "\t'VILLAGE': 'KWAKIMANI',\n" +
                "        'WARD': 'KIMANINI',\n" +
                "        'SUB_COUNTY': 'KIAMBU EAST',\n" +
                "        'COUNTY': 'KIAMBU',\n" +
                "        'NEAREST_LANDMARK': 'KIAMBU EAST'\n" +
                "}";
    }

    public static String getCardDetails () {
        return "'CARD_DETAILS': {\n" +
                "    'STATUS': 'ACTIVE/INACTIVE',\n" +
                "    'REASON': 'LOST/DEATH/DAMAGED',\n" +
                "    'LAST_UPDATED': '20180101',\n" +
                "    'LAST_UPDATED_FACILITY': '10829'\n" +
                "  }";
    }
   /*public static boolean isValidJSON(String SHRStr) {
        try {
            JsonFactory factory = new JsonFactory();
            JsonParser parser = factory.createParser(SHRStr);

            
            while (!parser.isClosed()) {
                JsonToken jsonToken = parser.nextToken();

                System.out.println("jsonToken = " + jsonToken);
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
        }
    }*/

    public static String fetchRequestBody(BufferedReader reader) {
        String requestBodyJsonStr = "";
        try {

            BufferedReader br = new BufferedReader(reader);
            String output = "";
            while ((output = reader.readLine()) != null) {
                requestBodyJsonStr += output;
            }


        } catch (IOException e) {

            System.out.println("IOException: " + e.getMessage());

        }
        return requestBodyJsonStr;
    }

    public static String getKenyaemrVersion() {
        Map<String, Object> kenyaemrInfo = ServerInformation.getKenyaemrInformation();

        String moduleVersion = (String) kenyaemrInfo.get("version");

        return moduleVersion;
    }

    public static String getLastLogin() {
        SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MMM-dd");


        Encounter lastFollowupEnc = EmrUtils.lastEncounter(null, Context.getEncounterService().getEncounterTypeByUuid(HivMetadata._EncounterType.HIV_CONSULTATION));
        if (lastFollowupEnc != null) {
             return DATE_FORMAT.format(lastFollowupEnc.getDateCreated());
        }
        return null;

    }

    public static String getDateofLastMOH731() {
        String moh731ReportDefUuid = "a66bf454-2a11-4e51-b28d-3d7ece76aa13";
        SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MMM-dd");
        List<ReportRequest> reports = fetchRequests(moh731ReportDefUuid, true, Context.getService(ReportService.class));
        if (reports != null) {
            return DATE_FORMAT.format(reports.get(0).getEvaluateStartDatetime());
        }

        return null;
    }

    public static List<ReportRequest> fetchRequests(String reportUuid, boolean finishedOnly, ReportService reportService) {
        ReportDefinition definition = null;

        // Hack to avoid loading (and thus de-serialising) the entire report
        if (StringUtils.isNotEmpty(reportUuid)) {
            definition = new ReportDefinition();
            definition.setUuid(reportUuid);
        }

        List<ReportRequest> requests = (finishedOnly)
                ? reportService.getReportRequests(definition, null, null, ReportRequest.Status.COMPLETED, ReportRequest.Status.FAILED)
                : reportService.getReportRequests(definition, null, null);

        // Sort by requested date desc (more sane than the default sorting)
        Collections.sort(requests, new Comparator<ReportRequest>() {
            @Override
            public int compare(ReportRequest request1, ReportRequest request2) {
                return OpenmrsUtil.compareWithNullAsEarliest(request2.getRequestDate(), request1.getRequestDate());
            }
        });

        return requests;
    }
}
