#!/usr/bin/env node
/**
 * NipunCurriculumSynthesizer
 * ──────────────────────────
 * Autonomous data-processing agent for NIPUN Bharat FLN curriculum.
 *
 * Ingests official NIPUN Bharat L1–L3 learning outcomes,
 * generates Hindi lesson scripts / activity instructions / assessment prompts,
 * translates to Santhali (Ol Chiki), Ho (Warang Citi), and Mundari (Nag Mundari),
 * and outputs a Room DB-compatible JSON array.
 *
 * Modes
 *   GEMINI_API_KEY set   → online: Gemini 2.0 Flash for generation + translation
 *   GEMINI_API_KEY unset → offline: deterministic templates + transliteration
 *
 * Usage:  node nipun_curriculum_synthesizer.mjs
 */

import { writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

const GEMINI_API_KEY = process.env.GEMINI_API_KEY || '';
const USE_GEMINI = GEMINI_API_KEY.length > 0;
const OUTPUT_PATH = join(
  __dirname, 'app', 'src', 'main', 'assets', 'database',
  'nipun_curriculum_prepopulated.json'
);
const GEMINI_MODEL = 'gemini-2.0-flash';
const GEMINI_RPM_DELAY = 4500;

// ═══════════════════════════════════════════════════════════════
//  1. NIPUN BHARAT LEARNING OUTCOMES  (18 outcomes: 9 FL + 9 FN)
// ═══════════════════════════════════════════════════════════════

const OUTCOMES = [
  // ── L1  Grade 1  Foundational Literacy ──
  { code:'L1-FL-OL-01', level:'L1', domain:'FL', sub:'OL', grade:1,
    title:'मौखिक भाषा विकास (कक्षा 1)',
    outcome:'बच्चे अपने आसपास की चीज़ों, लोगों और घटनाओं के बारे में बातचीत कर सकें',
    topic:'आसपास की चीज़ें और लोग', skill:'बोलना और सुनना' },
  { code:'L1-FL-RD-01', level:'L1', domain:'FL', sub:'RD', grade:1,
    title:'पठन कौशल (कक्षा 1)',
    outcome:'बच्चे वर्णमाला के अक्षरों को पहचान सकें और सरल शब्द पढ़ सकें',
    topic:'वर्णमाला और सरल शब्द', skill:'अक्षर पहचान और पढ़ना' },
  { code:'L1-FL-WR-01', level:'L1', domain:'FL', sub:'WR', grade:1,
    title:'लेखन कौशल (कक्षा 1)',
    outcome:'बच्चे वर्णमाला के अक्षर लिख सकें और सरल शब्दों की नकल कर सकें',
    topic:'अक्षर लेखन', skill:'लिखना और नकल करना' },
  // ── L2  Grade 2  Foundational Literacy ──
  { code:'L2-FL-OL-01', level:'L2', domain:'FL', sub:'OL', grade:2,
    title:'मौखिक भाषा विकास (कक्षा 2)',
    outcome:'बच्चे कहानी सुनकर उसे अपने शब्दों में बता सकें और प्रश्न पूछ सकें',
    topic:'कहानी सुनाना', skill:'कहानी सुनना और बताना' },
  { code:'L2-FL-RD-01', level:'L2', domain:'FL', sub:'RD', grade:2,
    title:'पठन कौशल (कक्षा 2)',
    outcome:'बच्चे सरल वाक्य और छोटे अनुच्छेद समझकर पढ़ सकें',
    topic:'सरल वाक्य और अनुच्छेद', skill:'समझकर पढ़ना' },
  { code:'L2-FL-WR-01', level:'L2', domain:'FL', sub:'WR', grade:2,
    title:'लेखन कौशल (कक्षा 2)',
    outcome:'बच्चे छोटे वाक्य लिख सकें और अपने विचार लिखकर व्यक्त कर सकें',
    topic:'वाक्य लेखन', skill:'विचार लिखना' },
  // ── L3  Grade 3  Foundational Literacy ──
  { code:'L3-FL-OL-01', level:'L3', domain:'FL', sub:'OL', grade:3,
    title:'मौखिक भाषा विकास (कक्षा 3)',
    outcome:'बच्चे किसी विषय पर अपने विचार व्यवस्थित रूप से प्रस्तुत कर सकें',
    topic:'विचार प्रस्तुति', skill:'व्यवस्थित बोलना' },
  { code:'L3-FL-RD-01', level:'L3', domain:'FL', sub:'RD', grade:3,
    title:'पठन कौशल (कक्षा 3)',
    outcome:'बच्चे अनुच्छेद पढ़कर प्रश्नों के उत्तर दे सकें और मुख्य विचार बता सकें',
    topic:'अनुच्छेद बोध', skill:'पढ़कर समझना' },
  { code:'L3-FL-WR-01', level:'L3', domain:'FL', sub:'WR', grade:3,
    title:'लेखन कौशल (कक्षा 3)',
    outcome:'बच्चे छोटी कहानी, पत्र या अनुच्छेद स्वतंत्र रूप से लिख सकें',
    topic:'स्वतंत्र लेखन', skill:'कहानी और अनुच्छेद लिखना' },
  // ── L1  Grade 1  Foundational Numeracy ──
  { code:'L1-FN-NS-01', level:'L1', domain:'FN', sub:'NS', grade:1,
    title:'संख्या ज्ञान (कक्षा 1)',
    outcome:'बच्चे 1 से 99 तक की संख्याओं को पहचान सकें, गिन सकें और लिख सकें',
    topic:'1 से 99 तक संख्याएँ', skill:'गिनना और पहचानना' },
  { code:'L1-FN-OP-01', level:'L1', domain:'FN', sub:'OP', grade:1,
    title:'गणितीय संक्रियाएँ (कक्षा 1)',
    outcome:'बच्चे 9 तक की संख्याओं का जोड़ और घटाव कर सकें',
    topic:'जोड़ और घटाव (1-9)', skill:'जोड़ना और घटाना' },
  { code:'L1-FN-SP-01', level:'L1', domain:'FN', sub:'SP', grade:1,
    title:'आकृतियाँ और पैटर्न (कक्षा 1)',
    outcome:'बच्चे बुनियादी आकृतियों को पहचान सकें और सरल पैटर्न बना सकें',
    topic:'आकृतियाँ और पैटर्न', skill:'पहचानना और बनाना' },
  // ── L2  Grade 2  Foundational Numeracy ──
  { code:'L2-FN-NS-01', level:'L2', domain:'FN', sub:'NS', grade:2,
    title:'संख्या ज्ञान (कक्षा 2)',
    outcome:'बच्चे 1 से 999 तक की संख्याओं को पढ़, लिख और तुलना कर सकें',
    topic:'1 से 999 तक संख्याएँ', skill:'पढ़ना, लिखना और तुलना' },
  { code:'L2-FN-OP-01', level:'L2', domain:'FN', sub:'OP', grade:2,
    title:'गणितीय संक्रियाएँ (कक्षा 2)',
    outcome:'बच्चे दो अंकों की संख्याओं का जोड़ और घटाव कर सकें',
    topic:'दो अंकों का जोड़-घटाव', skill:'हासिल के साथ जोड़ना-घटाना' },
  { code:'L2-FN-SP-01', level:'L2', domain:'FN', sub:'SP', grade:2,
    title:'आकृतियाँ और मापन (कक्षा 2)',
    outcome:'बच्चे 2D और 3D आकृतियों में अंतर कर सकें और सरल मापन कर सकें',
    topic:'2D/3D आकृतियाँ और मापन', skill:'अंतर करना और मापना' },
  // ── L3  Grade 3  Foundational Numeracy ──
  { code:'L3-FN-NS-01', level:'L3', domain:'FN', sub:'NS', grade:3,
    title:'संख्या ज्ञान (कक्षा 3)',
    outcome:'बच्चे 1 से 9999 तक की संख्याओं को समझ सकें, तुलना कर सकें और क्रम में लगा सकें',
    topic:'1 से 9999 तक संख्याएँ', skill:'समझना और क्रम लगाना' },
  { code:'L3-FN-OP-01', level:'L3', domain:'FN', sub:'OP', grade:3,
    title:'गणितीय संक्रियाएँ (कक्षा 3)',
    outcome:'बच्चे गुणा और भाग की बुनियादी अवधारणाओं को समझ सकें और सरल प्रश्न हल कर सकें',
    topic:'गुणा और भाग', skill:'गुणा करना और भाग देना' },
  { code:'L3-FN-SP-01', level:'L3', domain:'FN', sub:'SP', grade:3,
    title:'मापन और आँकड़े (कक्षा 3)',
    outcome:'बच्चे लंबाई, वजन, धारिता और समय की माप कर सकें और सरल आँकड़े पढ़ सकें',
    topic:'मापन और आँकड़े', skill:'मापना और आँकड़े पढ़ना' },
];

// ═══════════════════════════════════════════════════════════════
//  2. HINDI CONTENT GENERATOR  (deterministic template engine)
// ═══════════════════════════════════════════════════════════════

function generateLessonScripts(o) {
  return [
    `पाठ योजना: ${o.title}\nउद्देश्य: ${o.outcome}।\nचरण 1: शिक्षक कक्षा ${o.grade} के बच्चों को ${o.topic} से संबंधित चित्र दिखाएँ।\nचरण 2: बच्चों से पूछें कि वे इन चित्रों में क्या देखते हैं।\nचरण 3: ${o.skill} का अभ्यास करवाएँ।\nचरण 4: बच्चों को जोड़ी में बैठाकर एक-दूसरे से चर्चा करवाएँ।\nचरण 5: कक्षा में सामूहिक चर्चा करें और मुख्य बातें दोहराएँ।`,

    `पाठ योजना: ${o.topic} — कहानी विधि\nकक्षा: ${o.grade} | कौशल: ${o.skill}\nशिक्षक एक छोटी कहानी सुनाएँ जो ${o.topic} से जुड़ी हो। कहानी के बीच-बीच में रुककर बच्चों से प्रश्न पूछें। कहानी समाप्त होने पर बच्चों को ${o.skill} का अवसर दें। अंत में बच्चों से कहानी दोहराने को कहें।`,

    `पाठ योजना: ${o.topic} — खेल आधारित\nकक्षा ${o.grade} के लिए ${o.skill} विकसित करने हेतु।\nगतिविधि: बच्चों को गोले में बैठाएँ। शिक्षक ${o.topic} से जुड़ी वस्तुएँ या चित्र कार्ड बाँटें। हर बच्चा अपने कार्ड के बारे में बताए। सही उत्तर पर ताली बजाएँ। इससे ${o.outcome} का लक्ष्य प्राप्त होगा।`,

    `पाठ योजना: ${o.topic} — प्रत्यक्ष अनुभव\nकक्षा: ${o.grade}\nबच्चों को कक्षा के बाहर ले जाएँ। आसपास के वातावरण में ${o.topic} से जुड़ी चीज़ें दिखाएँ। बच्चों से ${o.skill} करवाएँ। वापस कक्षा में आकर अनुभव साझा करने को कहें। श्यामपट्ट पर मुख्य शब्द लिखें।`,

    `पाठ योजना: ${o.topic} — समूह कार्य\nकक्षा: ${o.grade} | लक्ष्य: ${o.outcome}\nबच्चों को 4-5 के समूहों में बाँटें। प्रत्येक समूह को ${o.topic} से जुड़ा एक कार्य दें। समूह अपना कार्य पूरा करे और कक्षा के सामने प्रस्तुत करे। शिक्षक प्रतिक्रिया दें और ${o.skill} पर ध्यान दिलाएँ।`,
  ];
}

function generateActivityInstructions(o) {
  return [
    `गतिविधि: "${o.topic} खोजो"\nबच्चों, अपने आसपास देखो और ${o.topic} से जुड़ी पाँच चीज़ें खोजो। हर चीज़ का नाम बोलो। अपने साथी को भी बताओ। फिर अपनी कॉपी में चित्र बनाओ।`,

    `गतिविधि: "जोड़ी बनाओ"\n${o.topic} से जुड़े चित्र कार्ड लो। हर चित्र को सही शब्द से मिलाओ। ${o.skill} का अभ्यास करो। जो जोड़ियाँ बनीं उन्हें अपनी कॉपी में लिखो।`,

    `गतिविधि: "मेरी कहानी"\n${o.topic} के बारे में एक छोटी कहानी सोचो। अपने समूह के दोस्तों को सुनाओ। कहानी में कम से कम तीन वाक्य हों। सबसे अच्छी कहानी पूरी कक्षा को सुनाई जाएगी।`,

    `गतिविधि: "रंगो और सीखो"\nइस कार्यपत्रक में ${o.topic} से जुड़े चित्र दिए गए हैं। हर चित्र को सही रंग से रंगो। चित्र के नीचे उसका नाम लिखो। ${o.skill} का अभ्यास करो।`,

    `गतिविधि: "शिक्षक बनो"\nआज तुम शिक्षक हो! अपने एक दोस्त को ${o.topic} के बारे में सिखाओ। ${o.skill} में उसकी मदद करो। बाद में बताओ कि तुमने क्या सिखाया।`,
  ];
}

function generateAssessmentPrompts(o) {
  const isDomainFN = o.domain === 'FN';
  return [
    isDomainFN
      ? `मूल्यांकन: ${o.topic}\nनिम्नलिखित प्रश्न हल करो और अपनी कॉपी में उत्तर लिखो। ${o.skill} का उपयोग करो। समय: 10 मिनट।`
      : `मूल्यांकन: ${o.topic}\nनिम्नलिखित चित्र देखो और प्रश्नों के उत्तर बोलकर बताओ। ${o.skill} की जाँच होगी। समय: 10 मिनट।`,

    `मूल्यांकन: ${o.title}\nशिक्षक ${o.topic} से जुड़े तीन प्रश्न पूछेंगे। हर प्रश्न का उत्तर सोचकर दो। सही उत्तर पर एक तारा मिलेगा।`,

    isDomainFN
      ? `मूल्यांकन: ${o.topic} — कार्यपत्रक\nदिए गए प्रश्नों को हल करो। हर प्रश्न के लिए ${o.skill} करो। अपना काम पूरा होने पर शिक्षक को दिखाओ।`
      : `मूल्यांकन: ${o.topic} — मौखिक\nशिक्षक एक चित्र या शब्द दिखाएँगे। तुम्हें ${o.skill} करके उत्तर देना है। ध्यान से देखो और सोचकर बोलो।`,

    `मूल्यांकन: ${o.title} — समूह कार्य\nअपने समूह के साथ मिलकर ${o.topic} से जुड़ा एक कार्य पूरा करो। हर सदस्य को भाग लेना होगा। शिक्षक देखेंगे कि ${o.outcome}।`,

    `मूल्यांकन: स्व-जाँच\nक्या तुम ${o.topic} के बारे में बता सकते हो? क्या तुम ${o.skill} कर सकते हो? अपने आप को तीन तारों में से अंक दो: ⭐ = कोशिश करूँगा, ⭐⭐ = थोड़ा आता है, ⭐⭐⭐ = अच्छे से आता है।`,
  ];
}

// ═══════════════════════════════════════════════════════════════
//  3. TRANSLITERATION ENGINE
//     Phonetic mapping: Devanagari → Ol Chiki / Warang Citi / Nag Mundari
//     These are approximate transliterations, NOT fluent translations.
// ═══════════════════════════════════════════════════════════════

// ── Ol Chiki  (Santhali)  U+1C50 – U+1C7F ──────────────────

const OL_CHIKI_VOWELS = {
  'अ':'\u1C5A','आ':'\u1C5F','इ':'\u1C64','ई':'\u1C64','उ':'\u1C69',
  'ऊ':'\u1C69','ए':'\u1C6E','ऐ':'\u1C6E','ओ':'\u1C73','औ':'\u1C73',
  'ऋ':'\u1C68\u1C64','अं':'\u1C5A\u1C5D','अः':'\u1C5A\u1C77',
};
const OL_CHIKI_MATRAS = {
  '\u093E':'\u1C5F','\u093F':'\u1C64','\u0940':'\u1C64',
  '\u0941':'\u1C69','\u0942':'\u1C69','\u0943':'\u1C68\u1C64',
  '\u0947':'\u1C6E','\u0948':'\u1C6E','\u094B':'\u1C73',
  '\u094C':'\u1C73',
};
const OL_CHIKI_CONSONANTS = {
  'क':'\u1C60','ख':'\u1C60\u1C77','ग':'\u1C5C','घ':'\u1C5C\u1C77',
  'ङ':'\u1C5D','च':'\u1C6A','छ':'\u1C6A\u1C77','ज':'\u1C61',
  'झ':'\u1C61\u1C77','ञ':'\u1C67','ट':'\u1C74','ठ':'\u1C74\u1C77',
  'ड':'\u1C70','ढ':'\u1C70\u1C77','ण':'\u1C6C','त':'\u1C5B',
  'थ':'\u1C5B\u1C77','द':'\u1C6B','ध':'\u1C6B\u1C77','न':'\u1C71',
  'प':'\u1C6F','फ':'\u1C6F\u1C77','ब':'\u1C75','भ':'\u1C75\u1C77',
  'म':'\u1C62','य':'\u1C6D','र':'\u1C68','ल':'\u1C5E',
  'व':'\u1C63','श':'\u1C65','ष':'\u1C65','स':'\u1C65',
  'ह':'\u1C66','क़':'\u1C60','ख़':'\u1C60\u1C77','ग़':'\u1C5C',
  'ज़':'\u1C61','ड़':'\u1C72','ढ़':'\u1C72','फ़':'\u1C6F\u1C77',
};
const OL_CHIKI_DIGITS = {
  '0':'\u1C50','1':'\u1C51','2':'\u1C52','3':'\u1C53','4':'\u1C54',
  '5':'\u1C55','6':'\u1C56','7':'\u1C57','8':'\u1C58','9':'\u1C59',
  '०':'\u1C50','१':'\u1C51','२':'\u1C52','३':'\u1C53','४':'\u1C54',
  '५':'\u1C55','६':'\u1C56','७':'\u1C57','८':'\u1C58','९':'\u1C59',
};

// ── Warang Citi  (Ho)  U+118A0 – U+118FF ────────────────────
// Small letters: U+118C0 – U+118DF

const WC_V = {
  'अ':'\u{118C0}','आ':'\u{118C0}\u{118C0}','इ':'\u{118C8}',
  'ई':'\u{118C8}','उ':'\u{118D4}','ऊ':'\u{118D4}',
  'ए':'\u{118C4}','ऐ':'\u{118C4}','ओ':'\u{118D7}','औ':'\u{118D7}',
  'ऋ':'\u{118D2}\u{118C8}',
};
const WC_M = {
  '\u093E':'\u{118C0}','\u093F':'\u{118C8}','\u0940':'\u{118C8}',
  '\u0941':'\u{118D4}','\u0942':'\u{118D4}','\u0943':'\u{118D2}\u{118C8}',
  '\u0947':'\u{118C4}','\u0948':'\u{118C4}',
  '\u094B':'\u{118D7}','\u094C':'\u{118D7}',
};
const WC_C = {
  'क':'\u{118CA}','ख':'\u{118CA}\u{118C7}','ग':'\u{118C6}',
  'घ':'\u{118C6}\u{118C7}','ङ':'\u{118D1}','च':'\u{118C2}',
  'छ':'\u{118C2}\u{118C7}','ज':'\u{118C9}','झ':'\u{118C9}\u{118C7}',
  'ञ':'\u{118D6}','ट':'\u{118D3}\u{118D3}','ठ':'\u{118D3}\u{118C7}',
  'ड':'\u{118C3}','ढ':'\u{118C3}\u{118C7}','ण':'\u{118D1}',
  'त':'\u{118D3}','थ':'\u{118D3}\u{118C7}','द':'\u{118C3}',
  'ध':'\u{118C3}\u{118C7}','न':'\u{118D1}','प':'\u{118D8}',
  'फ':'\u{118D8}\u{118C7}','ब':'\u{118C1}','भ':'\u{118C1}\u{118C7}',
  'म':'\u{118CB}','य':'\u{118DD}','र':'\u{118D2}','ल':'\u{118CC}',
  'व':'\u{118D6}','श':'\u{118D9}','ष':'\u{118D9}','स':'\u{118D9}',
  'ह':'\u{118C7}','क़':'\u{118CA}','ख़':'\u{118CA}\u{118C7}',
  'ग़':'\u{118C6}','ज़':'\u{118C9}','ड़':'\u{118D2}',
  'ढ़':'\u{118D2}','फ़':'\u{118D8}\u{118C7}',
};
const WC_D = {
  '0':'\u{118E0}','1':'\u{118E1}','2':'\u{118E2}','3':'\u{118E3}',
  '4':'\u{118E4}','5':'\u{118E5}','6':'\u{118E6}','7':'\u{118E7}',
  '8':'\u{118E8}','9':'\u{118E9}',
  '०':'\u{118E0}','१':'\u{118E1}','२':'\u{118E2}','३':'\u{118E3}',
  '४':'\u{118E4}','५':'\u{118E5}','६':'\u{118E6}','७':'\u{118E7}',
  '८':'\u{118E8}','९':'\u{118E9}',
};

// ── Nag Mundari  (Mundari Bani)  U+1E4D0 – U+1E4FF ────────
// Letters: U+1E4D0–U+1E4EF, Digits: U+1E4F0–U+1E4F9

const NM_V = {
  'अ':'\u{1E4D0}','आ':'\u{1E4D0}\u{1E4D0}','इ':'\u{1E4D2}',
  'ई':'\u{1E4D2}','उ':'\u{1E4D3}','ऊ':'\u{1E4D3}',
  'ए':'\u{1E4D1}','ऐ':'\u{1E4D1}','ओ':'\u{1E4D4}','औ':'\u{1E4D4}',
  'ऋ':'\u{1E4E2}\u{1E4D2}',
};
const NM_M = {
  '\u093E':'\u{1E4D0}','\u093F':'\u{1E4D2}','\u0940':'\u{1E4D2}',
  '\u0941':'\u{1E4D3}','\u0942':'\u{1E4D3}','\u0943':'\u{1E4E2}\u{1E4D2}',
  '\u0947':'\u{1E4D1}','\u0948':'\u{1E4D1}',
  '\u094B':'\u{1E4D4}','\u094C':'\u{1E4D4}',
};
const NM_C = {
  'क':'\u{1E4DA}','ख':'\u{1E4DA}\u{1E4D7}','ग':'\u{1E4D6}',
  'घ':'\u{1E4D6}\u{1E4D7}','ङ':'\u{1E4E1}','च':'\u{1E4DB}',
  'छ':'\u{1E4DB}\u{1E4D7}','ज':'\u{1E4DC}','झ':'\u{1E4DC}\u{1E4D7}',
  'ञ':'\u{1E4E0}','ट':'\u{1E4E4}','ठ':'\u{1E4E4}\u{1E4D7}',
  'ड':'\u{1E4DD}','ढ':'\u{1E4DD}\u{1E4D7}','ण':'\u{1E4E1}',
  'त':'\u{1E4E3}','थ':'\u{1E4E3}\u{1E4D7}','द':'\u{1E4DE}',
  'ध':'\u{1E4DE}\u{1E4D7}','न':'\u{1E4E0}','प':'\u{1E4E5}',
  'फ':'\u{1E4E5}\u{1E4D7}','ब':'\u{1E4D8}','भ':'\u{1E4D8}\u{1E4D7}',
  'म':'\u{1E4DF}','य':'\u{1E4E8}','र':'\u{1E4E2}','ल':'\u{1E4E6}',
  'व':'\u{1E4E7}','श':'\u{1E4E9}','ष':'\u{1E4E9}','स':'\u{1E4E9}',
  'ह':'\u{1E4D7}','क़':'\u{1E4DA}','ख़':'\u{1E4DA}\u{1E4D7}',
  'ग़':'\u{1E4D6}','ज़':'\u{1E4DC}','ड़':'\u{1E4E2}',
  'ढ़':'\u{1E4E2}','फ़':'\u{1E4E5}\u{1E4D7}',
};
const NM_D = {
  '0':'\u{1E4F0}','1':'\u{1E4F1}','2':'\u{1E4F2}','3':'\u{1E4F3}',
  '4':'\u{1E4F4}','5':'\u{1E4F5}','6':'\u{1E4F6}','7':'\u{1E4F7}',
  '8':'\u{1E4F8}','9':'\u{1E4F9}',
  '०':'\u{1E4F0}','१':'\u{1E4F1}','२':'\u{1E4F2}','३':'\u{1E4F3}',
  '४':'\u{1E4F4}','५':'\u{1E4F5}','६':'\u{1E4F6}','७':'\u{1E4F7}',
  '८':'\u{1E4F8}','९':'\u{1E4F9}',
};

const SCRIPT_TABLES = {
  santhali:  { vowels: OL_CHIKI_VOWELS, matras: OL_CHIKI_MATRAS, consonants: OL_CHIKI_CONSONANTS, digits: OL_CHIKI_DIGITS, inherent: '\u1C5A' },
  ho:        { vowels: WC_V, matras: WC_M, consonants: WC_C, digits: WC_D, inherent: '\u{118C0}' },
  mundari:   { vowels: NM_V, matras: NM_M, consonants: NM_C, digits: NM_D, inherent: '\u{1E4D0}' },
};

const VIRAMA = '\u094D';
const ANUSVARA = '\u0902';
const VISARGA = '\u0903';
const NUKTA = '\u093C';
const CHANDRABINDU = '\u0901';

function transliterate(hindi, lang) {
  const t = SCRIPT_TABLES[lang];
  if (!t) return hindi;

  const chars = [...hindi];
  let out = '';
  let prevWasConsonant = false;

  for (let i = 0; i < chars.length; i++) {
    const ch = chars[i];
    const cp = ch.codePointAt(0);

    if (t.digits[ch]) {
      out += t.digits[ch];
      prevWasConsonant = false;
    } else if (t.vowels[ch]) {
      out += t.vowels[ch];
      prevWasConsonant = false;
    } else if (t.consonants[ch]) {
      if (prevWasConsonant) out += t.inherent;
      out += t.consonants[ch];
      prevWasConsonant = true;
    } else if (ch === VIRAMA) {
      prevWasConsonant = false;
    } else if (t.matras[ch]) {
      out += t.matras[ch];
      prevWasConsonant = false;
    } else if (ch === ANUSVARA) {
      out += lang === 'santhali' ? '\u1C5D' :
             lang === 'ho' ? '\u{118D1}' : '\u{1E4E1}';
      prevWasConsonant = false;
    } else if (ch === VISARGA) {
      out += lang === 'santhali' ? '\u1C77' :
             lang === 'ho' ? '\u{118C7}' : '\u{1E4D7}';
      prevWasConsonant = false;
    } else if (ch === NUKTA || ch === CHANDRABINDU) {
      // skip — already handled by nuktated consonant lookups
    } else if (cp >= 0x0900 && cp <= 0x097F) {
      // unmapped Devanagari — skip
    } else {
      if (prevWasConsonant) out += t.inherent;
      prevWasConsonant = false;
      out += ch;
    }
  }
  if (prevWasConsonant) out += t.inherent;
  return out;
}

// ═══════════════════════════════════════════════════════════════
//  4. GEMINI API INTEGRATION  (optional, online mode)
// ═══════════════════════════════════════════════════════════════

async function geminiCall(prompt) {
  const url = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${GEMINI_API_KEY}`;
  const body = {
    contents: [{ parts: [{ text: prompt }] }],
    generationConfig: { temperature: 0.7, maxOutputTokens: 4096 },
  };
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Gemini ${res.status}: ${err}`);
  }
  const data = await res.json();
  return data.candidates?.[0]?.content?.parts?.[0]?.text || '';
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms));

