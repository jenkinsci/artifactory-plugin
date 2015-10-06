package org.jfrog.hudson.util.converters;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.Scrambler;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.ArtifactoryServer;
import org.jfrog.hudson.CredentialsConfig;
import org.jfrog.hudson.util.Credentials;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Lior Hasson
 */
public class ArtifactoryServerConverter extends XStream2.PassthruConverter<ArtifactoryServer> {
    Logger logger = Logger.getLogger(ArtifactoryServerConverter.class.getName());
    List<String> converterErrors = Lists.newArrayList();

    public ArtifactoryServerConverter(XStream2 xstream) {
        super(xstream);
    }

    @Override
    protected void callback(ArtifactoryServer server, UnmarshallingContext context) {
        oldResolverCredentials(server);
        credentialsMigration(server);

        if (!converterErrors.isEmpty()) {
            logger.info(converterErrors.toString());
        }
    }

    /**
     * When upgrading from an older version, a user might have resolver credentials as local variables. This converter
     * Will check for existing old resolver credentials and "move" them to a credentials object instead
     */
    private void oldResolverCredentials(ArtifactoryServer server) {
        Class<? extends ArtifactoryServer> overriderClass = server.getClass();
        try {
            Field userNameField = overriderClass.getDeclaredField("userName");
            userNameField.setAccessible(true);
            Object userName = userNameField.get(server);

            Field resolverCredentialsField = overriderClass.getDeclaredField("resolverCredentials");
            resolverCredentialsField.setAccessible(true);
            Object resolverCredentials = resolverCredentialsField.get(server);

            Field passwordField = overriderClass.getDeclaredField("password");
            passwordField.setAccessible(true);
            Object password = passwordField.get(server);

            if (userName != null && StringUtils.isNotBlank((String) userName) && (resolverCredentials == null)) {
                resolverCredentialsField.set(
                        server, new Credentials((String) userName, Scrambler.descramble((String) password)));
            }

        } catch (NoSuchFieldException e) {
            converterErrors.add(getConversionErrorMessage(server, e));
        } catch (IllegalAccessException e) {
            converterErrors.add(getConversionErrorMessage(server, e));
        }
    }

    private void credentialsMigration(ArtifactoryServer server) {
        try {
            deployerMigration(server);
            resolverMigration(server);
        } catch (NoSuchFieldException e) {
            converterErrors.add(getConversionErrorMessage(server, e));
        } catch (IllegalAccessException e) {
            converterErrors.add(getConversionErrorMessage(server, e));
        } catch (IOException e) {
            converterErrors.add(getConversionErrorMessage(server, e));
        }
    }

    private void deployerMigration(ArtifactoryServer server) throws NoSuchFieldException, IllegalAccessException, IOException {
        Class<? extends ArtifactoryServer> overriderClass = server.getClass();
        Field deployerCredentialsField = overriderClass.getDeclaredField("deployerCredentials");
        deployerCredentialsField.setAccessible(true);
        Object deployerCredentials = deployerCredentialsField.get(server);

        Field deployerCredentialsConfigField = overriderClass.getDeclaredField("deployerCredentialsConfig");
        deployerCredentialsConfigField.setAccessible(true);

        if (deployerCredentials != null) {
            CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            String userName = ((Credentials) deployerCredentials).getUsername();
            String password = ((Credentials) deployerCredentials).getPassword();

            if (StringUtils.isNotBlank(userName)) {
                String credentialId = userName + ":" + password + ":" + overriderClass.getName() + ":deployer";
                UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId,
                        "Migrated from Artifactory plugin (" + server.getUrl() + ")", userName, password
                );

                if (!store.getCredentials(Domain.global()).contains(usernamePasswordCredentials)) {
                    store.addCredentials(Domain.global(), usernamePasswordCredentials);
                }

                deployerCredentialsConfigField.set(server, new CredentialsConfig(new Credentials(userName, password), credentialId));
            }
        } else {
            deployerCredentialsField.set(server, CredentialsConfig.createEmptyCredentialsConfigObject());
        }
    }

    private void resolverMigration(ArtifactoryServer server)
            throws NoSuchFieldException, IllegalAccessException, IOException {
        Class<? extends ArtifactoryServer> overriderClass = server.getClass();
        Field resolverCredentialsField = overriderClass.getDeclaredField("resolverCredentials");
        resolverCredentialsField.setAccessible(true);
        Object resolverCredentials = resolverCredentialsField.get(server);

        Field deployerCredentialsConfigField = overriderClass.getDeclaredField("resolverCredentialsConfig");
        deployerCredentialsConfigField.setAccessible(true);

        if (resolverCredentials != null) {
            CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            String userName = ((Credentials) resolverCredentials).getUsername();
            String password = ((Credentials) resolverCredentials).getPassword();

            if (StringUtils.isNotBlank(userName)) {
                String credentialId = userName + ":" + password + ":" + overriderClass.getName() + ":resolver";
                UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId, "Migrated from Artifactory plugin (" + server.getUrl() + ")",
                        userName, password
                );

                if (!store.getCredentials(Domain.global()).contains(usernamePasswordCredentials)) {
                    store.addCredentials(Domain.global(), usernamePasswordCredentials);
                }

                deployerCredentialsConfigField.set(server, new CredentialsConfig(new Credentials(userName, password), credentialId));
            }
        } else {
            deployerCredentialsConfigField.set(server, CredentialsConfig.createEmptyCredentialsConfigObject());
        }
    }

    private String getConversionErrorMessage(ArtifactoryServer artifactoryServer, Exception e) {
        return String.format("Could not convert the class '%s' to use the new overriding" +
                "format. Cause: %s", artifactoryServer.getClass().getName(), e.getCause());
    }
}