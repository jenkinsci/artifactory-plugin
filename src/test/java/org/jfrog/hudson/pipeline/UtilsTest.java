package org.jfrog.hudson.pipeline;

import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertTrue;

public class UtilsTest {

    private static Method invokeEscapeUnixMethod;

    @BeforeClass
    public static void init() throws NoSuchMethodException {
        invokeEscapeUnixMethod = Utils.class.getDeclaredMethod("escapeUnixArgument", String.class);
        invokeEscapeUnixMethod.setAccessible(true);
    }

    @Test
    public void escapeUnixArgumentTest() throws InvocationTargetException, IllegalAccessException {
        validateEscapeUnixArgument("abcd", "abcd");
        validateEscapeUnixArgument("*abcd", "\\*abcd");
        validateEscapeUnixArgument("?abcd", "\\?abcd");
        validateEscapeUnixArgument("[abcd", "\\[abcd");
        validateEscapeUnixArgument("]abcd", "\\]abcd");
        validateEscapeUnixArgument("a*bcd", "a\\*bcd");
        validateEscapeUnixArgument("a?bcd", "a\\?bcd");
        validateEscapeUnixArgument("a[bcd", "a\\[bcd");
        validateEscapeUnixArgument("a]bcd", "a\\]bcd");
        validateEscapeUnixArgument("abcd*", "abcd\\*");
        validateEscapeUnixArgument("abcd?", "abcd\\?");
        validateEscapeUnixArgument("abcd[", "abcd\\[");
        validateEscapeUnixArgument("abcd]", "abcd\\]");
        validateEscapeUnixArgument("*", "\\*");
        validateEscapeUnixArgument("?", "\\?");
        validateEscapeUnixArgument("[", "\\[");
        validateEscapeUnixArgument("]", "\\]");
        validateEscapeUnixArgument("", "");
        validateEscapeUnixArgument("a*?[]b", "a\\*\\?\\[\\]b");
    }

    private void validateEscapeUnixArgument(String argToTest, String expected) throws InvocationTargetException, IllegalAccessException {
        String res = (String) invokeEscapeUnixMethod.invoke(Utils.class, argToTest);
        assertTrue(expected.equals(res));
    }
}
