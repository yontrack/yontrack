package net.nemerosa.ontrack.service;

import net.nemerosa.ontrack.extension.api.ExtensionManager;
import net.nemerosa.ontrack.model.events.BuildLinkListenerService;
import net.nemerosa.ontrack.model.events.EventFactory;
import net.nemerosa.ontrack.model.events.EventPostService;
import net.nemerosa.ontrack.model.metrics.MetricsExportService;
import net.nemerosa.ontrack.model.security.SecurityService;
import net.nemerosa.ontrack.model.settings.PredefinedPromotionLevelService;
import net.nemerosa.ontrack.model.settings.PredefinedValidationStampService;
import net.nemerosa.ontrack.model.structure.*;
import net.nemerosa.ontrack.repository.*;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static net.nemerosa.ontrack.model.structure.NameDescription.nd;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class StructureServiceImplTest {

    private StructureServiceImpl service;
    private StructureRepository structureRepository;
    private PromotionLevel copper;
    private Build build;

    @Before
    public void before() {
        SecurityService securityService = mock(SecurityService.class);
        ValidationRunStatusService validationRunStatusService = mock(ValidationRunStatusService.class);
        structureRepository = mock(StructureRepository.class);
        BranchRepository branchRepository = mock(BranchRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        EventPostService eventService = mock(EventPostService.class);
        EventFactory eventFactory = mock(EventFactory.class);
        ExtensionManager extensionManager = mock(ExtensionManager.class);
        PropertyService propertyService = mock(PropertyService.class);
        PredefinedPromotionLevelService predefinedPromotionLevelService = mock(PredefinedPromotionLevelService.class);
        PredefinedValidationStampService predefinedValidationStampService = mock(PredefinedValidationStampService.class);
        DecorationService decorationService = mock(DecorationService.class);
        ValidationDataTypeService validationDataTypeService = mock(ValidationDataTypeService.class);
        PromotionRunCheckService promotionRunCheckService = mock(PromotionRunCheckService.class);
        StatsRepository statsRepository = mock(StatsRepository.class);
        CoreBuildFilterRepository coreBuildFilterRepository = mock(CoreBuildFilterRepository.class);
        service = new StructureServiceImpl(
                securityService,
                eventService,
                eventFactory,
                validationRunStatusService,
                validationDataTypeService,
                structureRepository,
                projectRepository,
                branchRepository,
                mock(BuildLinkRepository.class),
                extensionManager,
                propertyService,
                predefinedPromotionLevelService,
                predefinedValidationStampService,
                promotionRunCheckService,
                statsRepository,
                mock(BuildLinkListenerService.class),
                coreBuildFilterRepository,
                mock(MetricsExportService.class),
                mock(PromotionRunRepository.class),
                mock(PromotionLevelRepository.class)
        );
        // Model
        Project project = Project.of(nd("P", "Project")).withId(ID.of(1));
        Branch branch = Branch.of(project, nd("B", "Branch")).withId(ID.of(1));
        copper = PromotionLevel.of(branch, nd("COPPER", "")).withId(ID.of(1));
        build = Build.of(branch, nd("1", "Build 1"), Signature.of("test")).withId(ID.of(1));
    }

    @Test
    public void newPromotionRun_with_date() throws Exception {
        PromotionRun promotionRunToCreate = PromotionRun.of(
                build,
                copper,
                Signature.of("test").withTime(LocalDateTime.of(2014, 9, 13, 18, 24)),
                ""
        );
        when(structureRepository.newPromotionRun(promotionRunToCreate)).thenReturn(promotionRunToCreate.withId(ID.of(1)));
        service.newPromotionRun(
                promotionRunToCreate
        );
        verify(structureRepository, times(1)).newPromotionRun(
                promotionRunToCreate
        );
    }

    @Test
    public void newPromotionRun_with_no_date() throws Exception {
        AtomicReference<PromotionRun> ref = new AtomicReference<>();
        when(structureRepository.newPromotionRun(any(PromotionRun.class))).then(
                invocation -> {
                    PromotionRun run = (PromotionRun) invocation.getArguments()[0];
                    ref.set(run);
                    return run;
                }
        );
        service.newPromotionRun(
                PromotionRun.of(
                        build,
                        copper,
                        Signature.of("test").withTime(null),
                        ""
                )
        );
        // Checks the signature's time
        assertNotNull(ref.get());
        assertNotNull(ref.get().getSignature().getTime());
    }
}