package com.medscan.security.filter;

import java.net.URI;
import java.util.List;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

public class FakeUriInfo implements UriInfo {

    private final String path;

    public FakeUriInfo(String path) {
        this.path = (path == null) ? "" : path;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getPath(boolean decode) {
        return path;
    }

    // Default implementations for unused methods
    @Override public List<PathSegment> getPathSegments() { return List.of(); }
    @Override public List<PathSegment> getPathSegments(boolean decode) { return List.of(); }
    @Override public URI getRequestUri() { return URI.create("http://localhost:8080" + path); }
    @Override public UriBuilder getRequestUriBuilder() { return null; }
    @Override public URI getAbsolutePath() { return URI.create("http://localhost:8080" + path); }
    @Override public UriBuilder getAbsolutePathBuilder() { return null; }
    @Override public URI getBaseUri() { return URI.create("http://localhost:8080/medscan/api"); }
    @Override public UriBuilder getBaseUriBuilder() { return null; }
    @Override public MultivaluedMap<String, String> getPathParameters() { return null; }
    @Override public MultivaluedMap<String, String> getPathParameters(boolean decode) { return null; }
    @Override public MultivaluedMap<String, String> getQueryParameters() { return null; }
    @Override public MultivaluedMap<String, String> getQueryParameters(boolean decode) { return null; }
    @Override public List<String> getMatchedURIs() { return List.of(); }
    @Override public List<String> getMatchedURIs(boolean decode) { return List.of(); }
    @Override public List<Object> getMatchedResources() { return List.of(); }
    @Override public String getMatchedResourceTemplate() { return path; }
    @Override public URI resolve(URI uri) { return uri; }
    @Override public URI relativize(URI uri) { return uri; }
}
