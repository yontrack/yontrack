package net.nemerosa.ontrack.service;

import net.nemerosa.ontrack.model.buildfilter.BuildFilterProviderData;
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService;
import net.nemerosa.ontrack.model.security.SecurityService;
import net.nemerosa.ontrack.model.structure.BuildDisplayNameService;
import net.nemerosa.ontrack.model.structure.PropertyService;
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData;
import net.nemerosa.ontrack.model.structure.StructureService;
import net.nemerosa.ontrack.repository.BuildFilterRepository;
import net.nemerosa.ontrack.repository.CoreBuildFilterRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class BuildFilterServiceImplTest {

    private BuildFilterService service;

    @Before
    public void before() {
        BuildFilterRepository buildFilterRepository = mock(BuildFilterRepository.class);
        StructureService structureService = mock(StructureService.class);
        SecurityService securityService = mock(SecurityService.class);
        service = new BuildFilterServiceImpl(
                Collections.singletonList(
                        new StandardBuildFilterProvider(
                                structureService,
                                mock(PropertyService.class),
                                mock(CoreBuildFilterRepository.class),
                                mock(BuildDisplayNameService.class)
                        )
                ),
                buildFilterRepository,
                structureService,
                securityService
        );
    }

    @Test
    public void standardFilter() {
        BuildFilterProviderData<?> providerData = service.standardFilterProviderData(20)
                .withWithPromotionLevel("BRONZE")
                .withWithProperty("my.property.MyPropertyType")
                .withWithPropertyValue("Value")
                .build();
        Object filter = providerData.getData();
        assertTrue(filter instanceof StandardBuildFilterData);
        StandardBuildFilterData std = (StandardBuildFilterData) filter;
        assertEquals(20, std.getCount());
        assertEquals("BRONZE", std.getWithPromotionLevel());
        assertEquals("my.property.MyPropertyType", std.getWithProperty());
        assertEquals("Value", std.getWithPropertyValue());
    }

}