async function geminiGenerateContent(outcome) {
  const prompt = `You are a NIPUN Bharat curriculum expert. For this learning outcome:

Code: ${outcome.code}
Grade: ${outcome.grade}
Outcome: ${outcome.outcome}
Topic: ${outcome.topic}
Skill: ${outcome.skill}

Generate in Hindi (Devanagari script):
1. Exactly 5 "lesson_script" entries — teacher-facing lesson plans
2. Exactly 5 "activity_instruction" entries — student-facing activity instructions
3. Exactly 5 "assessment_prompt" entries — assessment/check-for-understanding items

Return ONLY a JSON array of 15 objects, each with:
{"content_type": "lesson_script"|"activity_instruction"|"assessment_prompt", "hindi_text": "..."}

No markdown fencing, no explanation. Just the JSON array.`;

  const raw = await geminiCall(prompt);
  try {
    const cleaned = raw.replace(/```json\n?/g, '').replace(/```\n?/g, '').trim();
    return JSON.parse(cleaned);
  } catch {
    console.warn(`  ⚠ JSON parse failed for ${outcome.code}, falling back to templates`);
    return null;
  }
}

async function geminiTranslate(hindi, langName) {
  const prompt = `Translate the following Hindi educational text into ${langName}. 
Use the native script of ${langName}:
- Santhali → Ol Chiki script (Unicode U+1C50–U+1C7F)
- Ho → Warang Citi script (Unicode U+118A0–U+118FF)  
- Mundari → Nag Mundari / Mundari Bani script (Unicode U+1E4D0–U+1E4FF)

Hindi text:
${hindi}

Return ONLY the translated text in the native script. No explanation.`;

  return (await geminiCall(prompt)).trim();
}

