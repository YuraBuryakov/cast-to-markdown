/**
 * CastToMarkdown: converts documents into LLM/RAG-friendly Markdown.
 * Only {@code io.github.yuraburyakov.casttomarkdown} is API; {@code internal} packages are not exported.
 */
module io.github.yuraburyakov.casttomarkdown {
    requires org.apache.pdfbox;

    exports io.github.yuraburyakov.casttomarkdown;
}
