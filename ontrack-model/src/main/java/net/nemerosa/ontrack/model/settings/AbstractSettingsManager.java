package net.nemerosa.ontrack.model.settings;

import net.nemerosa.ontrack.model.security.GlobalSettings;
import net.nemerosa.ontrack.model.security.SecurityService;

public abstract class AbstractSettingsManager<T> implements SettingsManager<T> {

    private final Class<T> settingsClass;
    private final CachedSettingsService cachedSettingsService;
    private final SecurityService securityService;

    protected AbstractSettingsManager(Class<T> settingsClass, CachedSettingsService cachedSettingsService, SecurityService securityService) {
        this.settingsClass = settingsClass;
        this.cachedSettingsService = cachedSettingsService;
        this.securityService = securityService;
    }

    @Override
    public final T getSettings() {
        checkRead();
        return cachedSettingsService.getCachedSettings(settingsClass);
    }

    /**
     * Checks that the current user may read these settings: only the administrators may.
     * <p>
     * A settings manager overrides it only for settings which decide no permission and hold
     * nothing secret, to open their reading to more users. Saving the settings always requires
     * the {@link GlobalSettings} function.
     */
    protected void checkRead() {
        securityService.checkGlobalFunction(GlobalSettings.class);
    }

    @Override
    public final void saveSettings(T settings) {
        securityService.checkGlobalFunction(GlobalSettings.class);
        cachedSettingsService.invalidate(settingsClass);
        doSaveSettings(settings);
        // Outside of a transaction, a read by another thread during the writing would have put
        // the previous settings back into the cache
        cachedSettingsService.invalidate(settingsClass);
    }

    protected abstract void doSaveSettings(T settings);

    @Override
    public final Class<T> getSettingsClass() {
        return settingsClass;
    }
}