// ═══════════════════════════════════════════════════════════════
//  5. MAIN PIPELINE
// ═══════════════════════════════════════════════════════════════

const LANGUAGES = [
  { key: 'santhali', name: 'Santhali', script: 'ol_chiki', geminiName: 'Santhali (Santali)' },
  { key: 'ho',       name: 'Ho',       script: 'warang_citi', geminiName: 'Ho' },
  { key: 'mundari',  name: 'Mundari',  script: 'nag_mundari', geminiName: 'Mundari' },
];

async function run() {
  console.log('\n╔═══════════════════════════════════════════════════════╗');
  console.log('║       NipunCurriculumSynthesizer  v1.0               ║');
  console.log('║  NIPUN Bharat FLN → Tribal Language JSON Pipeline    ║');
  console.log('╚═══════════════════════════════════════════════════════╝\n');
  console.log(`  Mode:     ${USE_GEMINI ? '🌐 ONLINE (Gemini 2.0 Flash)' : '📦 OFFLINE (deterministic)'}`);
  console.log(`  Outcomes: ${OUTCOMES.length}`);
  console.log(`  Output:   ${OUTPUT_PATH}\n`);

  const rows = [];
  let id = 1;

  for (let oi = 0; oi < OUTCOMES.length; oi++) {
    const o = OUTCOMES[oi];
    console.log(`  [${oi + 1}/${OUTCOMES.length}] ${o.code} — ${o.title}`);

    // ── Step 1: Generate Hindi content ──
    let items = [];

    if (USE_GEMINI) {
      const geminiItems = await geminiGenerateContent(o);
      if (geminiItems && Array.isArray(geminiItems) && geminiItems.length === 15) {
        items = geminiItems;
        console.log('    ✓ Gemini: 15 items generated');
      } else {
        // fallback
        items = buildDeterministicItems(o);
        console.log('    ↩ Gemini failed, using templates');
      }
      await sleep(GEMINI_RPM_DELAY);
    } else {
      items = buildDeterministicItems(o);
    }

    // ── Step 2: Translate and build rows ──
    for (const item of items) {
      for (const lang of LANGUAGES) {
        let tribalText;

        if (USE_GEMINI) {
          try {
            tribalText = await geminiTranslate(item.hindi_text, lang.geminiName);
            await sleep(GEMINI_RPM_DELAY);
          } catch (err) {
            console.warn(`    ⚠ Gemini translate failed for ${lang.key}: ${err.message}`);
            tribalText = transliterate(item.hindi_text, lang.key);
          }
        } else {
          tribalText = transliterate(item.hindi_text, lang.key);
        }

        rows.push({
          id: id++,
          nipun_code: o.code,
          content_type: item.content_type,
          hindi_text: item.hindi_text,
          tribal_language: lang.key,
          tribal_text: tribalText,
          target_script: lang.script,
          machine_translated: true,
        });
      }
    }
    const countForOutcome = items.length * LANGUAGES.length;
    console.log(`    → ${countForOutcome} rows (${items.length} items × ${LANGUAGES.length} languages)`);
  }

  // ── Step 3: Write output ──
  const outDir = dirname(OUTPUT_PATH);
  if (!existsSync(outDir)) mkdirSync(outDir, { recursive: true });

  writeFileSync(OUTPUT_PATH, JSON.stringify(rows, null, 2), 'utf-8');

  // ── Summary ──
  console.log('\n  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`  ✅  Done! ${rows.length} rows written.`);
  console.log(`  📄  ${OUTPUT_PATH}`);
  console.log('  ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  validate(rows);
  return rows;
}

function buildDeterministicItems(o) {
  const ls = generateLessonScripts(o);
  const ai = generateActivityInstructions(o);
  const ap = generateAssessmentPrompts(o);
  return [
    ...ls.map(t => ({ content_type: 'lesson_script', hindi_text: t })),
    ...ai.map(t => ({ content_type: 'activity_instruction', hindi_text: t })),
    ...ap.map(t => ({ content_type: 'assessment_prompt', hindi_text: t })),
  ];
}

// ═══════════════════════════════════════════════════════════════
//  6. VALIDATION
// ═══════════════════════════════════════════════════════════════

function validate(rows) {
  console.log('  Validating...');
  let errors = 0;
  const REQUIRED = ['id','nipun_code','content_type','hindi_text','tribal_language','tribal_text','target_script'];

  // schema check
  for (const row of rows) {
    for (const field of REQUIRED) {
      if (row[field] === undefined || row[field] === null || row[field] === '') {
        console.error(`    ✗ Row ${row.id}: missing field "${field}"`);
        errors++;
      }
    }
  }

  // count check
  const expected = OUTCOMES.length * 15 * LANGUAGES.length;
  if (rows.length !== expected) {
    console.error(`    ✗ Expected ${expected} rows, got ${rows.length}`);
    errors++;
  }

  // nipun_code pattern
  const codeRe = /^L[1-3]-F[LN]-[A-Z]{2}-\d{2}$/;
  for (const row of rows) {
    if (!codeRe.test(row.nipun_code)) {
      console.error(`    ✗ Row ${row.id}: bad nipun_code "${row.nipun_code}"`);
      errors++;
    }
  }

  // Unicode range spot-check
  const RANGES = {
    ol_chiki:     [0x1C50, 0x1C7F],
    warang_citi:  [0x118A0, 0x118FF],
    nag_mundari:  [0x1E4D0, 0x1E4FF],
  };
  for (const script of Object.keys(RANGES)) {
    const [lo, hi] = RANGES[script];
    const sample = rows.find(r => r.target_script === script);
    if (sample) {
      const cps = [...sample.tribal_text].map(c => c.codePointAt(0));
      const inRange = cps.some(cp => cp >= lo && cp <= hi);
      if (inRange) {
        console.log(`    ✓ ${script}: contains characters in U+${lo.toString(16).toUpperCase()}–U+${hi.toString(16).toUpperCase()}`);
      } else {
        console.error(`    ✗ ${script}: no characters found in expected range`);
        errors++;
      }
    }
  }

  // content_type distribution
  const typeCounts = {};
  for (const r of rows) typeCounts[r.content_type] = (typeCounts[r.content_type] || 0) + 1;
  console.log(`    Distribution: ${JSON.stringify(typeCounts)}`);

  if (errors === 0) {
    console.log('    ✓ All validation checks passed!\n');
  } else {
    console.error(`    ✗ ${errors} validation error(s)\n`);
    process.exitCode = 1;
  }
}

// ── Run ─────────────────────────────────────────────────────────
run().catch(err => {
  console.error('Fatal:', err);
  process.exitCode = 1;
});
