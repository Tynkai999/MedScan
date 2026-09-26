package com.medscan.security.filter;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;

public class FakeContainerRequestContext implements ContainerRequestContext {

    private final Map<String, String> headers = new HashMap<>();
    private final String path;
    private SecurityContext securityContext;
    private Response abortedWith;

    public FakeContainerRequestContext(String path) {
        this.path = path;
    }

    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
    }

    public Response getAbortedWith() {
        return abortedWith;
    }

    @Override
    public String getHeaderString(String name) {
        return headers.get(name.toLowerCase());
    }

    @Override
    public SecurityContext getSecurityContext() {
        return securityContext;
    }

    @Override
    public void setSecurityContext(SecurityContext context) {
        this.securityContext = context;
    }

    @Override
    public void abortWith(Response response) {
        this.abortedWith = response;
    }

    @Override
    public UriInfo getUriInfo() {
        return new FakeUriInfo(path);
    }

    // Default implementations for unused methods
    @Override public Object getProperty(String name) { return null; }
    @Override public Collection<String> getPropertyNames() { return List.of(); }
    @Override public void setProperty(String name, Object object) {}
    @Override public void removeProperty(String name) {}
    @Override public void setRequestUri(URI requestUri) {}
    @Override public void setRequestUri(URI baseUri, URI requestUri) {}
    @Override public Request getRequest() { return null; }
    @Override public String getMethod() { return "GET"; }
    @Override public void setMethod(String method) {}
    @Override public boolean containsHeaderString(String name, java.util.function.Predicate<String> valuePredicate) {
        String val = getHeaderString(name);
        return val != null && valuePredicate.test(val);
    }
    @Override public boolean containsHeaderString(String name, String valueSeparator, java.util.function.Predicate<String> valuePredicate) {
        String val = getHeaderString(name);
        return val != null && valuePredicate.test(val);
    }
    @Override public MultivaluedMap<String, String> getHeaders() { return null; }
    @Override public Date getDate() { return null; }
    @Override public Locale getLanguage() { return null; }
    @Override public int getLength() { return 0; }
    @Override public MediaType getMediaType() { return null; }
    @Override public List<MediaType> getAcceptableMediaTypes() { return List.of(); }
    @Override public List<Locale> getAcceptableLanguages() { return List.of(); }
    @Override public Map<String, Cookie> getCookies() { return Map.of(); }
    @Override public boolean hasEntity() { return false; }
    @Override public InputStream getEntityStream() { return null; }
    @Override public void setEntityStream(InputStream input) {}
}
