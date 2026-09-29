package net.nemerosa.ontrack.extension.github.ingestion.config.parser

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.extension.github.ingestion.config.model.IngestionConfig.Companion.V2_VERSION

class ConfigVersionException(version: String) : BaseException(
    "Unsupported version for the ingestion configuration: $version. Use version $V2_VERSION instead."
)