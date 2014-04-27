package org.jfrog.hudson.util;

import org.jfrog.hudson.ServerDetails;

/**
 * Created by liorh on 4/26/14.
 */
public class ResolverReleaseRepoConverter<T extends ServerDetails> {

//    public ResolverReleaseRepoConverter(XStream2 xstream) {
//        super(xstream);
//    }

//    @Override
//    protected void callback(T obj, UnmarshallingContext context) {
//        Class<? extends ServerDetails> overrideClass = obj.getClass();
//
//        try {
//            Field oldReleaseRepositryField = overrideClass.getDeclaredField("downloadRepositoryKey");
//            oldReleaseRepositryField.setAccessible(true);
//            Object oldReleaseRepositoryValue = oldReleaseRepositryField.get(obj);
//
//            if(oldReleaseRepositoryValue != null && StringUtils.isNotBlank((String) oldReleaseRepositoryValue)){
//                Field newReleaseRepositryField = overrideClass.getDeclaredField("downloadReleaseRepositoryKey");
//                newReleaseRepositryField.setAccessible(true);
//                newReleaseRepositryField.set(obj,oldReleaseRepositoryValue);
//            }
//
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//    }
}
