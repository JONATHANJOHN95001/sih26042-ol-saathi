// Ol Chiki -> Devanagari, for the Postman "Scripts" tab.
//
// Why: Bhashini's hi->sat translation (ai4bharat/indictrans-v2-all-gpu--t4,
// MeitY pipeline 64392f96daac500b55c543cd) answers in Ol Chiki, while the
// Santali voice (Bhashini/IITM/TTS, IIT Madras pipeline 660fa5bec7fb5b0328229016)
// returns 0.02 s of silence for Ol Chiki and speaks Devanagari. Bhashini's own
// sat->hi transliterator (ai4bharat/indicxlit--cpu-fsv2) left Ol Chiki letters
// in its output when tested on 10 Sep 2026, so this does the conversion
// deterministically instead. Same table as tools/olchiki_bridge.py, read off
// the Unicode letter names. It is a respelling, not a translation, and a
// Santali speaker may prefer some vowels written differently.
//
// Paste into the Post-response script of the NMT (or ASR+NMT) compute request.
// It reads the Santali text from the response and stores the Devanagari form
// in the collection variable `sat_deva`, which the TTS request body uses.

const VOWELS = {
  'ᱚ': ['ओ', 'ो'], // LA   o
  'ᱟ': ['आ', 'ा'], // LAA  aa
  'ᱤ': ['इ', 'ि'], // LI   i
  'ᱩ': ['उ', 'ु'], // LU   u
  'ᱮ': ['ए', 'े'], // LE   e
  'ᱳ': ['ओ', 'ो'], // LO   o
};
const CONSONANTS = {
  'ᱛ': 'त', 'ᱜ': 'ग', 'ᱝ': 'ङ', 'ᱞ': 'ल',
  'ᱠ': 'क', 'ᱡ': 'ज', 'ᱢ': 'म', 'ᱣ': 'व',
  'ᱥ': 'स', 'ᱦ': 'ह', 'ᱧ': 'ञ', 'ᱨ': 'र',
  'ᱪ': 'च', 'ᱫ': 'द', 'ᱬ': 'ण', 'ᱭ': 'य',
  'ᱯ': 'प', 'ᱰ': 'ड', 'ᱱ': 'न', 'ᱲ': 'ड़',
  'ᱴ': 'ट', 'ᱵ': 'ब', 'ᱶ': 'व', 'ᱷ': 'ह',
};
const MARKS = {
  'ᱸ': 'ं', 'ᱹ': '', 'ᱺ': 'ं', 'ᱻ': '',
  'ᱼ': '', 'ᱽ': '', '᱾': '।', '᱿': '॥',
};
const VIRAMA = '्';

function olChikiToDevanagari(text) {
  const out = [];
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    const nxt = text[i + 1] || '';
    if (CONSONANTS[ch]) {
      out.push(CONSONANTS[ch]);
      if (VOWELS[nxt]) { out.push(VOWELS[nxt][1]); i++; }
      else if (CONSONANTS[nxt]) out.push(VIRAMA);
    } else if (VOWELS[ch]) out.push(VOWELS[ch][0]);
    else if (ch in MARKS) out.push(MARKS[ch]);
    else if (ch >= '᱐' && ch <= '᱙') out.push(String.fromCharCode(0x0966 + ch.charCodeAt(0) - 0x1C50));
    else out.push(ch);
  }
  return out.join('');
}

// Find the translation task in the compute response, whatever its position.
const res = pm.response.json();
const nmt = (res.pipelineResponse || []).find((t) => t.taskType === 'translation');
const olck = nmt && nmt.output && nmt.output[0] && nmt.output[0].target;
if (olck) {
  const deva = olChikiToDevanagari(olck);
  pm.collectionVariables.set('sat_olck', olck);
  pm.collectionVariables.set('sat_deva', deva);
  console.log('Santali (Ol Chiki):', olck);
  console.log('Santali (Devanagari, for TTS):', deva);
} else {
  console.log('No translation output found in this response.');
}
