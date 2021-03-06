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

package org.keycloak.authorization.model;

import java.util.Map;
import java.util.Set;

/**
 * Represents an authorization policy and all the configuration associated with it.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public interface Policy {

    /**
     * Returns the unique identifier for this instance.
     *
     * @return the unique identifier for this instance
     */
    String getId();

    /**
     * Returns the type of this policy.
     *
     * @return the type of this policy
     */
    String getType();

    /**
     * Returns the {@link DecisionStrategy} for this policy.
     *
     * @return the decision strategy defined for this policy
     */
    DecisionStrategy getDecisionStrategy();

    /**
     * Sets the {DecisionStrategy} for this policy.
     *
     * @return the decision strategy for this policy
     */
    void setDecisionStrategy(DecisionStrategy decisionStrategy);

    /**
     * Returns the {@link Logic} for this policy.
     *
     * @return the decision strategy defined for this policy
     */
    Logic getLogic();

    /**
     * Sets the {Logic} for this policy.
     *
     * @return the decision strategy for this policy
     */
    void setLogic(Logic logic);

    /**
     * Returns a {@link Map} holding string-based key/value pairs representing any additional configuration for this policy.
     *
     * @return a map with any additional configuration defined for this policy.
     */
    Map<String, String> getConfig();

    /**
     * Sets a {@link Map} with string-based key/value pairs representing any additional configuration for this policy.
     *
     * @return a map with any additional configuration for this policy.
     */
    void setConfig(Map<String, String> config);

    /**
     * Returns the name of this policy.
     *
     * @return the name of this policy
     */
    String getName();

    /**
     * Sets an unique name to this policy.
     *
     * @param name an unique name
     */
    void setName(String name);

    /**
     * Returns the description of this policy.
     *
     * @return a description or null of there is no description
     */
    String getDescription();

    /**
     * Sets the description for this policy.
     *
     * @param description a description
     */
    void setDescription(String description);

    /**
     * Returns the {@link ResourceServer} where this policy belongs to.
     *
     * @return a resource server
     */
    <R extends ResourceServer> R getResourceServer();

    /**
     * Returns the {@link Policy} instances associated with this policy and used to evaluate authorization decisions when
     * this policy applies.
     *
     * @return the associated policies or an empty set if no policy is associated with this policy
     */
    <P extends Policy> Set<P> getAssociatedPolicies();

    /**
     * Returns the {@link Resource} instances where this policy applies.
     *
     * @return a set with all resource instances where this policy applies. Or an empty set if there is no resource associated with this policy
     */
    <R extends Resource> Set<R> getResources();

    /**
     * Returns the {@link Scope} instances where this policy applies.
     *
     * @return a set with all scope instances where this policy applies. Or an empty set if there is no scope associated with this policy
     */
    <S extends Scope> Set<S> getScopes();

    void addScope(Scope scope);

    void removeScope(Scope scope);

    void addAssociatedPolicy(Policy associatedPolicy);

    void removeAssociatedPolicy(Policy associatedPolicy);

    void addResource(Resource resource);

    void removeResource(Resource resource);

    /**
     * The decision strategy dictates how the policies associated with a given policy are evaluated and how a final decision
     * is obtained.
     */
    enum DecisionStrategy {
        /**
         * Defines that at least one policy must evaluate to a positive decision in order to the overall decision be also positive.
         */
        AFFIRMATIVE,

        /**
         * Defines that all policies must evaluate to a positive decision in order to the overall decision be also positive.
         */
        UNANIMOUS,

        /**
         * Defines that the number of positive decisions must be greater than the number of negative decisions. If the number of positive and negative is the same,
         * the final decision will be negative.
         */
        CONSENSUS
    }

    /**
     * The decision strategy dictates how the policies associated with a given policy are evaluated and how a final decision
     * is obtained.
     */
    enum Logic {
        /**
         * Defines that this policy follows a positive logic. In other words, the final decision is the policy outcome.
         */
        POSITIVE,

        /**
         * Defines that this policy uses a logical negation. In other words, the final decision would be a negative of the policy outcome.
         */
        NEGATIVE,
    }
}
