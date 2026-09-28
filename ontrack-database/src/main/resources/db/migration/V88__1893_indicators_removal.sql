-- 88. Removal of the indicators (#1893)

-- Indicator values
DELETE
FROM ENTITY_DATA_STORE
WHERE CATEGORY = 'net.nemerosa.ontrack.extension.indicators.model.Indicator';

-- Indicator categories, types, views, portfolios, configurable indicators
-- and the Jenkins pipeline libraries indicator settings
DELETE
FROM STORAGE
WHERE STORE IN (
                'net.nemerosa.ontrack.extension.indicators.model.IndicatorCategory',
                'net.nemerosa.ontrack.extension.indicators.model.IndicatorType',
                'net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorView',
                'net.nemerosa.ontrack.extension.indicators.portfolio.IndicatorPortfolio',
                'net.nemerosa.ontrack.extension.indicators.computing.ConfigurableIndicatorState',
                'net.nemerosa.ontrack.extension.jenkins.indicator.JenkinsPipelineLibraryIndicatorSettings'
    );

-- Grants of the indicator roles
DELETE
FROM GLOBAL_AUTHORIZATIONS
WHERE ROLE = 'GLOBAL_INDICATOR_MANAGER';

DELETE
FROM GROUP_GLOBAL_AUTHORIZATIONS
WHERE ROLE = 'GLOBAL_INDICATOR_MANAGER';

DELETE
FROM PROJECT_AUTHORIZATIONS
WHERE ROLE = 'PROJECT_INDICATOR_MANAGER';

DELETE
FROM GROUP_PROJECT_AUTHORIZATIONS
WHERE ROLE = 'PROJECT_INDICATOR_MANAGER';
