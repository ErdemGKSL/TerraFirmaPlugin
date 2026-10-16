/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

/**
 * Service-provider entry of an adapter module ({@code META-INF/services/dev.tfp.paper.integration.IntegrationProvider}).
 * Implementations must not reference the client plugin's classes in their own signatures, fields or
 * constructor: they are instantiated even when the plugin is absent. {@link #create} is called only when
 * the plugin is installed and may then load the adapter class that links against the plugin.
 */
public interface IntegrationProvider {
    /** The plugin this adapter needs, as in its descriptor. */
    String pluginName();

    Integration create(IntegrationContext context);
}
