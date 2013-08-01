package org.jasig.portlet.proxy.service.web;

import java.io.IOException;
import java.net.URL;

import javax.portlet.PortletPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UsdWebAdvisorFormModifier implements IAuthenticationFormModifier {
	
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());
	private String fieldName;
	
	public UsdWebAdvisorFormModifier() {
	}
	
	@Override
	public String getResult(PortletPreferences preferences) throws IOException {
	      String urlSource ="https://wa-usd.prod.sdbor.edu/WebAdvisor/webadvisor?&TYPE=M&PID=CORE-WBMAIN&TOKENIDX="; //must be secure
	      URL xmlURLToOpen = new URL(urlSource);

	      String headerInfo = xmlURLToOpen.openConnection().getHeaderFields().toString();
	      int headerInfoBegin = headerInfo.indexOf("LASTTOKEN=");
	      String tokenID = headerInfo.substring(headerInfoBegin+10,headerInfoBegin+20);
	      tokenID = tokenID.replaceAll("=","");
	      tokenID = tokenID.replaceAll(",","");
	      logger.debug("urlSource: " + urlSource);
	      String formAction = urlSource+tokenID+"&SS=LGRQ&URL=https%3A%2F%2Fwa-usd.prod.sdbor.edu%2FWebAdvisor%2Fwebadvisor%3F%26TYPE%3DM%26PID%3DCORE-WBMAIN%26TOKENIDX%3D"+tokenID;
	      logger.debug("formAction: " + formAction);
		return formAction;
	}
	
	@Override
	public String getFieldName() {
		return this.fieldName;
	}
	
	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

}
