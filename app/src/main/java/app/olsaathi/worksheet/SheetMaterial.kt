package app.olsaathi.worksheet

import android.content.Context
import app.olsaathi.content.Provenance
import app.olsaathi.content.VerifiedContentPack
import app.olsaathi.pdf.ImportedLine
import app.olsaathi.pdf.LessonPdfImporter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The material a worksheet or flashcard sheet is generated from: one lesson,
 * the phrase deck, or a chapter the teacher imported (for example from an
 * NCERT textbook PDF). Always lines the teacher selected, never invented.
 */
data class SheetMaterial(
    /** Stable id, used in file names and to remember the selection. */
    val key: String,
    val title: String,
    val languageEnglish: String,
    val lines: List<SheetLine>,
) {
    companion object {

        /** A pack lesson, or the phrase deck when [lessonId] is empty. */
        fun fromPack(pack: VerifiedContentPack, lessonId: String): SheetMaterial {
            val entries = if (lessonId.isEmpty()) pack.entries().filter { it.kind == "phrase" }
            else pack.entries().filter { it.lesson == lessonId && (it.kind == "lesson" || it.kind == "check") }
            val lines = entries.filter { it.target.isNotBlank() }.sortedBy { it.id }.map { e ->
                val t = pack.lookup(e.source)
                SheetLine(
                    id = e.id, hindi = e.source, target = e.target, en = e.en, bridge = t.bridge,
                    kind = e.kind, nipun = e.nipun, nipunOutcome = e.nipunOutcome,
                    nipunDomain = e.nipunDomain, image = e.image,
                    provenance = t.provenance, serviceName = t.serviceName,
                )
            }
            val title = if (lessonId.isEmpty()) "Classroom phrases"
            else lessonId.replace("-", " ").replaceFirstChar { it.uppercase() }
            return SheetMaterial(lessonId.ifEmpty { "phrases" }, title, pack.languageEnglish, lines)
        }

        /** Lines read from a teacher's PDF; only the translated ones. */
        fun fromImport(title: String, imported: List<ImportedLine>, pack: VerifiedContentPack): SheetMaterial {
            val lines = imported.filter { !it.translation.isNullOrBlank() }.map { l ->
                SheetLine(
                    id = "imp-%03d".format(l.index), hindi = l.hindi, target = l.translation!!,
                    // A chapter's own questions ("मीना के भाई का नाम क्या है?")
                    // become open questions on the sheet, not flashcards.
                    kind = if (l.hindi.trimEnd().endsWith("?")) "check" else "imported",
                    // A teacher's chapter carries no NIPUN tags of its own; the
                    // sheet type's code is added by the NIPUN box.
                    provenance = when {
                        l.live -> Provenance.ONLINE_MACHINE
                        l.onDevice -> Provenance.ON_DEVICE_MACHINE
                        else -> Provenance.VERIFIED
                    },
                    serviceName = when {
                        l.live -> "Bhashini"
                        l.onDevice -> "IndicTrans2"
                        else -> pack.serviceName
                    },
                )
            }
            return SheetMaterial("imported-" + slug(title), title, pack.languageEnglish, lines)
        }

        // ── Imported chapters kept on the tablet ─────────────────────────

        /** The Materials key a chapter saved under [title] is listed with. */
        fun importedKey(title: String) = "imported-" + slug(title)

        private fun dir(context: Context, languageCode: String) =
            File(context.filesDir, "imported/$languageCode").apply { mkdirs() }

        /**
         * Keep an imported chapter so the Materials screen can generate from it
         * later. Stored per language, because the translations are.
         */
        fun saveImported(context: Context, languageCode: String, title: String, lines: List<ImportedLine>) {
            val arr = JSONArray()
            lines.filter { !it.translation.isNullOrBlank() }.forEach {
                arr.put(JSONObject().put("i", it.index).put("hi", it.hindi)
                    .put("t", it.translation).put("live", it.live).put("dev", it.onDevice))
            }
            if (arr.length() == 0) return
            val json = JSONObject().put("title", title).put("lines", arr)
            File(dir(context, languageCode), slug(title) + ".json").writeText(json.toString(), Charsets.UTF_8)
        }

        /** Imported chapters for this language: key to title, newest first. */
        fun listImported(context: Context, languageCode: String): List<Pair<String, String>> =
            (dir(context, languageCode).listFiles() ?: emptyArray())
                .filter { it.extension == "json" }
                .sortedByDescending { it.lastModified() }
                .mapNotNull { f ->
                    runCatching {
                        val json = JSONObject(f.readText(Charsets.UTF_8))
                        val arr = json.getJSONArray("lines")
                        // A chapter saved before the Hindi check may hold no Hindi
                        // at all; it has nothing to make questions from.
                        val anyHindi = (0 until arr.length()).any {
                            LessonPdfImporter.isDevanagari(arr.getJSONObject(it).getString("hi"))
                        }
                        if (anyHindi) json.getString("title") else null
                    }.getOrNull()?.let { ("imported-" + f.nameWithoutExtension) to it }
                }

        fun loadImported(context: Context, key: String, pack: VerifiedContentPack): SheetMaterial? {
            val f = File(dir(context, pack.languageCode), key.removePrefix("imported-") + ".json")
            if (!f.isFile) return null
            return runCatching {
                val json = JSONObject(f.readText(Charsets.UTF_8))
                val arr = json.getJSONArray("lines")
                val lines = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ImportedLine(o.getInt("i"), o.getString("hi"), o.getString("t"), null,
                        o.optBoolean("live"), o.optBoolean("dev"))
                }.filter { LessonPdfImporter.isDevanagari(it.hindi) }
                if (lines.isEmpty()) return null
                fromImport(json.getString("title"), lines, pack)
            }.getOrNull()
        }

        private fun slug(s: String) =
            s.lowercase().replace(Regex("[^a-z0-9\\u0900-\\u097f]+"), "-").trim('-').take(40).ifEmpty { "chapter" }
    }
}
