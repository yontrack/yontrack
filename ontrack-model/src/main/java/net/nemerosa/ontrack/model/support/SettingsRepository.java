package net.nemerosa.ontrack.model.support;

import java.util.Map;
import java.util.function.Function;

/**
 * Storage of the settings.
 * <p>
 * A {@link net.nemerosa.ontrack.model.settings.SettingsProvider} reads the fields of its settings
 * from {@code settingsRepository.values(category)}, a {@link SettingsValues} read in one query,
 * rather than one field at a time.
 */
public interface SettingsRepository extends SettingsReader {

    void delete(Class<?> category, String name);

    /**
     * Deletes all fields in a given settings category.
     *
     * @param category Category to delete.
     */
    void deleteAll(Class<?> category);

    /**
     * All the stored values of a category, indexed by name, in one query.
     */
    Map<String, String> getAllValues(Class<?> category);

    void setBoolean(Class<?> category, String name, boolean value);

    void setInt(Class<?> category, String name, int value);

    void setLong(Class<?> category, String name, long value);

    void setString(Class<?> category, String name, String value);

    void setPassword(Class<?> category, String name, String plain, boolean dontSaveIfBlank, Function<String, String> encryptService);

}
