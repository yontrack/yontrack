package net.nemerosa.ontrack.model.support;

import java.util.function.Function;

/**
 * Reading the fields of the settings, stored by category - the class of the settings - and name.
 * <p>
 * A blank or missing value gives the default value.
 *
 * @see SettingsRepository for one query per field
 * @see SettingsValues for the fields of one category, read in one query
 */
public interface SettingsReader {

    boolean getBoolean(Class<?> category, String name, boolean defaultValue);

    int getInt(Class<?> category, String name, int defaultValue);

    long getLong(Class<?> category, String name, long defaultValue);

    String getString(Class<?> category, String name, String defaultValue);

    String getPassword(Class<?> category, String name, String defaultValue, Function<String, String> decryptService);

}
