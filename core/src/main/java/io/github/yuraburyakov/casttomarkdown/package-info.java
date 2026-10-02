/**
 * CastToMarkdown: converts documents into LLM/RAG-friendly Markdown.
 *
 * <p>Entry point: {@link io.github.yuraburyakov.casttomarkdown.CastToMarkdown}.
 *
 * <p>This is the only API package. Formats are separate artifacts ({@code cast-to-markdown-pdf}, ...):
 * add the ones you need, they are found automatically. Public types must not expose parser types
 * (PDFBox, POI, jsoup).
 */
package io.github.yuraburyakov.casttomarkdown;
