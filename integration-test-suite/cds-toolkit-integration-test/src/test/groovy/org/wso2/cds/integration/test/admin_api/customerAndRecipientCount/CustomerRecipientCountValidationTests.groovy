/**
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.cds.integration.test.admin_api.customerAndRecipientCount

import com.nimbusds.oauth2.sdk.AccessTokenResponse
import org.wso2.cds.test.framework.AUTest
import org.wso2.cds.test.framework.constant.AUAccountProfile
import org.wso2.cds.test.framework.constant.AUConstants
import org.wso2.cds.test.framework.request_builder.AURegistrationRequestBuilder
import org.wso2.cds.test.framework.request_builder.AURequestBuilder
import org.wso2.cds.test.framework.utility.AUTestUtil
import io.restassured.response.Response
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

/**
 * Customer Count and Recipient Count Validation Tests.
 */
class CustomerRecipientCountValidationTests extends AUTest {

    private AccessTokenResponse accessTokenResponse
    private String cdrArrangementId = ""
    private String clientId, accessToken, requestUri
    File xmlFile = new File(System.getProperty("user.dir").toString().concat("/../../resources/test-config.xml"))

    @BeforeClass
    void "Initial Metrics Request"() {

        //Send Initial Metrics Request
        def metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)

        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)
        Assert.assertEquals(metricsResponse.getHeader(AUConstants.X_V_HEADER).toInteger(), AUConstants.X_V_HEADER_METRICS)

        //Assign Metrics to Variables
        getInitialMetricsResponse(metricsResponse)

        auConfiguration.setTppNumber(1)
        jtiVal = String.valueOf(System.currentTimeMillis())
        AURegistrationRequestBuilder registrationRequestBuilder = new AURegistrationRequestBuilder()

        //Create New Application
        if(auConfiguration.getAppInfoClientID().equalsIgnoreCase("") ||
                auConfiguration.getAppInfoClientID().equalsIgnoreCase("AppConfig2.Application.ClientID")) {

            Response registrationResponse = AURegistrationRequestBuilder
                    .buildRegistrationRequest(registrationRequestBuilder.getAURegularClaims())
                    .post(AUConstants.DCR_REGISTRATION_ENDPOINT)

            clientId = AUTestUtil.parseResponseBody(registrationResponse, AUConstants.CLIENT_ID)

            Assert.assertEquals(registrationResponse.statusCode(), AUConstants.CREATED)
            clientId = AUTestUtil.parseResponseBody(registrationResponse, "client_id")
            AUTestUtil.writeXMLContent(xmlFile.toString(), "Application", "ClientID", clientId, 1)
        }
    }

    @Test
    void "Verify the count 0 if there are no active authorisations"() {

        def metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)
        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)

        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_CUSTOMER_COUNT),
                "${customerCount}", "$AUConstants.DATA_CUSTOMER_COUNT count mismatch")
        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_RECIPIENT_COUNT),
                "${recipientCount}", "$AUConstants.DATA_RECIPIENT_COUNT count mismatch")
    }

    @Test
    void "Verify the count equals to the PSU count with active authorisations"() {

        auConfiguration.setPsuNumber(1)

        def response = auAuthorisationBuilder.doPushAuthorisationRequest(scopes,
                AUConstants.DEFAULT_SHARING_DURATION, true, cdrArrangementId)
        requestUri = AUTestUtil.parseResponseBody(response, AUConstants.REQUEST_URI)
        Assert.assertEquals(response.statusCode(), AUConstants.STATUS_CODE_201)

        doConsentAuthorisationViaRequestUri(scopes, requestUri.toURI(), auConfiguration.getAppInfoClientID(),
                AUAccountProfile.INDIVIDUAL)
        Assert.assertNotNull(authorisationCode)

        //Get User Access Token
        accessTokenResponse = AURequestBuilder.getUserToken(authorisationCode, auAuthorisationBuilder.getCodeVerifier(),
                auConfiguration.getAppInfoClientID())
        accessToken = accessTokenResponse.getTokens().accessToken
        cdrArrangementId = accessTokenResponse.getCustomParameters().get("cdr_arrangement_id")

        def metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)
        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)

        customerCount = customerCount + 1
        recipientCount = recipientCount + 1

        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_CUSTOMER_COUNT),
                "${customerCount}", "$AUConstants.DATA_CUSTOMER_COUNT count mismatch")
        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_RECIPIENT_COUNT),
                "${recipientCount}", "$AUConstants.DATA_RECIPIENT_COUNT count mismatch")
    }

    @Test (dependsOnMethods = "Verify the count equals to the PSU count with active authorisations")
    void "Verify the count after revoking consent"() {

        //revoke sharing arrangement
        Response response = doRevokeCdrArrangement(auConfiguration.getAppInfoClientID(), cdrArrangementId)
        Assert.assertEquals(response.statusCode(), AUConstants.STATUS_CODE_204)

        def metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)
        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)

        customerCount = customerCount - 1
        recipientCount = recipientCount - 1

        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_CUSTOMER_COUNT),
                "${customerCount}", "$AUConstants.DATA_CUSTOMER_COUNT count mismatch")
        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_RECIPIENT_COUNT),
                "${recipientCount}", "$AUConstants.DATA_RECIPIENT_COUNT count mismatch")
    }

    @Test
    void "Verify the count unchanged when there is at least one active authorisation exist"() {

        //Create Consent
        auConfiguration.setPsuNumber(1)
        auConfiguration.setTppNumber(1)

        def response = auAuthorisationBuilder.doPushAuthorisationRequest(scopes,
                AUConstants.DEFAULT_SHARING_DURATION, true, cdrArrangementId)
        requestUri = AUTestUtil.parseResponseBody(response, AUConstants.REQUEST_URI)
        Assert.assertEquals(response.statusCode(), AUConstants.STATUS_CODE_201)

        doConsentAuthorisationViaRequestUri(scopes, requestUri.toURI(), auConfiguration.getAppInfoClientID(),
                AUAccountProfile.INDIVIDUAL)
        Assert.assertNotNull(authorisationCode)

        //Send Metrics Request and Verify count
        def metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)
        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)

        customerCount = customerCount + 1
        recipientCount = recipientCount + 1

        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_CUSTOMER_COUNT),
                "${customerCount}", "$AUConstants.DATA_CUSTOMER_COUNT count mismatch")
        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_RECIPIENT_COUNT),
                "${recipientCount}", "$AUConstants.DATA_RECIPIENT_COUNT count mismatch")

        //Same TPP and PSU Create another consent
        response = auAuthorisationBuilder.doPushAuthorisationRequest(scopes, AUConstants.DEFAULT_SHARING_DURATION,
                true, cdrArrangementId)
        requestUri = AUTestUtil.parseResponseBody(response, AUConstants.REQUEST_URI)
        Assert.assertEquals(response.statusCode(), AUConstants.STATUS_CODE_201)
        doConsentAuthorisationViaRequestUri(scopes, requestUri.toURI(), auConfiguration.getAppInfoClientID(),
                AUAccountProfile.INDIVIDUAL)
        Assert.assertNotNull(authorisationCode)

        //Send Metrics Request and Verify count
        metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)
        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)

        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_CUSTOMER_COUNT),
                "${customerCount}", "$AUConstants.DATA_CUSTOMER_COUNT count mismatch")
        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_RECIPIENT_COUNT),
                "${recipientCount}", "$AUConstants.DATA_RECIPIENT_COUNT count mismatch")
    }

    @Test (dependsOnMethods = "Verify the count unchanged when there is at least one active authorisation exist")
    void "Verify the count after deleting App via DCR API"() {

        //Get Application Access Token
        auConfiguration.setTppNumber(1)

        accessToken = getApplicationAccessToken(auConfiguration.getAppInfoClientID())
        Assert.assertNotNull(accessToken)

        //Delete DCR Request
        def registrationResponse = AURegistrationRequestBuilder.buildBasicRequest(accessToken)
                .when()
                .delete(AUConstants.DCR_REGISTRATION_ENDPOINT + clientId)

        Assert.assertEquals(registrationResponse.statusCode(), AUConstants.STATUS_CODE_204)

        //Send Metrics Request and Verify count
        def metricsResponse = getMetrics(AUConstants.PERIOD_CURRENT)
        Assert.assertEquals(metricsResponse.statusCode(), AUConstants.STATUS_CODE_200)

        customerCount = customerCount - 1
        recipientCount = recipientCount - 1

        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_CUSTOMER_COUNT),
                "${customerCount}", "$AUConstants.DATA_CUSTOMER_COUNT count mismatch")
        Assert.assertEquals(AUTestUtil.parseResponseBody(metricsResponse, AUConstants.DATA_RECIPIENT_COUNT),
                "${recipientCount}", "$AUConstants.DATA_RECIPIENT_COUNT count mismatch")
    }
}
