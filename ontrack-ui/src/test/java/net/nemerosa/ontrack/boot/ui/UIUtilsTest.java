package net.nemerosa.ontrack.boot.ui;

import net.nemerosa.ontrack.common.Document;
import net.nemerosa.ontrack.ui.support.UIUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UIUtilsTest {

    @Test
    public void imageCacheControl() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        UIUtils.setupDefaultImageCache(response, new Document("image/png", new byte[10])); // Content does not matter
        String header = response.getHeader("Cache-Control");
        assertEquals("max-age=86400, public", header);
    }

    @Test
    public void imageConfigurableCacheControl() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        UIUtils.setupImageCache(response, new Document("image/png", new byte[10]), 2); // Content does not matter
        String header = response.getHeader("Cache-Control");
        assertEquals("max-age=172800, public", header);
    }

    @Test
    public void empyImageNoCacheControl() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        UIUtils.setupDefaultImageCache(response, Document.EMPTY);
        String header = response.getHeader("Cache-Control");
        assertNull(header);
    }

}
