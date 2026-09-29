-- 92. Removal of the pure-Git support (#1924)

-- Git configuration property of the projects
DELETE
FROM PROPERTIES
WHERE TYPE = 'net.nemerosa.ontrack.extension.git.property.GitProjectConfigurationPropertyType';

-- Git configurations, which only this property used
DELETE
FROM CONFIGURATIONS
WHERE TYPE = 'net.nemerosa.ontrack.extension.git.model.BasicGitConfiguration';
