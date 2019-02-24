/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.kenyaemrpsmart.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.kenyaemrpsmart.PsmartStore;
import org.openmrs.module.kenyaemrpsmart.api.PsmartService;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.IncomingPatientSHR;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.MiddlewareRequest;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.OutgoingPatientSHR;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.PsmartAuthentication;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.mapper.SmartCardEligibleList;
import org.openmrs.module.kenyaemrpsmart.jsonvalidator.utils.SHRUtils;
import org.openmrs.module.kenyaemrpsmart.kenyaemrUtils.Utils;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;

/**
 * The main controller.
 */
@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/smartcard")
public class PSMARTRestController extends BaseRestController {

	protected final Log log = LogFactory.getLog(getClass());

	/**
	 * gets SHR based on patient/client internal ID
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/getshr")
	@ResponseBody
	public Object receiveSHR(HttpServletRequest request) {
		Integer patientID=null;
		String requestBody = null;
		MiddlewareRequest thisRequest = null;
		try {
			requestBody = SHRUtils.fetchRequestBody(request.getReader());//request.getParameter("encryptedSHR") != null? request.getParameter("encryptedSHR"): null;
		} catch (IOException e) {
			return new SimpleObject().add("ServerResponse", "Error extracting request body");
		}
		try {
			thisRequest = new ObjectMapper().readValue(requestBody, MiddlewareRequest.class);
		} catch (IOException e) {
			e.printStackTrace();
			return new SimpleObject().add("ServerResponse", "Error reading patient id: " + requestBody);
		}
		patientID=Integer.parseInt(thisRequest.getPatientID());
		if (patientID != 0) {
			OutgoingPatientSHR shr = new OutgoingPatientSHR(patientID);
			return shr.patientIdentification().toString();

		}
		return new SimpleObject().add("identification", "No patient id specified in the request: Got this: => " + request.getParameter("patientID"));
	}

	/**
	 * Processes SHR read from smart card
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/processshr")
	@ResponseBody
	public Object prepareSHR(HttpServletRequest request) {

		String encryptedSHR=null;
		try {
			encryptedSHR = SHRUtils.fetchRequestBody(request.getReader());//request.getParameter("encryptedSHR") != null? request.getParameter("encryptedSHR"): null;
		} catch (IOException e) {
			return new SimpleObject().add("ServerResponse", "Error extracting request body");
		}

		IncomingPatientSHR shr = new IncomingPatientSHR(encryptedSHR);
		return shr.processIncomingSHR();
	}

	/**
	 * Return list of patients/clients to be issued with smart cards
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/getsmartcardeligiblelist")
	@ResponseBody
	public Object prepareEligibleList(HttpServletRequest request) {
		SmartCardEligibleList list = new SmartCardEligibleList();
		return list.getEligibleList().toString();
	}

	/**
	 * Generates P-Smart SHR based on psmart card serial number
	 * @param request
	 * @param cardSerialNo
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/getshrusingcardserial/{cardSerialNo}")
	@ResponseBody
	public Object getShrUsingCardSerial(HttpServletRequest request, @PathVariable("cardSerialNo") String cardSerialNo) {
		if(cardSerialNo != null) {
			OutgoingPatientSHR shr = new OutgoingPatientSHR(cardSerialNo);
			return shr.patientIdentification().toString();
		}
		return null;
	}

	/**
	 * adds psmart card serial number as patient identifier
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/assigncardserialnumber")
	@ResponseBody
	public Object addSmartCardSerialToIdentifiers(HttpServletRequest request) {

		Integer patientID=null;
		String cardSerialNumber=null;
		String requestBody = null;
		MiddlewareRequest thisRequest = null;
		try {
			requestBody = SHRUtils.fetchRequestBody(request.getReader());//request.getParameter("encryptedSHR") != null? request.getParameter("encryptedSHR"): null;
		} catch (IOException e) {
			return new SimpleObject().add("ServerResponse", "Error extracting request body");
		}
		try {
			thisRequest = new ObjectMapper().readValue(requestBody, MiddlewareRequest.class);
		} catch (IOException e) {
			e.printStackTrace();
			return new SimpleObject().add("ServerResponse", "Error reading patient id: " + requestBody);
		}
		patientID=Integer.parseInt(thisRequest.getPatientID());
		cardSerialNumber = thisRequest.getCardSerialNumber();
		IncomingPatientSHR shr = new IncomingPatientSHR(patientID);
		return shr.assignCardSerialIdentifier(cardSerialNumber, null);
	}


	/**
	 * Handle authentication
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/authenticateuser")
	@ResponseBody
	public Object userAuthentication(HttpServletRequest request) {
		String requestBody = null;
		String userName=null;
		String pwd = null;
		MiddlewareRequest thisRequest = null;
		try {
			requestBody = SHRUtils.fetchRequestBody(request.getReader());//request.getParameter("encryptedSHR") != null? request.getParameter("encryptedSHR"): null;
		} catch (IOException e) {
			return new SimpleObject().add("ServerResponse", "Error extracting request body");
		}

		try {
			thisRequest = new ObjectMapper().readValue(requestBody, MiddlewareRequest.class);
		} catch (IOException e) {
			e.printStackTrace();
			return new SimpleObject().add("ServerResponse", "Error parsing request body: " + requestBody);
		}
		userName = thisRequest.getUserName();
		pwd = thisRequest.getPwd();

		return PsmartAuthentication.authenticateUser(userName.trim(), pwd.trim()).toString();
	}

	/**
	 * Saves addendumized SHR to psmart store
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/savetopsmart")
	@ResponseBody
	public Object saveToPsmart(HttpServletRequest request) {
		String requestBody = null;
		try {
			requestBody = SHRUtils.fetchRequestBody(request.getReader());
		} catch (IOException e) {
			return "Could not process request body";
		}

		if(requestBody != null) {
			try {
				PsmartStore entry = new PsmartStore();
				entry.setDateCreated(new Date());
				entry.setShr(requestBody);
				entry.setStatus("PENDING");
				entry.setStatusDate(new Timestamp(new Date().getTime()));
				entry.setDateCreated(new Timestamp(new Date().getTime()));

				OutgoingPatientSHR handler = new OutgoingPatientSHR();
				handler.saveRegistryEntry(entry);
				return "Card details successfully sent to EMR";
			} catch (Exception e) {
				return "There was an error writing card details in the EMR";
			}
		}

		return "There was a problem sending encrypted data to the EMR";
	}

	@RequestMapping(method = RequestMethod.POST, value = "/getidentifiers")
	@ResponseBody
	public Object getAllPatientIdentifiers(HttpServletRequest request) {

		Integer patientID=null;
		String requestBody = null;
		MiddlewareRequest thisRequest = null;
		try {
			requestBody = SHRUtils.fetchRequestBody(request.getReader());//request.getParameter("encryptedSHR") != null? request.getParameter("encryptedSHR"): null;
		} catch (IOException e) {
			return new SimpleObject().add("ServerResponse", "Error extracting request body");
		}
		try {
			thisRequest = new ObjectMapper().readValue(requestBody, MiddlewareRequest.class);
		} catch (IOException e) {
			e.printStackTrace();
			return new SimpleObject().add("ServerResponse", "Error reading patient id: " + requestBody);
		}
		patientID=Integer.parseInt(thisRequest.getPatientID());

		return Utils.getPatientIdentifiers(patientID).toString();
	}


	@RequestMapping(method = RequestMethod.POST, value = "/getopenmrsid")
	@ResponseBody
	public Object getOpenMRSIdentifiers(HttpServletRequest request) {

		Integer patientID=null;
		String requestBody = null;
		MiddlewareRequest thisRequest = null;
		try {
			requestBody = SHRUtils.fetchRequestBody(request.getReader());//request.getParameter("encryptedSHR") != null? request.getParameter("encryptedSHR"): null;
		} catch (IOException e) {
			return new SimpleObject().add("ServerResponse", "Error extracting request body");
		}
		try {
			thisRequest = new ObjectMapper().readValue(requestBody, MiddlewareRequest.class);
		} catch (IOException e) {
			e.printStackTrace();
			return new SimpleObject().add("ServerResponse", "Error reading patient id: " + requestBody);
		}
		patientID=Integer.parseInt(thisRequest.getPatientID());

		return Utils.getOpenMRSIdentifiers(patientID).toString();
	}

	/**
	 * Generates KenyaEMR related metrics
	 * @param request
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/getemrmetrics")
	@ResponseBody
	public Object getKenyaEMRDetails(HttpServletRequest request) {

		return "{\n" +
				"    \"EmrName\":\"KenyaEMR\",\n" +
				"    \"EmrVersion\":\"17.0.0\",\n" +
				"    \"LastLoginDate\":\"\",\n" +
				"    \"LastMoH731RunDate\":\"\"\n" +
				"}";
	}

	/**
	 * @see org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController#getNamespace()
	 */

	@Override
	public String getNamespace() {
		return "v1/kenyaemrpsmart";
	}

}
