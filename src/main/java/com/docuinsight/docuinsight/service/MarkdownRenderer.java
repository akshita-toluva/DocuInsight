package com.docuinsight.docuinsight.service;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns the AI's Markdown into a tree (for Word) or into HTML (for PDF).
 * Parser and HtmlRenderer are thread-safe, so one instance serves all requests.
 */
@Component
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;

    public MarkdownRenderer() {
        List<Extension> extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder()
                .extensions(extensions)
                .build();
        this.htmlRenderer = HtmlRenderer.builder()
                .extensions(extensions)
                .escapeHtml(true)     // raw HTML in AI output is printed as text, never interpreted
                .sanitizeUrls(true)   // drops javascript: links
                .build();
    }

    /** Markdown -> tree of nodes (Heading, Paragraph, BulletList, StrongEmphasis, ...) */
    public Node parse(String markdown) {
        return parser.parse(clean(markdown));
    }

    /** Markdown -> HTML fragment (no <html>/<body>) */
    public String toHtml(String markdown) {
        return htmlRenderer.render(parse(markdown));
    }

    // Control characters (except tab/newline) are illegal in XML and would crash the PDF parser
    private String clean(String markdown) {
        if (markdown == null) return "";
        return markdown.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}