package com.cursoragent.acp;

import com.cursoragent.parser.ToolCallPayloadParser;
import com.cursoragent.service.AgentEvent;
import com.cursoragent.service.AgentTool;
import com.cursoragent.service.AgentToolContent;
import com.cursoragent.ui.timeline.MarkdownRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Offline characterization, not a Cursor live fixture or a desired product acceptance test. */
class Issue290ContractCheck {
    private JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private AgentTool update(AcpProtocol protocol, String fields) {
        return ((AgentEvent.Tool) protocol.update(json("{\"sessionUpdate\":\"tool_call_update\",\"toolCallId\":\"synthetic-290\"," + fields + "}"))).getState();
    }

    private static final String[] BLOCKS = {
        "{\"type\":\"text\",\"text\":\"<script>synthetic</script>\"}",
        "{\"type\":\"image\",\"mimeType\":\"image/png\",\"data\":\"AA==\"}",
        "{\"type\":\"audio\",\"mimeType\":\"audio/wav\",\"data\":\"AA==\"}",
        "{\"type\":\"resource_link\",\"uri\":\"https://example.invalid/result\",\"name\":\"result\"}",
        "{\"type\":\"resource\",\"resource\":{\"uri\":\"synthetic://290/text\",\"text\":\"result\"}}",
        "{\"type\":\"resource\",\"resource\":{\"uri\":\"synthetic://290/blob\",\"blob\":\"AA==\"}}",
        "{\"type\":\"future-290\",\"data\":\"unknown\"}"
    };

    @Test void toolContentTypesAndReplacement() {
        var protocol = new AcpProtocol();

        for (int i = 0; i < BLOCKS.length; i++) {
            var tool = update(protocol, "\"status\":\"completed\",\"content\":[{\"type\":\"content\",\"content\":" + BLOCKS[i] + "}]");
            assertEquals("completed", tool.getStatus());
            assertEquals(1, tool.getContent().size());
            assertEquals(i == 0, tool.getContent().get(0) instanceof AgentToolContent.Text);
            assertEquals(i != 0, tool.getContent().get(0) instanceof AgentToolContent.Unsupported);
            assertEquals(tool.getContent(), update(protocol, "\"title\":\"later\"").getContent());
        }
        assertTrue(update(protocol, "\"content\":[]").getContent().isEmpty());
        assertTrue(update(protocol, "\"rawOutput\":{\"structuredContent\":{\"value\":290}}").getContent().isEmpty());
        assertInstanceOf(AgentToolContent.Unsupported.class, update(protocol, "\"content\":[{\"type\":\"terminal\",\"terminalId\":\"synthetic-terminal\"}]").getContent().get(0));
        assertInstanceOf(AgentToolContent.Diff.class, update(protocol, "\"content\":[{\"type\":\"diff\",\"path\":\"/synthetic-290.txt\",\"oldText\":\"a\",\"newText\":\"b\"}]").getContent().get(0));
        assertFalse(protocol.getHasUnfinishedTools());
        update(protocol, "\"status\":\"future-status\"");
        assertTrue(protocol.getHasUnfinishedTools());
        update(protocol, "\"status\":\"failed\"");
        assertFalse(protocol.getHasUnfinishedTools());
    }

    @Test void assistantNonTextAndMalformedToolAreDifferent() {
        var protocol = new AcpProtocol();
        for (int i = 1; i < BLOCKS.length; i++) {
            assertNull(protocol.update(json("{\"sessionUpdate\":\"agent_message_chunk\",\"content\":" + BLOCKS[i] + "}")));
        }
        assertThrows(RuntimeException.class, () -> update(protocol, "\"content\":[null]"));
    }

    @Test void printUnknownPayloadIsOnlySummary() {
        var value = ToolCallPayloadParser.INSTANCE.parse(json("{\"type\":\"tool_call\",\"subtype\":\"completed\",\"call_id\":\"synthetic-290\",\"tool_call\":{\"syntheticRichToolCall\":{\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"result\"}],\"isError\":true}}}}"));
        assertNotNull(value);
        assertEquals("syntheticRich tool", value.getSummary());
        assertNull(value.getFileEdit());
        assertNull(value.getShellResult());
        assertNull(ToolCallPayloadParser.INSTANCE.parse(json("{\"type\":\"tool_call\",\"subtype\":\"completed\",\"call_id\":\"synthetic-290\",\"tool_call\":{\"function\":{\"name\":\"synthetic\",\"arguments\":{}}}}")));
    }

    @Test void literalHtmlEscapeDoesNotDisableMarkdownImages() {
        String literal = MarkdownRenderer.INSTANCE.toHtmlFragment("<img src=\"https://example.invalid/pixel.png\">");
        assertFalse(literal.contains("<img"));
        String markdown = MarkdownRenderer.INSTANCE.toHtmlFragment("![synthetic](https://example.invalid/pixel.png)");
        assertTrue(markdown.contains("<img"));
        assertTrue(markdown.contains("https://example.invalid/pixel.png"));
        // Only render a string: no Swing component, URI dereference, Browser, or network access.
    }
}
