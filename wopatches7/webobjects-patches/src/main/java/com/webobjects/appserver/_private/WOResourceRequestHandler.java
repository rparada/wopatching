package com.webobjects.appserver._private;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WODynamicURL;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WORequestHandler;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver.WOResponse;

public class WOResourceRequestHandler extends WORequestHandler {

	/**
	 * The default authorizer denies access to WO resource files that should
	 * probably be private.
	 */
	public static final Predicate<WORequest> DEFAULT_AUTHORIZER = req -> {
		Optional<Path> requestHandlerPath = Optional.of(req)
				.map(WORequest::_uriDecomposed)
				.map(WODynamicURL::requestHandlerPath)
				.map(Paths::get);
		String fileName = requestHandlerPath.map(Path::getFileName).map(Path::toString).orElse(null);
		String parentName = requestHandlerPath.map(Path::getParent).map(Path::toString).orElse(null);
		Predicate<String> denyFile = name -> name == null
				|| name.startsWith("Properties")
				|| name.endsWith(".plist")
				|| name.endsWith(".eomodeld")
				|| name.endsWith(".strings")
				|| name.endsWith(".sh")
				|| name.endsWith(".d2wmodel")
				|| name.endsWith(".d2wmodel.txt")
				|| name.endsWith(".eogen")
				|| name.endsWith(".api")
				|| name.endsWith(".class")
				|| name.endsWith(".properties");
		Predicate<String> denyDir = name -> name == null 
				|| name.endsWith(".wo")
				|| name.endsWith(".eomodel");
		return !denyFile.test(fileName) 
				&& !denyDir.test(parentName);
	};
	
	/**
	 * The default sanitizer just returns the request's handler path. But in the
	 * event someone finds a sneaky path to escape your bundle, it can be
	 * replaced.
	 */
	public static final Function<WORequest,String> DEFAULT_SANITIZER = req -> {
		String requestHandlerPath = req._uriDecomposed().requestHandlerPath();
		
		return requestHandlerPath;
	};
	
	private final Predicate<WORequest> authorize;
	
	private final Function<WORequest,String> sanitize;
	
	public WOResourceRequestHandler() {
		this(DEFAULT_AUTHORIZER, DEFAULT_SANITIZER);
	}
	
	public WOResourceRequestHandler(Predicate<WORequest> authorizer, Function<WORequest, String> sanitizer) {
		Objects.requireNonNull(authorizer);
		Objects.requireNonNull(sanitizer);
		this.authorize = authorizer;
		this.sanitize = sanitizer;
	}
	
	@Override
	public WOResponse handleRequest(WORequest request) {
		WOApplication app = WOApplication.application();
		WOResponse response = app.createResponseInContext(request.context());
		if(!authorize.test(request)) {
			response.setStatus(403);
			response.setHeader("text/html", "content-type");
			response.setContent("Forbidden");
		} else {
			String handlerPath = sanitize.apply(request);
			WOResourceManager rm = app.resourceManager();
			String contentType = rm.contentTypeForResourceNamed(handlerPath);
			response.setHeader(contentType, "content-type");
			WOURLValuedElementData data = rm._cachedDataForKey(handlerPath);
			if(data == null) {
				response.setStatus(404);
			} else {
				response.setContent(data.data());
				response.setHeader(String.valueOf(data.data().length()), "content-length");
			}
		}
		return response;
	}

}
