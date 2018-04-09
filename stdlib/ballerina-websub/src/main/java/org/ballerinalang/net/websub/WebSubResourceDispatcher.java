/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.websub;

import org.ballerinalang.connector.api.BLangConnectorSPIUtil;
import org.ballerinalang.connector.api.BallerinaConnectorException;
import org.ballerinalang.mime.util.MimeUtil;
import org.ballerinalang.model.values.BJSON;
import org.ballerinalang.model.values.BMap;
import org.ballerinalang.model.values.BString;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.net.http.HttpResource;
import org.ballerinalang.net.http.HttpService;
import org.ballerinalang.net.http.HttpUtil;
import org.ballerinalang.net.http.caching.RequestCacheControlStruct;
import org.ballerinalang.util.codegen.ProgramFile;
import org.ballerinalang.util.exceptions.BallerinaException;
import org.wso2.transport.http.netty.common.Constants;
import org.wso2.transport.http.netty.contract.ServerConnectorException;
import org.wso2.transport.http.netty.message.HTTPCarbonMessage;

/**
 * Resource dispatcher specific for WebSub subscriber services.
 *
 * @since 0.965.0
 */
class WebSubResourceDispatcher {

    static HttpResource findResource(HttpService service, HTTPCarbonMessage inboundRequest,
                                     WebSubServicesRegistry servicesRegistry)
            throws BallerinaConnectorException, ServerConnectorException {

        String method = (String) inboundRequest.getProperty(HttpConstants.HTTP_METHOD);
        HttpResource httpResource = null;
        String resourceName;

        if (WebSubSubscriberConstants.TOPIC_ID_HEADER.equals(servicesRegistry.getTopicIdentifier())) {
            String topic = inboundRequest.getHeader(servicesRegistry.getTopicHeader());
            resourceName = retrieveResourceName(method, topic, servicesRegistry.getTopicResourceMap());
        } else if (servicesRegistry.getTopicIdentifier() != null && HttpConstants.HTTP_METHOD_POST.equals(method)) {
            if (inboundRequest.getProperty(Constants.HTTP_RESOURCE) == null) {
                inboundRequest.setProperty(Constants.HTTP_RESOURCE,
                                           WebSubSubscriberConstants.DEFERRED_FOR_PAYLOAD_BASED_DISPATCHING);
                return null;
            }
            ProgramFile programFile = service.getBalService().getServiceInfo().getPackageInfo().getProgramFile();
            String payloadValue = retrievePayloadValueForKey(programFile, inboundRequest,
                                                             servicesRegistry.getTopicPayloadKey());
            if (servicesRegistry.getTopicIdentifier().equals(WebSubSubscriberConstants.TOPIC_ID_PAYLOAD_KEY)) {
                resourceName = retrieveResourceName(method, payloadValue, servicesRegistry.getTopicResourceMap());
            } else {
                String topic = inboundRequest.getHeader(servicesRegistry.getTopicHeader()) + "::" + payloadValue;
                resourceName = retrieveResourceName(method, topic, servicesRegistry.getTopicResourceMap());
            }
        } else {
            resourceName = retrieveResourceName(method);
        }

        for (HttpResource resource : service.getResources()) {
            if (resource.getName().equals(resourceName)) {
                httpResource = resource;
                break;
            }
        }

        if (httpResource == null) {
            if (WebSubSubscriberConstants.RESOURCE_NAME_ON_INTENT_VERIFICATION.equals(resourceName)) {
                //if the request is a GET request indicating an intent verification request, and the user has not
                //specified an onIntentVerification resource, assume auto intent verification and respond
                String annotatedTopic = (service.getBalService())
                        .getAnnotationList(WebSubSubscriberConstants.WEBSUB_PACKAGE_PATH,
                                           WebSubSubscriberConstants.ANN_NAME_WEBSUB_SUBSCRIBER_SERVICE_CONFIG)
                        .get(0).getValue().getStringField(WebSubSubscriberConstants.ANN_WEBSUB_ATTR_TOPIC);
                if (annotatedTopic.isEmpty() && service instanceof WebSubHttpService) {
                    annotatedTopic = ((WebSubHttpService) service).getTopic();
                }
                inboundRequest.setProperty(WebSubSubscriberConstants.ANNOTATED_TOPIC, annotatedTopic);
                inboundRequest.setProperty(Constants.HTTP_RESOURCE, WebSubSubscriberConstants.ANNOTATED_TOPIC);
            } else {
                inboundRequest.setProperty(HttpConstants.HTTP_STATUS_CODE, 404);
                throw new BallerinaConnectorException("no matching WebSub Subscriber service  resource " + resourceName
                                                              + " found for method : " + method);
            }
        }
        return httpResource;
    }

