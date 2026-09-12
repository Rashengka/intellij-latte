package dev.noctud.latte.editor;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.JTree;

/**
 * The structure of a template lists its tags however deep the HTML around them is. A block whose
 * content sits in a {@code <div>} used to show as empty, because only the block's direct children
 * were looked at and a tag inside an element is not one of them.
 */
public class StructureViewTest extends BasePlatformTestCase {

    public void testTagsDirectlyInABlockAreListed() {
        assertStructure("{block content}\n{if true}x{/if}\n{/block}\n",
            "-structure.latte\n -block content\n  if true\n");
    }

    public void testTagsInsideHtmlElementsAreListed() {
        assertStructure("{block content}\n<div>\n  {if true}x{/if}\n</div>\n{/block}\n{define helper}x{/define}\n",
            "-structure.latte\n -block content\n  if true\n define helper\n");
    }

    public void testHtmlWithoutTagsAddsNothing() {
        assertStructure("<div><p>text</p></div>\n{define helper}x{/define}\n",
            "-structure.latte\n define helper\n");
    }

    private void assertStructure(String template, String expected) {
        myFixture.configureByText("structure.latte", template);
        myFixture.testStructureView(component -> {
            JTree tree = component.getTree();
            PlatformTestUtil.expandAll(tree);
            assertEquals(expected, PlatformTestUtil.print(tree, true) + "\n");
        });
    }
}
