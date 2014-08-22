package io.hummer.util.ws;

import io.hummer.util.Configuration;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.ParameterStyle;
import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@WebService
public interface IAbstractNode {
	
	@XmlRootElement(name="setHandlerActive", namespace=Configuration.NAMESPACE)
	public static class SetHandlerActiveRequest {
		@XmlElement
		public String handlerSuperClass;
		@XmlElement
		public boolean active;
		public SetHandlerActiveRequest() {}
		public SetHandlerActiveRequest(String handlerSuperClass, boolean active) {
			this.handlerSuperClass = handlerSuperClass;
			this.active = active;
		}
	}
	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE, style=Style.DOCUMENT, use=Use.LITERAL)
	void setHandlerActive(@WebParam final SetHandlerActiveRequest params) throws Exception;


	@XmlRootElement(name="terminate", namespace=Configuration.NAMESPACE)
	public static class TerminateRequest {
		@XmlElement
		public boolean recursive;
		public TerminateRequest() {}
		public TerminateRequest(boolean recursive) {
			this.recursive = recursive;
		}
	}
	@WebMethod
	@SOAPBinding(parameterStyle=ParameterStyle.BARE, style=Style.DOCUMENT, use=Use.LITERAL)
	void terminate(@WebParam final TerminateRequest params);

}
