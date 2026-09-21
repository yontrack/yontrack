package net.nemerosa.ontrack.model.support.tree;

import net.nemerosa.ontrack.model.support.tree.support.Markup;
import net.nemerosa.ontrack.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static java.util.Arrays.asList;
import static net.nemerosa.ontrack.model.support.tree.support.Markup.of;

public class NodeTest {

    @Test
    public void json() throws IOException {
        NodeFactory<Markup> factory = new DefaultNodeFactory<>();
        Node<Markup> root = factory.node(
                null,
                asList(
                        factory.node(
                                of("link").attr("url", "http://test/id/177"),
                                asList(
                                        factory.leaf(Markup.text("#")),
                                        factory.node(
                                                of("em"),
                                                asList(
                                                        factory.leaf(Markup.text("177"))
                                                )
                                        )
                                )
                        ),
                        factory.leaf(Markup.text(" One match"))
                )
        );

        TestUtils.assertJsonWrite(
                TestUtils.resourceJson("/node.json"),
                root
        );
    }

}
