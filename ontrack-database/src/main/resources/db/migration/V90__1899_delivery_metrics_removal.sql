-- 90. Removal of the delivery metrics (#1899)

-- Settings of the export of the end-to-end promotion metrics
DELETE
FROM SETTINGS
WHERE CATEGORY = 'net.nemerosa.ontrack.extension.dm.export.EndToEndPromotionMetricsExportSettings';
