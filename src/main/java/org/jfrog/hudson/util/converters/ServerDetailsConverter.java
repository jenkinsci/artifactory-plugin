package org.jfrog.hudson.util.converters;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.util.XStream2;
import org.apache.commons.lang.StringUtils;
import org.jfrog.hudson.RepositoryConf;
import org.jfrog.hudson.ServerDetails;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Lior Hasson
 */
public class ServerDetailsConverter extends XStream2.PassthruConverter<ServerDetails> {
    // mapping of the old ServerDetails field to the corresponding new field
    private static final Map<String, String> newToOldFields;

    static {
        newToOldFields = new HashMap<String, String>();
        newToOldFields.put("repositoryKey", "deployReleaseRepository");
        newToOldFields.put("snapshotsRepositoryKey", "deploySnapshotRepository");
        newToOldFields.put("downloadSnapshotRepositoryKey", "resolveSnapshotRepository");
        newToOldFields.put("downloadReleaseRepositoryKey", "resolveReleaseRepository");
    }
    public ServerDetailsConverter(XStream2 xstream) {
        super(xstream);
    }

    public void convertToReleaseAndSnapshotRepository(ServerDetails server) throws NoSuchFieldException, IllegalAccessException {
        Class<? extends ServerDetails> overrideClass = server.getClass();

        Field oldReleaseRepositoryField = overrideClass.getDeclaredField("downloadRepositoryKey");
        oldReleaseRepositoryField.setAccessible(true);
        Object oldReleaseRepositoryValue = oldReleaseRepositoryField.get(server);

        if (oldReleaseRepositoryValue != null && StringUtils.isNotBlank((String) oldReleaseRepositoryValue)) {
            Field newReleaseRepositoryField = overrideClass.getDeclaredField("downloadReleaseRepositoryKey");
            newReleaseRepositoryField.setAccessible(true);
            newReleaseRepositoryField.set(server, oldReleaseRepositoryValue);

            Field newSnapshotRepositoryField = overrideClass.getDeclaredField("downloadSnapshotRepositoryKey");
            newSnapshotRepositoryField.setAccessible(true);
            newSnapshotRepositoryField.set(server, oldReleaseRepositoryValue);
        }
    }

    public void convertToDynamicReposSelection(ServerDetails server) throws NoSuchFieldException, IllegalAccessException {
        Class<? extends ServerDetails> overrideClass = server.getClass();
        for (Map.Entry<String, String> e : newToOldFields.entrySet()) {
            setNewReposFieldFromOld(server, overrideClass, e.getKey(), e.getValue());
        }
    }

    private void setNewReposFieldFromOld(Object reflectedObject, Class classToChange, String oldFieldName,
                                         String newFieldName) throws NoSuchFieldException, IllegalAccessException {
        Field oldField = classToChange.getDeclaredField(oldFieldName);
        oldField.setAccessible(true);
        String oldValue = (String) oldField.get(reflectedObject);
        if (StringUtils.isNotBlank(oldValue)) {
            Field newField = classToChange.getDeclaredField(newFieldName);
            RepositoryConf newValue = new RepositoryConf(oldValue, oldValue, false);
            newField.setAccessible(true);
            newField.set(reflectedObject, newValue);
        }
    }

    @Override
    protected void callback(ServerDetails server, UnmarshallingContext context) {
        try {
            convertToReleaseAndSnapshotRepository(server);
            convertToDynamicReposSelection(server);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(getConversionErrorMessage(server), e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(getConversionErrorMessage(server), e);
        }
    }

    private String getConversionErrorMessage(ServerDetails serverDetails) {
        return String.format("Could not convert the class '%s' to use the new overriding Resolve repositories."
                , serverDetails.getClass().getName());
    }
}
