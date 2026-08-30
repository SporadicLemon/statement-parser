package io.github.sporadiclemon.statementparser

/**
 * Everything that can go wrong while parsing a statement, as a sealed hierarchy rather than a
 * grab-bag of [IllegalArgumentException]/[IllegalStateException] with only a message to go on.
 *
 * Each variant is still a real [Exception] - so it carries a stack trace, can be thrown, and
 * slots straight into a `Result<T>` via `Result.failure(error)` - but a caller can also `when`
 * over it exhaustively to decide what to tell their own user, without parsing English out of
 * [message]. [userMessage] is that English, deliberately written for someone who is not a
 * developer: no exception names, no internal terms like "column layout" or "fragment".
 */
sealed class StatementParseError(
    override val message: String,
    val userMessage: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** The file had no content at all. */
    class EmptyInput : StatementParseError(
        message = "Input was empty.",
        userMessage = "This file is empty.",
    )

    /** [io.github.sporadiclemon.statementparser.StatementParser.parse] was asked to handle a PDF. */
    object WrongParseFunctionForPdf : StatementParseError(
        message = "Use parsePdf(bytes) for PDF format.",
        userMessage = "This is a PDF file - it needs to be opened a different way.",
    )

    /**
     * No text could be pulled out of the PDF at all. The most common causes are a scanned image
     * with no text layer, a corrupted file, or a password-protected one - [cause] carries
     * whatever the underlying platform extractor reported, if anything.
     */
    class UnreadablePdf(cause: Throwable? = null) : StatementParseError(
        message = "No text extracted from PDF.",
        userMessage = "Couldn't read this PDF. It may be a scanned image, password-protected, or corrupted.",
        cause = cause,
    )

    /** No PDF bank profile's detection keyword matched the first page. */
    object UnrecognisedPdfBank : StatementParseError(
        message = "Unrecognised bank — no matching PDF profile found.",
        userMessage = "This bank's statement layout isn't supported yet.",
    )

    /** A bank was recognised from the first page, but its transaction table could not be found. */
    class PdfTableNotFound(val bank: Bank) : StatementParseError(
        message = "Recognised ${bank.displayName} but could not detect table columns in PDF.",
        userMessage = "This looks like a ${bank.displayName} statement, but its transaction table " +
            "couldn't be found on the page.",
    )

    /**
     * More than one CSV bank profile's header signature matched this file's headers. Rather than
     * silently picking the first and parsing with what might be the wrong bank's fixed column
     * positions - which fails safe most of the time, but can silently swap fields when it doesn't -
     * detection refuses to guess.
     */
    class AmbiguousCsvBank(val candidates: List<Bank>) : StatementParseError(
        message = "Multiple CSV bank profiles matched: ${candidates.joinToString { it.name }}.",
        userMessage = "This file's columns match more than one bank's format " +
            "(${candidates.joinToString { it.displayName }}). Please choose one explicitly.",
    )

    /**
     * Something else went wrong while parsing; [cause] carries the underlying exception. This is
     * the catch-all for failures that are not one of the specific, anticipated shapes above.
     */
    class Unexpected(cause: Throwable) : StatementParseError(
        message = cause.message ?: "An unexpected error occurred while parsing.",
        userMessage = "Something went wrong while reading this file.",
        cause = cause,
    )
}
