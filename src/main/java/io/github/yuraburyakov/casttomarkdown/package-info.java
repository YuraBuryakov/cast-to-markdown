/**
 * CastToMarkdown: converts documents into LLM/RAG-friendly Markdown.
 *
 * <p>Entry point: {@link io.github.yuraburyakov.casttomarkdown.CastToMarkdown}.
 *
 * <p>This is the only API package. Format converters live in {@code internal.*} packages, which are
 * not exported by the module and may change at any time. Public types must not expose parser types
 * (PDFBox, POI, jsoup).
 */
package io.github.yuraburyakov.casttomarkdown;
