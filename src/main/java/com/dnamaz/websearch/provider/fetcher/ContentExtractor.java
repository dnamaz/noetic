package com.dnamaz.websearch.provider.fetcher;

import com.dnamaz.websearch.model.OutputFormat;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.List;

/**
 * Shared content extraction utilities for all fetchers.
 *
 * <p>Strips boilerplate (nav, ads, footers, cookie banners, social widgets,
 * comments, scripts) and extracts the main content from a Jsoup {@link Document},
 * returning it as clean HTML, plain text, or LLM-ready Markdown.</p>
 *
 * <p>Used by both {@link StaticContentFetcher} and {@link DynamicContentFetcher}
 * to ensure consistent output quality regardless of which fetcher is selected.</p>
 */
public final class ContentExtractor {

    private ContentExtractor() {}

    /**
     * CSS selectors for noise elements that should be stripped before extraction.
     * Covers navigation, ads, cookie banners, popups, social widgets,
     * related content, newsletter forms, comments, and scripts.
     */
    static final String NOISE_SELECTORS =
            "nav, header, footer, aside, .sidebar, .nav, .menu, "
            + ".advertisement, .ad, .ads, .adsbygoogle, [data-ad], [data-ad-slot], "
            + ".cookie-banner, .cookie-consent, .cookie-notice, #cookie-banner, "
            + ".popup, .modal, .overlay, "
            + ".social-share, .share-buttons, .social-links, "
            + ".related-posts, .recommended, .suggestions, "
            + ".newsletter-signup, .subscribe-form, .email-signup, "
            + ".comments, #comments, .disqus, "
            + "script, style, noscript, iframe[src*=ad], iframe[src*=doubleclick]";

    /**
     * CSS selectors for semantic main-content elements, tried in order.
     */
    private static final String MAIN_CONTENT_SELECTORS =
            "main, article, [role=main], .content, .post-content, #content";

    /**
     * Strips boilerplate noise from the document and extracts the main content
     * in the requested output format.
     *
     * <p>Mutates the document by removing noise elements.</p>
     *
     * @param doc    the Jsoup document (will be modified in place)
     * @param format desired output format
     * @return extracted content as a string
     */
    public static String extractMainContent(Document doc, OutputFormat format) {
        // Remove noise elements
        doc.select(NOISE_SELECTORS).remove();

        Element main = doc.selectFirst(MAIN_CONTENT_SELECTORS);
        Element source = main != null ? main : doc.body();

        if (source == null) {
            return doc.text();
        }

        return switch (format) {
            case HTML -> source.html();
            case TEXT -> source.text();
            case MARKDOWN -> convertToMarkdown(source);
        };
    }

