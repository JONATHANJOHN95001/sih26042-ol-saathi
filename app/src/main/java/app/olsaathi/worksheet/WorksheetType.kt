package app.olsaathi.worksheet

/**
 * The four worksheet types, each aligned to a published NIPUN Bharat outcome.
 *
 * These codes and outcomes come from the CBSE Class 1 ECL (Early Childhood 
 * Literacy) framework. The mapping is explicit so the sheet can print both 
 * the code and the verbatim outcome sentence, which together are checkable.
 */
enum class WorksheetType(
    val displayName: String,
    val hindiSubtitle: String,
    val nipunCode: String
) {
    TRACE_AND_CONNECT(
        displayName = "Trace & Connect",
        hindiSubtitle = "चित्र-वाक्य मिलान",
        nipunCode = "ECL2 4.5"
    ),
    WORD_FLASH_STRIPS(
        displayName = "Word Flash Strips",
        hindiSubtitle = "कट-आउट शब्द पट्टियां",
        nipunCode = "ECL1 4.8"
    ),
    SCRIPT_TRACING(
        displayName = "Script Tracing",
        hindiSubtitle = "अक्षर अभ्यास",
        nipunCode = "ECL2 4.5"
    ),
    CLASSROOM_DIALOGUES(
        displayName = "Classroom Dialogues",
        hindiSubtitle = "संवाद अभ्यास",
        nipunCode = "ECL2 4.1a"
    )
}