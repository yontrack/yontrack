package net.nemerosa.ontrack.model.settings;

/**
 * Loads a type of settings from the database.
 * <p>
 * The settings are loaded in one query, from {@code settingsRepository.values(settingsClass)}:
 * they are read once per unit of work, through the {@link CachedSettingsService}, and fresh in a
 * job.
 *
 * @param <T> Type of the settings
 */
public interface SettingsProvider<T> {

    T getSettings();

    Class<T> getSettingsClass();

}
