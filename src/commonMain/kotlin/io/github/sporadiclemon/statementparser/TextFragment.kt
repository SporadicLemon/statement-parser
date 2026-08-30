package io.github.sporadiclemon.statementparser

/**
 * One word of text lifted from a PDF, with where it sits on the page.
 *
 * @property text the word itself, with no surrounding whitespace.
 * @property x the left edge of the word, in points from the left of the page.
 * @property y the baseline of the word, in points from the top of the page.
 * @property page zero-based page index.
 * @property width the width of the word in points. Zero when the extractor could not measure it,
 *   in which case [right] degrades to [x]. Statement tables right-align their amount columns, so
 *   the right edge is what identifies which column a figure belongs to.
 */
data class TextFragment(
    val text: String,
    val x: Float,
    val y: Float,
    val page: Int,
    val width: Float = 0f,
) {
    /** The right edge of the word. */
    val right: Float get() = x + width
}