    /**
     * Extracts all absolute link URLs from the document.
     *
     * @param doc the Jsoup document
     * @return deduplicated list of absolute href URLs
     */
    public static List<String> extractLinks(Document doc) {
        return doc.select("a[href]").stream()
                .map(e -> e.absUrl("href"))
                .filter(href -> !href.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Extracts all absolute image URLs from the document.
     *
     * @param doc the Jsoup document
     * @return deduplicated list of absolute image src URLs
     */
    public static List<String> extractImages(Document doc) {
        return doc.select("img[src]").stream()
                .map(e -> e.absUrl("src"))
                .filter(src -> !src.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Counts the number of whitespace-delimited words in the content.
     *
     * @param content the text content
     * @return word count, or 0 if content is null or blank
     */
    public static int countWords(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.split("\\s+").length;
    }

    /**
     * Converts an HTML element tree to Markdown.
     *
     * <p>Supports headings (h1-h6), paragraphs, ordered and unordered lists,
     * code blocks, blockquotes, horizontal rules, tables, inline links,
     * and inline formatting (bold, italic, code). Unknown elements fall back
     * to their text content.</p>
     *
     * @param element the root element to convert
     * @return Markdown string
     */
    static String convertToMarkdown(Element element) {
        var md = new StringBuilder();

        for (Element child : element.children()) {
            String tag = child.tagName();

            switch (tag) {
                case "h1" -> md.append("# ").append(child.text()).append("\n\n");
                case "h2" -> md.append("## ").append(child.text()).append("\n\n");
                case "h3" -> md.append("### ").append(child.text()).append("\n\n");
                case "h4" -> md.append("#### ").append(child.text()).append("\n\n");
                case "h5" -> md.append("##### ").append(child.text()).append("\n\n");
                case "h6" -> md.append("###### ").append(child.text()).append("\n\n");
                case "p" -> md.append(convertInlineElements(child)).append("\n\n");
                case "ul" -> {
                    for (Element li : child.select("> li")) {
                        md.append("- ").append(convertInlineElements(li)).append("\n");
                    }
                    md.append("\n");
                }
                case "ol" -> {
                    int i = 1;
                    for (Element li : child.select("> li")) {
                        md.append(i++).append(". ").append(convertInlineElements(li)).append("\n");
                    }
                    md.append("\n");
                }
                case "pre" -> {
                    // Try to detect language from nested <code> element
                    Element code = child.selectFirst("code");
                    String lang = "";
                    if (code != null) {
                        String cls = code.className();
                        if (cls.startsWith("language-")) {
                            lang = cls.substring("language-".length());
                        } else if (cls.startsWith("lang-")) {
                            lang = cls.substring("lang-".length());
                        }
                    }
                    md.append("```").append(lang).append("\n")
                      .append(child.text()).append("\n```\n\n");
                }
                case "blockquote" -> md.append("> ").append(child.text()).append("\n\n");
                case "hr" -> md.append("---\n\n");
                case "table" -> md.append(convertTable(child)).append("\n\n");
                case "div", "section" -> {
                    // Recurse into container elements
                    String nested = convertToMarkdown(child);
                    if (!nested.isBlank()) {
                        md.append(nested).append("\n\n");
                    }
                }
                case "dl" -> {
                    for (Element dt : child.select("> dt")) {
                        md.append("**").append(dt.text()).append("**\n");
                    }
                    for (Element dd : child.select("> dd")) {
                        md.append(": ").append(dd.text()).append("\n");
                    }
                    md.append("\n");
                }
                default -> {
                    String text = child.text().trim();
                    if (!text.isEmpty()) {
                        md.append(text).append("\n\n");
                    }
                }
            }
        }

        return md.toString().trim();
    }

    /**
     * Converts inline elements (links, bold, italic, code) within a block element.
     */
    private static String convertInlineElements(Element element) {
        var sb = new StringBuilder();
        for (var node : element.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode textNode) {
                sb.append(textNode.text());
            } else if (node instanceof Element el) {
                switch (el.tagName()) {
                    case "a" -> {
                        String href = el.absUrl("href");
                        if (!href.isBlank()) {
                            sb.append("[").append(el.text()).append("](").append(href).append(")");
                        } else {
                            sb.append(el.text());
                        }
                    }
                    case "strong", "b" -> sb.append("**").append(el.text()).append("**");
                    case "em", "i" -> sb.append("*").append(el.text()).append("*");
                    case "code" -> sb.append("`").append(el.text()).append("`");
                    case "br" -> sb.append("\n");
                    default -> sb.append(el.text());
                }
            }
        }
        return sb.toString();
    }

    /**
     * Converts an HTML table to a Markdown table.
     */
    private static String convertTable(Element table) {
        var sb = new StringBuilder();

        // Header row
        var headerCells = table.select("thead th");
        if (headerCells.isEmpty()) {
            headerCells = table.select("tr:first-child th");
        }
        if (!headerCells.isEmpty()) {
            sb.append("| ");
            for (Element th : headerCells) {
                sb.append(th.text()).append(" | ");
            }
            sb.append("\n|");
            for (int i = 0; i < headerCells.size(); i++) {
                sb.append(" --- |");
            }
            sb.append("\n");
        }

        // Body rows
        Elements bodyRows = table.select("tbody tr");
        List<Element> rows;
        if (!bodyRows.isEmpty()) {
            rows = bodyRows;
        } else {
            // No explicit tbody -- get all tr, skip header row if we rendered one
            Elements allRows = table.select("tr");
            if (!headerCells.isEmpty() && allRows.size() > 1) {
                rows = allRows.subList(1, allRows.size());
            } else {
                rows = allRows;
            }
        }
        for (Element row : rows) {
            sb.append("| ");
            for (Element td : row.select("td, th")) {
                sb.append(td.text()).append(" | ");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }
}
