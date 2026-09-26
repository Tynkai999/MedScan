package com.medscan.security.filter;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.Variant;
import jakarta.ws.rs.ext.RuntimeDelegate;

/**
 * Lightweight RuntimeDelegate for standalone unit tests outside an application container.
 */
public class TestRuntimeDelegate extends RuntimeDelegate {

    public static void install() {
        try {
            RuntimeDelegate.setInstance(new TestRuntimeDelegate());
        } catch (SecurityException ignored) {
        }
    }

    @Override
    public UriBuilder createUriBuilder() {
        return null;
    }

    @Override
    public Response.ResponseBuilder createResponseBuilder() {
        return new TestResponseBuilder();
    }

    @Override
    public Variant.VariantListBuilder createVariantListBuilder() {
        return null;
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) {
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        if (type == MediaType.class) {
            return (HeaderDelegate<T>) new HeaderDelegate<MediaType>() {
                @Override
                public MediaType fromString(String value) {
                    if (value == null) return null;
                    String[] parts = value.split("/");
                    return parts.length == 2 ? new MediaType(parts[0].trim(), parts[1].trim()) : new MediaType();
                }

                @Override
                public String toString(MediaType value) {
                    return value == null ? "" : value.toString();
                }
            };
        }
        return null;
    }

    @Override
    public Link.Builder createLinkBuilder() {
        return null;
    }

    @Override
    public jakarta.ws.rs.core.EntityPart.Builder createEntityPartBuilder(String partName) {
        return null;
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Application application, SeBootstrap.Configuration configuration) {
        return null;
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Class<? extends Application> clazz, SeBootstrap.Configuration configuration) {
        return null;
    }

    @Override
    public SeBootstrap.Configuration.Builder createConfigurationBuilder() {
        return null;
    }

    static class TestResponseBuilder extends Response.ResponseBuilder {
        private int status;
        private Object entity;
        private MediaType mediaType;
        private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();

        @Override
        public Response build() {
            return new TestResponse(status, entity, mediaType, headers);
        }

        @Override
        public Response.ResponseBuilder clone() {
            return this;
        }

        @Override
        public Response.ResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        @Override
        public Response.ResponseBuilder status(int status, String reasonPhrase) {
            this.status = status;
            return this;
        }

        @Override
        public Response.ResponseBuilder entity(Object entity) {
            this.entity = entity;
            return this;
        }

        @Override
        public Response.ResponseBuilder entity(Object entity, Annotation[] annotations) {
            this.entity = entity;
            return this;
        }

        @Override
        public Response.ResponseBuilder type(MediaType type) {
            this.mediaType = type;
            return this;
        }

        @Override
        public Response.ResponseBuilder type(String type) {
            this.mediaType = (type == null) ? null : MediaType.valueOf(type);
            return this;
        }

        @Override
        public Response.ResponseBuilder header(String name, Object value) {
            this.headers.add(name, value);
            return this;
        }

        @Override
        public Response.ResponseBuilder allow(String... methods) {
            return this;
        }

        @Override
        public Response.ResponseBuilder allow(Set<String> methods) {
            return this;
        }

        // Dummy overrides
        @Override public Response.ResponseBuilder variant(Variant variant) { return this; }
        @Override public Response.ResponseBuilder variants(java.util.List<Variant> variants) { return this; }
        @Override public Response.ResponseBuilder variants(Variant... variants) { return this; }
        @Override public Response.ResponseBuilder language(String language) { return this; }
        @Override public Response.ResponseBuilder language(Locale language) { return this; }
        @Override public Response.ResponseBuilder location(URI location) { return this; }
        @Override public Response.ResponseBuilder contentLocation(URI location) { return this; }
        @Override public Response.ResponseBuilder encoding(String encoding) { return this; }
        @Override public Response.ResponseBuilder tag(EntityTag tag) { return this; }
        @Override public Response.ResponseBuilder tag(String tag) { return this; }
        @Override public Response.ResponseBuilder lastModified(Date lastModified) { return this; }
        @Override public Response.ResponseBuilder cacheControl(CacheControl cacheControl) { return this; }
        @Override public Response.ResponseBuilder expires(Date expires) { return this; }
        @Override public Response.ResponseBuilder cookie(NewCookie... cookies) { return this; }
        @Override public Response.ResponseBuilder replaceAll(MultivaluedMap<String, Object> headers) { return this; }
        @Override public Response.ResponseBuilder link(URI uri, String rel) { return this; }
        @Override public Response.ResponseBuilder link(String uri, String rel) { return this; }
        @Override public Response.ResponseBuilder links(Link... links) { return this; }
    }

    static class TestResponse extends Response {
        private final int status;
        private final Object entity;
        private final MediaType mediaType;
        private final MultivaluedMap<String, Object> headers;

        TestResponse(int status, Object entity, MediaType mediaType, MultivaluedMap<String, Object> headers) {
            this.status = status;
            this.entity = entity;
            this.mediaType = mediaType;
            this.headers = headers;
        }

        @Override public int getStatus() { return status; }
        @Override public StatusType getStatusInfo() { return Status.fromStatusCode(status); }
        @Override public Object getEntity() { return entity; }
        @Override public <T> T readEntity(Class<T> entityType) { return entityType.cast(entity); }
        @Override public <T> T readEntity(GenericType<T> entityType) { return null; }
        @Override public <T> T readEntity(Class<T> entityType, Annotation[] annotations) { return entityType.cast(entity); }
        @Override public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) { return null; }
        @Override public boolean hasEntity() { return entity != null; }
        @Override public boolean bufferEntity() { return false; }
        @Override public void close() {}
        @Override public MediaType getMediaType() { return mediaType; }
        @Override public Locale getLanguage() { return null; }
        @Override public int getLength() { return 0; }
        @Override public Set<String> getAllowedMethods() { return Set.of(); }
        @Override public Map<String, NewCookie> getCookies() { return Map.of(); }
        @Override public EntityTag getEntityTag() { return null; }
        @Override public Date getDate() { return null; }
        @Override public Date getLastModified() { return null; }
        @Override public URI getLocation() { return null; }
        @Override public Set<Link> getLinks() { return Set.of(); }
        @Override public boolean hasLink(String relation) { return false; }
        @Override public Link getLink(String relation) { return null; }
        @Override public Link.Builder getLinkBuilder(String relation) { return null; }
        @Override public MultivaluedMap<String, Object> getMetadata() { return headers; }
        @Override public MultivaluedMap<String, String> getStringHeaders() { return new MultivaluedHashMap<>(); }
        @Override public String getHeaderString(String name) { return headers.getFirst(name) != null ? headers.getFirst(name).toString() : null; }
    }
}
