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
import org.jfrog.hudson.util.Credentials;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

/**
 *
 * @author Lior Hasson
 */
public class ArtifactoryServerConverter extends XStream2.PassthruConverter<ArtifactoryServer>{
    List<String> converterErrors = Lists.newArrayList();
    public ArtifactoryServerConverter(XStream2 xstream) {
        super(xstream);
    }

    @Override
    protected void callback(ArtifactoryServer server, UnmarshallingContext context) {
        oldResolverCredentials(server);
        credentialsMigration(server);

        if(!converterErrors.isEmpty()){
//            throw new RuntimeException(converterErrors.toString());
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
                        server, new Credentials((String)userName, Scrambler.descramble((String)password)));
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

        if (deployerCredentials != null) {
            CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            String userName = ((Credentials) deployerCredentials).getUsername();
            String password = ((Credentials) deployerCredentials).getPassword();

            if (StringUtils.isNotBlank(userName)) {
                UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, null, "Migrated from Artifactory plugin", userName, password
                );

                if (store.addCredentials(Domain.global(), usernamePasswordCredentials)) {
                    int credentialsIndex = store.getCredentials(
                            Domain.global()).lastIndexOf(usernamePasswordCredentials);
                    String newCredentialsId = ((UsernamePasswordCredentialsImpl) store.getCredentials(
                            Domain.global()).get(credentialsIndex)).getId();


                    Field deployerCredentialsIdField = overriderClass.getDeclaredField("deployerCredentialsId");
                    deployerCredentialsIdField.setAccessible(true);
                    deployerCredentialsIdField.set(server, newCredentialsId);
                }
            }
        }
    }

    private void resolverMigration(ArtifactoryServer server) throws NoSuchFieldException, IllegalAccessException, IOException {
        Class<? extends ArtifactoryServer> overriderClass = server.getClass();
        Field resolverCredentialsField = overriderClass.getDeclaredField("resolverCredentials");
        resolverCredentialsField.setAccessible(true);
        Object resolverCredentials = resolverCredentialsField.get(server);

        if (resolverCredentials != null) {
            CredentialsStore store = CredentialsProvider.lookupStores(Jenkins.getInstance()).iterator().next();
            String userName = ((Credentials) resolverCredentials).getUsername();
            String password = ((Credentials) resolverCredentials).getPassword();

            if (StringUtils.isNotBlank(userName)) {
                UsernamePasswordCredentialsImpl usernamePasswordCredentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, null, "Migrated from Artifactory plugin.", userName, password
                );

                if (store.addCredentials(Domain.global(), usernamePasswordCredentials)) {
                    int credentialsIndex = store.getCredentials(
                            Domain.global()).lastIndexOf(usernamePasswordCredentials);
                    String newCredentialsId = ((UsernamePasswordCredentialsImpl) store.getCredentials(
                            Domain.global()).get(credentialsIndex)).getId();


                    Field deployerCredentialsIdField = overriderClass.getDeclaredField("resolverCredentialsId");
                    deployerCredentialsIdField.setAccessible(true);
                    deployerCredentialsIdField.set(server, newCredentialsId);
                }
            }
        }
    }

    private String getConversionErrorMessage(ArtifactoryServer artifactoryServer, Exception e) {
        return String.format("Could not convert the class '%s' to use the new overriding" +
                "format. Cause: %s", artifactoryServer.getClass().getName(), e.getCause());
    }
}