    private static String retrieveResourceName(String method) {
        switch (method) {
            case HttpConstants.HTTP_METHOD_POST:
                return WebSubSubscriberConstants.RESOURCE_NAME_ON_NOTIFICATION;
            case HttpConstants.HTTP_METHOD_GET:
                return WebSubSubscriberConstants.RESOURCE_NAME_ON_INTENT_VERIFICATION;
            default:
                throw new BallerinaConnectorException("method not allowed for WebSub Subscriber Services : " + method);
        }
    }

    private static String retrievePayloadValueForKey(ProgramFile programFile, HTTPCarbonMessage httpCarbonMessage,
                                               String payloadKey) {
        BValue httpRequest = getHttpRequest(programFile, httpCarbonMessage);
        BStruct entityStruct = MimeUtil.extractEntity((BStruct) httpRequest);
        if (entityStruct != null) {
            if (entityStruct.getNativeData(
                    org.ballerinalang.mime.util.Constants.MESSAGE_DATA_SOURCE) instanceof BJSON) {
                BJSON jsonBody = (BJSON)
                        (entityStruct.getNativeData(org.ballerinalang.mime.util.Constants.MESSAGE_DATA_SOURCE));
                return jsonBody.value().get(payloadKey).stringValue();
            } else {
                throw new BallerinaException("Non-JSON payload received for payload key based dispatching");
            }
        } else {
            throw new BallerinaException("Error retrieving payload for payload key based dispatching");
        }
    }

    private static String retrieveResourceName(String method, String topic, BMap<String, BString> topicResourceMap) {
        switch (method) {
            case HttpConstants.HTTP_METHOD_POST:
                if (topicResourceMap.get(topic) != null) {
                    return topicResourceMap.get(topic).stringValue();
                } else {
                    throw new BallerinaConnectorException("resource not specified for topic : " + topic);
                }
            case HttpConstants.HTTP_METHOD_GET:
                //TODO handle GET without verify intent
                return WebSubSubscriberConstants.RESOURCE_NAME_ON_INTENT_VERIFICATION;
            default:
                throw new BallerinaConnectorException("method not allowed for WebSub Subscriber Services : " + method);
        }
    }


    private static BStruct getHttpRequest(ProgramFile programFile, HTTPCarbonMessage httpCarbonMessage) {
        BStruct httpRequest = createBStruct(programFile, HttpConstants.PROTOCOL_PACKAGE_HTTP, HttpConstants.REQUEST);
        BStruct inRequestEntity = createBStruct(programFile,
                                                org.ballerinalang.mime.util.Constants.PROTOCOL_PACKAGE_MIME,
                                                org.ballerinalang.mime.util.Constants.ENTITY);
        BStruct mediaType = createBStruct(programFile,
                                          org.ballerinalang.mime.util.Constants.PROTOCOL_PACKAGE_MIME,
                                          org.ballerinalang.mime.util.Constants.MEDIA_TYPE);
        BStruct cacheControlStruct = createBStruct(programFile,
                                                   HttpConstants.PROTOCOL_PACKAGE_HTTP,
                                                   HttpConstants.REQUEST_CACHE_CONTROL);
        RequestCacheControlStruct requestCacheControl = new RequestCacheControlStruct(cacheControlStruct);
        HttpUtil.populateInboundRequest(httpRequest, inRequestEntity, mediaType, httpCarbonMessage,
                                        requestCacheControl);
        return httpRequest;
    }

    private static BStruct createBStruct(ProgramFile programFile, String packagePath, String structName) {
        return BLangConnectorSPIUtil.createBStruct(programFile, packagePath, structName);
    }

}
