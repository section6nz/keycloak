/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.authorization.entitlement;

import org.jboss.resteasy.spi.HttpRequest;
import org.keycloak.OAuthErrorException;
import org.keycloak.authorization.AuthorizationProvider;
import org.keycloak.authorization.common.KeycloakEvaluationContext;
import org.keycloak.authorization.common.KeycloakIdentity;
import org.keycloak.authorization.entitlement.representation.EntitlementRequest;
import org.keycloak.authorization.entitlement.representation.EntitlementResponse;
import org.keycloak.authorization.model.Resource;
import org.keycloak.authorization.model.ResourceServer;
import org.keycloak.authorization.permission.ResourcePermission;
import org.keycloak.authorization.policy.evaluation.DecisionResultCollector;
import org.keycloak.authorization.policy.evaluation.Result;
import org.keycloak.authorization.store.StoreFactory;
import org.keycloak.authorization.util.Permissions;
import org.keycloak.authorization.util.Tokens;
import org.keycloak.jose.jws.JWSInput;
import org.keycloak.jose.jws.JWSInputException;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.RealmModel;
import org.keycloak.protocol.oidc.TokenManager;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.authorization.Permission;
import org.keycloak.services.ErrorResponseException;
import org.keycloak.services.resources.Cors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class EntitlementService {

    private final AuthorizationProvider authorization;

    @Context
    private HttpRequest request;

    public EntitlementService(AuthorizationProvider authorization) {
        this.authorization = authorization;
    }

    @Path("{resource_server_id}")
    @OPTIONS
    public Response authorizePreFlight(@PathParam("resource_server_id") String resourceServerId) {
        return Cors.add(this.request, Response.ok()).auth().preflight().build();
    }

    @Path("{resource_server_id}")
    @GET()
    @Produces("application/json")
    @Consumes("application/json")
    public void getAll(@PathParam("resource_server_id") String resourceServerId, @Suspended AsyncResponse asyncResponse) {
        KeycloakIdentity identity = new KeycloakIdentity(this.authorization.getKeycloakSession());

        if (resourceServerId == null) {
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Requires resource_server_id request parameter.", Status.BAD_REQUEST);
        }

        RealmModel realm = this.authorization.getKeycloakSession().getContext().getRealm();
        ClientModel client = realm.getClientByClientId(resourceServerId);

        if (client == null) {
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Identifier is not associated with any client and resource server.", Status.BAD_REQUEST);
        }

        StoreFactory storeFactory = authorization.getStoreFactory();
        ResourceServer resourceServer = storeFactory.getResourceServerStore().findByClient(client.getId());

        authorization.evaluators().from(Permissions.all(resourceServer, identity, authorization), new KeycloakEvaluationContext(this.authorization.getKeycloakSession())).evaluate(new DecisionResultCollector() {

            @Override
            public void onError(Throwable cause) {
                asyncResponse.resume(cause);
            }

            @Override
            protected void onComplete(List<Result> results) {
                List<Permission> entitlements = Permissions.allPermits(results);

                if (entitlements.isEmpty()) {
                    asyncResponse.resume(Cors.add(request, Response.status(Status.FORBIDDEN)
                            .entity(new ErrorResponseException("not_authorized", "Authorization denied.", Status.FORBIDDEN)))
                            .allowedOrigins(identity.getAccessToken())
                            .exposedHeaders(Cors.ACCESS_CONTROL_ALLOW_METHODS).build());
                } else {
                    asyncResponse.resume(Cors.add(request, Response.ok().entity(new EntitlementResponse(createRequestingPartyToken(entitlements)))).allowedOrigins(identity.getAccessToken()).allowedMethods("GET").exposedHeaders(Cors.ACCESS_CONTROL_ALLOW_METHODS).build());
                }
            }
        });
    }

    @Path("{resource_server_id}")
    @POST
    @Consumes("application/json")
    @Produces("application/json")
    public void get(@PathParam("resource_server_id") String resourceServerId, EntitlementRequest entitlementRequest, @Suspended AsyncResponse asyncResponse) {
        KeycloakIdentity identity = new KeycloakIdentity(this.authorization.getKeycloakSession());

        if (entitlementRequest == null) {
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Invalid entitlement request.", Status.BAD_REQUEST);
        }

        if (resourceServerId == null) {
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Invalid resource_server_id.", Status.BAD_REQUEST);
        }

        RealmModel realm = this.authorization.getKeycloakSession().getContext().getRealm();

        ClientModel client = realm.getClientByClientId(resourceServerId);

        if (client == null) {
            throw new ErrorResponseException(OAuthErrorException.INVALID_REQUEST, "Identifier is not associated with any resource server.", Status.BAD_REQUEST);
        }

        StoreFactory storeFactory = authorization.getStoreFactory();
        ResourceServer resourceServer = storeFactory.getResourceServerStore().findByClient(client.getId());

        authorization.evaluators().from(createPermissions(entitlementRequest, resourceServer, authorization), new KeycloakEvaluationContext(this.authorization.getKeycloakSession())).evaluate(new DecisionResultCollector() {

            @Override
            public void onError(Throwable cause) {
                asyncResponse.resume(cause);
            }

            @Override
            protected void onComplete(List<Result> results) {
                List<Permission> entitlements = Permissions.allPermits(results);

                if (entitlements.isEmpty()) {
                    asyncResponse.resume(new ErrorResponseException("not_authorized", "Authorization denied.", Status.FORBIDDEN));
                } else {
                    asyncResponse.resume(Cors.add(request, Response.ok().entity(new EntitlementResponse(createRequestingPartyToken(entitlements)))).allowedOrigins(identity.getAccessToken()).allowedMethods("GET").exposedHeaders(Cors.ACCESS_CONTROL_ALLOW_METHODS).build());
                }
            }
        });
    }

    private String createRequestingPartyToken(List<Permission> permissions) {
        AccessToken accessToken = Tokens.getAccessToken(this.authorization.getKeycloakSession());
        RealmModel realm = this.authorization.getKeycloakSession().getContext().getRealm();
        AccessToken.Authorization authorization = new AccessToken.Authorization();

        authorization.setPermissions(permissions);

        accessToken.setAuthorization(authorization);
        ;
        return new TokenManager().encodeToken(realm, accessToken);
    }

    private List<ResourcePermission> createPermissions(EntitlementRequest entitlementRequest, ResourceServer resourceServer, AuthorizationProvider authorization) {
        StoreFactory storeFactory = authorization.getStoreFactory();
        Map<String, Set<String>> permissionsToEvaluate = new HashMap<>();

        entitlementRequest.getPermissions().forEach(requestedResource -> {
            Resource resource;

            if (requestedResource.getResourceSetId() != null) {
                resource = storeFactory.getResourceStore().findById(requestedResource.getResourceSetId());
            } else {
                resource = storeFactory.getResourceStore().findByName(requestedResource.getResourceSetName(), resourceServer.getId());
            }

            if (resource == null) {
                throw new ErrorResponseException("invalid_resource", "Resource with id [" + requestedResource.getResourceSetId() + "] or name [" + requestedResource.getResourceSetName() + "] does not exist.", Status.FORBIDDEN);
            }

            permissionsToEvaluate.put(resource.getId(), requestedResource.getScopes());
        });

        String rpt = entitlementRequest.getRpt();

        if (rpt != null && !"".equals(rpt)) {
            KeycloakContext context = authorization.getKeycloakSession().getContext();

            if (!Tokens.verifySignature(rpt, context.getRealm().getPublicKey())) {
                throw new ErrorResponseException("invalid_rpt", "RPT signature is invalid", Status.FORBIDDEN);
            }

            AccessToken requestingPartyToken;

            try {
                requestingPartyToken = new JWSInput(rpt).readJsonContent(AccessToken.class);
            } catch (JWSInputException e) {
                throw new ErrorResponseException("invalid_rpt", "Invalid RPT", Status.FORBIDDEN);
            }

            if (requestingPartyToken.isActive()) {
                AccessToken.Authorization authorizationData = requestingPartyToken.getAuthorization();

                if (authorizationData != null) {
                    authorizationData.getPermissions().forEach(permission -> {
                        Resource resourcePermission = storeFactory.getResourceStore().findById(permission.getResourceSetId());

                        if (resourcePermission != null) {
                            Set<String> scopes = permissionsToEvaluate.get(resourcePermission.getId());

                            if (scopes == null) {
                                scopes = new HashSet<>();
                                permissionsToEvaluate.put(resourcePermission.getId(), scopes);
                            }

                            scopes.addAll(permission.getScopes());
                        }
                    });
                }
            }
        }

        return permissionsToEvaluate.entrySet().stream()
                .flatMap((Function<Map.Entry<String, Set<String>>, Stream<ResourcePermission>>) entry -> {
                    Resource entryResource = storeFactory.getResourceStore().findById(entry.getKey());

                    if (entry.getValue().isEmpty()) {
                        return Arrays.asList(new ResourcePermission(entryResource, Collections.emptyList(), entryResource.getResourceServer())).stream();
                    } else {
                        return entry.getValue().stream()
                                .map(scopeName -> storeFactory.getScopeStore().findByName(scopeName, entryResource.getResourceServer().getId()))
                                .filter(scope -> scope != null)
                                .map(scope -> new ResourcePermission(entryResource, Arrays.asList(scope), entryResource.getResourceServer()));
                    }
                }).collect(Collectors.toList());
    }
}
