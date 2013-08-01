/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portlet.proxy.mvc.portlet.gateway;

import org.apache.commons.lang.StringUtils;
import org.jasig.portlet.proxy.mvc.IViewSelector;
import org.jasig.portlet.proxy.service.IFormField;
import org.jasig.portlet.proxy.service.web.IAuthenticationFormModifier;
import org.jasig.portlet.proxy.service.web.HttpContentRequestImpl;
import org.jasig.portlet.proxy.service.web.interceptor.IPreInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.portlet.ModelAndView;
import org.springframework.web.portlet.bind.annotation.RenderMapping;
import org.springframework.web.portlet.bind.annotation.ResourceMapping;

import javax.portlet.RenderRequest;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("VIEW")
public class GatewayPortletController {
    private static final String HTTPS = "HTTPS";

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
    
	@Autowired(required=false)
	private String viewName = "gateway";
	
	@Autowired(required=false)
	private String mobileViewName = "mobileGateway";
	
	@Autowired(required=true)
	private ApplicationContext applicationContext;
	
	@Autowired(required=true)
	private IViewSelector viewSelector;

	@RenderMapping
	public ModelAndView getView(RenderRequest request){
		final ModelAndView mv = new ModelAndView();
		final List<GatewayEntry> entries =  (List<GatewayEntry>) applicationContext.getBean("gatewayEntries", List.class);
		final Map<String, Boolean> validations = new HashMap<String, Boolean>();
        for (GatewayEntry entry : entries) {
	        for (Map.Entry<HttpContentRequestImpl, List<String>> requestEntry : entry.getContentRequests().entrySet()){
	
	            // run each content request through any configured preinterceptors to validate each entry
	            final HttpContentRequestImpl contentRequest = requestEntry.getKey();
	            for (String interceptorKey : requestEntry.getValue()) {
	                final IPreInterceptor interceptor = applicationContext.getBean(interceptorKey, IPreInterceptor.class);
	                boolean isValid = interceptor.validate(contentRequest, request);
	                validations.put(entry.getName(), isValid);
	            }
	        }
        }
		
		
		mv.addObject("entries", entries);
		mv.addObject("validations", validations);

        String openInNewPage = request.getPreferences().getValue("openInNewPage", "true");
        mv.addObject("openInNewPage", openInNewPage);
		
		final String view = viewSelector.isMobile(request) ? mobileViewName : viewName;
		mv.setView(view);
		return mv;
	}

	@ResourceMapping()
	public ModelAndView showTarget(ResourceRequest portletRequest, ResourceResponse portletResponse, @RequestParam("index") int index) throws IOException {		
		final ModelAndView mv = new ModelAndView("json");

        // get the requested gateway link entry from the list configured for
        // this portlet
        final List<GatewayEntry> entries =  (List<GatewayEntry>) applicationContext.getBean("gatewayEntries", List.class);
        final GatewayEntry entry = entries.get(index);

        // build a list of content requests
        final List<HttpContentRequestImpl> contentRequests = new ArrayList<HttpContentRequestImpl>();
        for (Map.Entry<HttpContentRequestImpl, List<String>> requestEntry : entry.getContentRequests().entrySet()){

            // run each content request through any configured preinterceptors
            // before adding it to the list.  Use a clone so that preinterceptors can change the
            // values without impacting future executions (e.g. need to retain substitution tokens).
            final HttpContentRequestImpl contentRequest = requestEntry.getKey().duplicate();
            for (String interceptorKey : requestEntry.getValue()) {
                final IPreInterceptor interceptor = applicationContext.getBean(interceptorKey, IPreInterceptor.class);
                interceptor.intercept(contentRequest, portletRequest);
            }
            contentRequests.add(contentRequest);
        }

        // add custom form field processing logic to the ModelAndView
        for (IAuthenticationFormModifier authenticationFormModifier :
                (List<IAuthenticationFormModifier>) entry.getAuthenticationFormModifier()) {
        	String fieldName = authenticationFormModifier.getFieldName();
        	String result = authenticationFormModifier.getResult(portletRequest.getPreferences());
        	for (HttpContentRequestImpl contentRequest : contentRequests) {
            	contentRequest.addParameter(fieldName, result);        		
        	}
        }

        // If the content parameters specify a proxiedLocation, override the contentRequest's proxiedLocation value.
        // Also insure the proxiedLocation value is secure (HTTPS) if required.
        for (HttpContentRequestImpl contentRequest : contentRequests) {
            if (contentRequest.getParameters().get(IAuthenticationFormModifier.PROXY_LOCATION_OVERRIDE) != null) {
                contentRequest.setProxiedLocation(contentRequest.getParameters().get(IAuthenticationFormModifier.PROXY_LOCATION_OVERRIDE).getValue());
            }
            if (entry.isRequireSecure() && StringUtils.isNotBlank(contentRequest.getProxiedLocation())
                    && contentRequest.getProxiedLocation().length() >= HTTPS.length()) {
                if (!HTTPS.equalsIgnoreCase(contentRequest.getProxiedLocation().substring(0, HTTPS.length()))) {
                    logger.error("Proxied location '" + contentRequest.getProxiedLocation() + "' for gateway entry "
                            + entry.getName() + " is not secure - discarding entry!!!");
                    contentRequest.setParameters(new HashMap<String, IFormField>());
                    contentRequest.setProxiedLocation("/HTTPSUrlRequiredButNotSpecified");   // Force a failure that's clear
                }
            }
        }

        mv.addObject("contentRequests", contentRequests);

        // we don't want this response to be cached by the browser since it may
        // include one-time-only authentication tokens
        // See http://stackoverflow.com/questions/49547/making-sure-a-web-page-is-not-cached-across-all-browsers
        portletResponse.setProperty("Cache-Control", "no-cache, no-store, must-revalidate"); // HTTP 1.1.
        portletResponse.setProperty("Pragma", "no-cache"); // HTTP 1.0.
        portletResponse.setProperty("Expires", "0"); // Proxies.

        return mv;
    }

    @ResourceMapping(value = "showTargetInNewWindow")
    public String showTargetInNewWindow(ResourceRequest portletRequest, ResourceResponse portletResponse, Model model,
                                              @RequestParam("index") int index) throws IOException {
        model.addAttribute("index", index);
        return "gatewayNewPage";
    }


}
