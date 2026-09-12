package app.olsaathi.mt

import java.io.File

/**
 * Reads IndicTrans2's `dict.SRC.json` / `dict.TGT.json`: one flat object of
 * piece to id, about 122,000 entries.
 *
 * A hand-rolled reader because the file is too big to load comfortably as an
 * org.json tree on a low-end tablet, and android.util.JsonReader does not run
 * in the JVM unit tests that check the tokenizer.
 */
object FlatJsonVocab {

    fun load(file: File): HashMap<String, Int> = parse(file.readText(Charsets.UTF_8))

    fun parse(json: String): HashMap<String, Int> {
        val out = HashMap<String, Int>(200_000)
        var i = json.indexOf('{') + 1
        val key = StringBuilder()
        while (i < json.length) {
            // Next key.
            while (i < json.length && json[i] != '"' && json[i] != '}') i++
            if (i >= json.length || json[i] == '}') break
            i++
            key.setLength(0)
            while (json[i] != '"') {
                val c = json[i]
                if (c == '\\') {
                    val e = json[i + 1]
                    when (e) {
                        'u' -> {
                            key.append(json.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 6
                            continue
                        }
                        'n' -> key.append('\n')
                        't' -> key.append('\t')
                        'r' -> key.append('\r')
                        'b' -> key.append('\b')
                        'f' -> key.append('\u000C')
                        else -> key.append(e)
                    }
                    i += 2
                } else {
                    key.append(c)
                    i++
                }
            }
            i++
            // Value: an integer after the colon.
            while (json[i] != '-' && !json[i].isDigit()) i++
            val start = i
            i++
            while (i < json.length && json[i].isDigit()) i++
            out[key.toString()] = json.substring(start, i).toInt()
        }
        return out
    }
}
