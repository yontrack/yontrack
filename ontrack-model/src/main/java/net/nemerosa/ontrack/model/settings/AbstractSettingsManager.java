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
        securityService.checkGlobalFunction(GlobalSettings.class);
        return cachedSettingsService.getCachedSettings(settingsClass);
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
