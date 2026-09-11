# Santali cross-check: two machine routes compared

Generated 2026-09-10 by `bhashini/crosscheck_sat.mjs`. **This is not a review.** No Santali speaker has checked any line, and nothing in the pack was changed.

- **Pack route:** human-written English, translated to Santali by AI4Bharat IndicTrans2 1B, run locally.
- **Second route:** the Hindi for the same line, translated straight to Santali by Bhashini (`ai4bharat/indictrans-v2-all-gpu--t4`).
- **Round trip:** each pack line translated back to English by `tools/backtranslate_qa.py` and compared with the English it came from (0 to 1, higher is closer).
- Both routes are the IndicTrans2 family, so agreement can also be a shared mistake. Disagreement is the useful signal.
- Similarity between routes is chrF (character n-grams 1 to 4, beta 2, spaces ignored). 1.00 means identical.
- Two routes can both be right and still differ: the pack translated English, Bhashini translated Hindi, and a short classroom phrase has more than one good Santali rendering.

| Band | chrF | Lines |
|---|---|---|
| Identical | 1.00 | 2 |
| Close | 0.70 to 0.99 | 7 |
| Partial | 0.40 to 0.69 | 23 |
| Different | below 0.40 | 21 |

53 of 53 lines compared. Every Bhashini output was pure Ol Chiki.

## Broken outputs, visible without reading Santali

**Bhashini:** 2 of 53 outputs loop on a word or a letter (p14, p22). For these lines the comparison says nothing about the pack.

**Pack:** 0 of 53 shipped lines show the same pattern.

## Review first

Lines where the pack's own round trip scored below 0.5 and a working second route also disagrees (chrF below 0.4). Two independent signals pointing at the same line. Give these to a Santali speaker before anything else.

| Entry | Round trip | chrF | English source | Pack line | Pack line, back in English |
|---|---|---|---|---|---|
| p20 | 0.19 | 0.11 | Correct answer. | ᱡᱚᱛᱷᱟᱛ ᱛᱮᱞᱟ ᱮᱢ ᱢᱮ ᱾ | Come up with an appropriate response. |
| p19 | 0.36 | 0.05 | Well done! | ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱠᱚᱨᱟᱣ ᱦᱩᱭ ᱟᱠᱟᱱᱟ ᱾ | It has been done very well. |
| p21 | 0.22 | 0.21 | Never mind, it is all right. | ᱛᱤᱥᱦᱚᱸ ᱟᱞᱚᱢ ᱩᱭᱦᱟᱹᱨᱟ ᱡᱮ ᱥᱟᱱᱟᱢᱟᱜ ᱜᱮ ᱴᱷᱤᱠ ᱜᱮᱭᱟ ᱾ | Don 't ever assume that everything is perfect. |
| p10 | 0.29 | 0.20 | Listen carefully. | ᱥᱚᱱᱛᱚᱨ ᱠᱟᱛᱮ ᱟᱸᱡᱚᱢ ᱢᱮ ᱾ | Listen to it with a sense of foreboding. |
| neema-dadi.l4 | 0.18 | 0.39 | Her knees hurt. | ᱩᱱᱤᱭᱟᱜ ᱜᱩᱱᱴᱷᱮ ᱨᱮ ᱦᱟᱥᱩ ᱞᱮᱱᱟ ᱾ | He suffered an injury to his knee. |
| p15 | 0.38 | 0.23 | Please be quiet. | ᱫᱟᱭᱟ ᱠᱟᱛᱮ ᱛᱷᱤᱨ ᱛᱟᱦᱮᱱ ᱢᱮ ᱾ | Please, please, please, please be still. |
| p38 | 0.36 | 0.27 | Are you all right? | ᱟᱢ ᱪᱮᱫ ᱴᱷᱤᱠ ᱟᱢ? | Are you sure you are doing the right thing? |
| p16 | 0.42 | 0.22 | Sit in pairs. | ᱡᱩᱴᱤ ᱠᱟᱛᱮ ᱫᱩᱲᱩᱵ ᱢᱮ ᱾ | Sit down in a pair of shoes. |
| neema-dadi.l9 | 0.35 | 0.29 | Neema ran and fetched grandmother's slippers. | ᱱᱤᱢᱟ ᱫᱟᱹᱲ ᱠᱟᱛᱮ ᱜᱚᱲᱚᱢ ᱜᱚᱲᱚᱢ ᱠᱩᱲᱤᱭᱟᱜ ᱪᱟᱯᱚᱞᱮ ᱟᱹᱜᱩ ᱠᱮᱫᱟ ᱾ | Nima ran to get the slippers for her grandchildren. |
| p11 | 0.36 | 0.34 | Speak loudly. | ᱡᱳᱨᱛᱮ ᱨᱚᱲ ᱢᱮ ᱾ | Speak in a firm tone. |
| p06 | 0.48 | 0.38 | Open your book. | ᱟᱢᱟᱜ ᱯᱚᱛᱚᱵ ᱡᱷᱤᱡ ᱢᱮ ᱾ | Open up the book that you are reading. |
| neema-dadi.l5 | 0.48 | 0.40 | She waits eagerly for Neema. | ᱩᱱᱤ ᱫᱚ ᱩᱫᱜᱟᱹᱣ ᱥᱟᱞᱟᱜ ᱱᱤᱢᱟ ᱞᱟᱹᱜᱤᱫᱼᱮ ᱛᱟᱸᱜᱤ ᱞᱮᱱᱟ ᱾ | He eagerly waits for Nima to arrive. |

## Every line, least agreement first

| Entry | chrF | Round trip | Flags | Hindi | Pack (IndicTrans2 via English) | Bhashini (from Hindi) |
|---|---|---|---|---|---|---|
| p14 | 0.04 | 0.29 | Bhashini output broken | हाथ उठाओ। | ᱛᱤ ᱨᱟᱠᱟᱵ ᱢᱮ ᱾ | ᱟᱭᱢᱟᱜᱟᱱ ᱟᱭᱢᱟᱜᱟᱱ ᱟᱭᱢᱟᱜᱟᱱ ᱟᱭᱢᱟᱜᱟᱱ ᱾ |
| p19 | 0.05 | 0.36 | review first | शाबाश! | ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱠᱚᱨᱟᱣ ᱦᱩᱭ ᱟᱠᱟᱱᱟ ᱾ | ᱥᱟᱵᱟᱥ! |
| p20 | 0.11 | 0.19 | review first | सही जवाब। | ᱡᱚᱛᱷᱟᱛ ᱛᱮᱞᱟ ᱮᱢ ᱢᱮ ᱾ | ᱥᱚᱨᱟᱥᱚᱨᱤ ᱩᱛᱛᱚᱨ ᱾ |
| p29 | 0.15 | 0.99 |  | दो और तीन कितने होते हैं? | ᱵᱟᱨ ᱥᱮᱞᱮᱫ ᱯᱮ ᱫᱚ ᱪᱮᱫ | ᱵᱟᱨ ᱟᱨ ᱯᱮᱭᱟ ᱛᱤᱱᱟᱹᱜ? |
| p10 | 0.20 | 0.29 | review first | ध्यान से सुनो। | ᱥᱚᱱᱛᱚᱨ ᱠᱟᱛᱮ ᱟᱸᱡᱚᱢ ᱢᱮ ᱾ | ᱱᱟᱯᱟᱭ ᱛᱮ ᱵᱟᱰᱟᱭ ᱢᱮ ᱾ |
| p21 | 0.21 | 0.22 | review first | कोई बात नहीं। | ᱛᱤᱥᱦᱚᱸ ᱟᱞᱚᱢ ᱩᱭᱦᱟᱹᱨᱟ ᱡᱮ ᱥᱟᱱᱟᱢᱟᱜ ᱜᱮ ᱴᱷᱤᱠ ᱜᱮᱭᱟ ᱾ | ᱱᱚᱶᱟ ᱫᱚ ᱪᱮᱫ ᱦᱚᱸ ᱵᱟᱝ ᱠᱟᱱᱟ ᱾ |
| p16 | 0.22 | 0.42 | review first | जोड़ी में बैठो। | ᱡᱩᱴᱤ ᱠᱟᱛᱮ ᱫᱩᱲᱩᱵ ᱢᱮ ᱾ | ᱡᱳᱲᱟᱨᱮ ᱥᱮᱞᱮᱫ ᱢᱮ ᱾ |
| neema-dadi.c2 | 0.23 | 0.64 |  | दादी के कहाँ दर्द रहता है? | ᱜᱚᱲᱚᱢ ᱟᱭᱳᱣᱟᱜ ᱫᱚ ᱚᱠᱟ ᱨᱮ ᱦᱟᱹᱥᱩ ᱢᱮᱱᱟᱜ - ᱟ ᱾ | ᱢᱟᱨᱮᱭᱟᱜ ᱜᱷᱟᱹᱞ ᱫᱚ ᱪᱮᱫ ᱨᱮ ᱛᱟᱦᱮᱸᱱᱟ? |
| p15 | 0.23 | 0.38 | review first | शांत हो जाओ। | ᱫᱟᱭᱟ ᱠᱟᱛᱮ ᱛᱷᱤᱨ ᱛᱟᱦᱮᱱ ᱢᱮ ᱾ | ᱥᱟᱹᱱᱛᱤ ᱢᱮ ᱾ |
| p22 | 0.26 | 0.28 | Bhashini output broken | क्या तुम समझ गए? | ᱟᱢ ᱪᱮᱫ ᱮᱢ ᱵᱩᱡᱷᱟᱹᱣ ᱟᱠᱟᱫᱟ | ᱟᱢ ᱪᱮᱫ ᱚᱱᱟᱢ ᱮᱢᱢᱢᱢᱢᱢᱢᱟᱢ ᱠᱟᱱᱟ? |
| p34 | 0.26 | 0.99 |  | इस शब्द की पहली ध्वनि क्या है? | ᱱᱚᱶᱟ ᱟᱹᱲᱟᱹ ᱨᱮ ᱯᱩᱭᱞᱩ ᱟᱲᱟᱝ ᱫᱚ ᱪᱮᱫ ᱠᱟᱱᱟ | ᱱᱚᱶᱟ ᱥᱟᱵᱟᱫ ᱨᱮᱭᱟᱜ ᱯᱳᱭᱞᱳ ᱥᱟᱵᱟᱫ ᱪᱮᱫ? |
| p38 | 0.27 | 0.36 | review first | क्या तुम ठीक हो? | ᱟᱢ ᱪᱮᱫ ᱴᱷᱤᱠ ᱟᱢ? | ᱟᱢ ᱪᱮᱫ ᱱᱟᱯᱟᱭ ᱢᱮᱱᱟᱜᱼᱟ? |
| neema-dadi.l9 | 0.29 | 0.35 | review first | नीमा दौड़कर दादी की चप्पलें ले आई। | ᱱᱤᱢᱟ ᱫᱟᱹᱲ ᱠᱟᱛᱮ ᱜᱚᱲᱚᱢ ᱜᱚᱲᱚᱢ ᱠᱩᱲᱤᱭᱟᱜ ᱪᱟᱯᱚᱞᱮ ᱟᱹᱜᱩ ᱠᱮᱫᱟ ᱾ | ᱱᱤᱢᱟ ᱟᱡ ᱢᱟᱨᱮᱭᱟᱜ ᱪᱚᱞᱟᱹ ᱦᱟᱛᱟᱣ ᱞᱟᱹᱜᱤᱫ ᱮ ᱪᱟᱞᱟᱣ ᱞᱮᱱᱟ ᱾ |
| p05 | 0.30 | 1.00 |  | अब छुट्टी का समय है। | ᱚᱲᱟᱜ ᱪᱟᱞᱟᱜ ᱚᱠᱛᱚ ᱦᱩᱭ ᱟᱠᱟᱱᱟ ᱾ | ᱱᱚᱶᱟ ᱚᱠᱛᱚ ᱫᱚ ᱪᱷᱟᱹᱲ ᱨᱮᱭᱟᱜ ᱚᱠᱛᱚ ᱾ |
| neema-dadi.l3 | 0.31 | 0.83 |  | दादी कहीं नहीं आती-जाती हैं। | ᱜᱚᱲᱚᱢ ᱟᱭᱳ ᱫᱚ ᱚᱠᱟᱨᱮᱦᱚᱸ ᱵᱟᱭ ᱪᱟᱞᱟᱜᱼᱟ ᱾ | ᱟᱭᱢᱟᱜᱮ ᱫᱚ ᱵᱟᱝ ᱠᱚ ᱦᱮᱡᱚᱜᱼᱟ ᱟᱨ ᱵᱟᱝ ᱠᱚ ᱪᱟᱞᱟᱣᱚᱜᱼᱟ ᱾ |
| p03 | 0.32 | 0.51 |  | आज हम क्या सीखेंगे? | ᱛᱮᱦᱮᱧ ᱟᱞᱮ ᱪᱮᱫᱢ ᱥᱮᱲᱟᱭᱟ | ᱛᱮᱦᱮᱧ ᱤᱧᱟᱹᱜ ᱪᱮᱫ ᱥᱮᱪᱮᱫ ᱦᱩᱭᱩᱜ ᱠᱟᱱᱟ? |
| p11 | 0.34 | 0.36 | review first | ज़ोर से बोलो। | ᱡᱳᱨᱛᱮ ᱨᱚᱲ ᱢᱮ ᱾ | ᱡᱳᱨ ᱛᱮ ᱠᱟᱛᱷᱟ ᱞᱟᱹᱭ ᱢᱮ ᱾ |
| p06 | 0.38 | 0.48 | review first | किताब खोलो। | ᱟᱢᱟᱜ ᱯᱚᱛᱚᱵ ᱡᱷᱤᱡ ᱢᱮ ᱾ | ᱯᱚᱛᱚᱵ ᱫᱚ ᱮᱦᱚᱵ ᱢᱮ ᱾ |
| neema-dadi.l8 | 0.38 | 0.63 |  | एक दिन दादी बोलीं, मेरा समय नहीं कटता। | ᱢᱤᱫ ᱫᱤᱱ ᱜᱚᱲᱚᱢ ᱟᱭᱳᱭ ᱞᱟᱹᱭ ᱠᱮᱫᱟ, ᱤᱧᱟᱹᱜ ᱚᱠᱛᱚ ᱫᱚ ᱵᱟᱝ ᱯᱟᱨᱚᱢᱚᱜᱼᱟ ᱾ | ᱢᱤᱫ ᱫᱤᱱ, ᱟᱭᱢᱟᱜᱮ ᱢᱮᱱ ᱞᱮᱫᱟᱭ, ᱤᱧ ᱚᱠᱛᱚ ᱵᱮᱥᱟᱭ ᱵᱟᱝ ᱠᱟᱱᱟ ᱾ |
| neema-dadi.l4 | 0.39 | 0.18 | review first | उनके घुटनों में दर्द रहता है। | ᱩᱱᱤᱭᱟᱜ ᱜᱩᱱᱴᱷᱮ ᱨᱮ ᱦᱟᱥᱩ ᱞᱮᱱᱟ ᱾ | ᱩᱱᱤ ᱫᱚ ᱜᱩᱱᱤ ᱨᱮ ᱜᱷᱟᱹᱞ ᱞᱮᱱᱟ ᱾ |
| neema-dadi.l5 | 0.40 | 0.48 | review first | उन्हें नीमा का बहुत इंतज़ार होता है। | ᱩᱱᱤ ᱫᱚ ᱩᱫᱜᱟᱹᱣ ᱥᱟᱞᱟᱜ ᱱᱤᱢᱟ ᱞᱟᱹᱜᱤᱫᱼᱮ ᱛᱟᱸᱜᱤ ᱞᱮᱱᱟ ᱾ | ᱩᱱᱠᱩ ᱫᱚ ᱱᱤᱢᱟᱜ ᱞᱟᱹᱜᱤᱫ ᱟᱹᱰᱤ ᱠᱚ ᱵᱤᱰᱟᱹᱣ ᱞᱮᱫᱟ ᱾ |
| neema-dadi.l6 | 0.41 | 0.68 |  | नीमा रोज़ खाना खाते-खाते स्कूल की बातें सुनाती है। | ᱫᱤᱱᱟᱹᱢ ᱡᱚᱢ ᱚᱠᱛᱚ ᱱᱤᱢᱟ ᱩᱱᱤ ᱵᱤᱨᱫᱟᱹᱜᱟᱲ ᱵᱟᱨᱮᱛᱮᱭ ᱞᱟᱹᱭᱟ ᱾ | ᱱᱤᱢᱟ ᱡᱚᱛᱚ ᱫᱤᱱ ᱜᱮ ᱵᱤᱨᱫᱟᱹᱜᱟᱲ ᱨᱮᱭᱟᱜ ᱠᱟᱛᱷᱟ ᱠᱚ ᱠᱷᱚᱡᱟ ᱟᱨ ᱠᱟᱛᱷᱟ ᱠᱚ ᱞᱟᱹᱭᱟ ᱾ |
| p40 | 0.43 | 0.38 |  | घर पर अभ्यास करना। | ᱚᱲᱟᱜ ᱨᱮ ᱥᱚᱲᱠᱚ ᱢᱮ ᱾ | ᱚᱲᱟᱜ ᱨᱮ ᱠᱷᱮᱹᱞᱳᱰ ᱢᱮ ᱾ |
| p07 | 0.45 | 0.47 |  | किताब बंद करो। | ᱟᱢᱟᱜ ᱯᱚᱛᱚᱵ ᱵᱚᱱᱫᱚ ᱢᱮ ᱾ | ᱯᱚᱛᱚᱵ ᱫᱚ ᱵᱚᱱᱚᱫᱚᱞ ᱢᱮ ᱾ |
| p08 | 0.47 | 0.42 |  | पेंसिल उठाओ। | ᱟᱢᱟᱜ ᱯᱮᱱᱥᱤᱞ ᱨᱟᱠᱟᱵ ᱢᱮ ᱾ | ᱯᱮᱱᱥᱤᱞ ᱦᱟᱛᱟᱣ ᱢᱮ ᱾ |
| p39 | 0.48 | 0.38 |  | पानी पी लो। | ᱱᱟᱥᱮ ᱫᱟᱜ ᱧᱩᱭ ᱢᱮ ᱾ | ᱫᱟᱜᱧᱟᱢ ᱢᱮ ᱾ |
| p35 | 0.48 | 1.00 |  | कहानी सुनो। | ᱠᱟᱹᱦᱱᱤ ᱫᱚ ᱟᱸᱡᱚᱢ ᱢᱮ ᱾ | ᱠᱟᱹᱦᱱᱤᱭ ᱥᱩᱧ ᱢᱮ ᱾ |
| p02 | 0.49 | 0.10 |  | सब बैठ जाओ। | ᱡᱚᱛᱚ ᱦᱚᱲ ᱠᱚ ᱫᱩᱲᱩᱵ ᱮᱱᱟ ᱾ | ᱡᱚᱛᱚ ᱦᱚᱲ ᱠᱚ ᱵᱮᱶᱦᱟᱨᱚᱜᱼᱟ ᱾ |
| p37 | 0.50 | 0.50 |  | यह वाक्य दोहराओ। | ᱱᱚᱣᱟ ᱵᱷᱟᱥᱚᱱ ᱫᱚ ᱫᱚᱲᱦᱟᱛᱮ ᱞᱟᱹᱭ ᱢᱮ ᱾ | ᱱᱚᱶᱟ ᱵᱚᱨᱱᱚᱱ ᱫᱚ ᱵᱟᱨᱮᱛᱮ ᱞᱟᱹᱭ ᱢᱮ ᱾ |
| p28 | 0.50 | 0.79 |  | कितने हैं? | ᱛᱤᱱᱟᱹᱜ ᱜᱟᱱ ᱢᱮᱱᱟᱜ - ᱟ | ᱛᱤᱱᱟᱹᱜ ᱠᱚ ᱢᱮᱱᱟᱜ ᱠᱚᱣᱟ? |
| neema-dadi.c1 | 0.51 | 0.65 |  | इस समय घर पर कौन होता है? | ᱚᱱᱟ ᱚᱠᱛᱚ ᱚᱲᱟᱜ ᱨᱮ ᱚᱠᱚᱭ ᱢᱮᱱᱟᱜ - ᱟ | ᱱᱚᱶᱟ ᱚᱠᱛᱚ ᱚᱲᱟᱜ ᱨᱮ ᱡᱟᱦᱟᱸᱭ ᱠᱚ ᱢᱮᱱᱟᱜ ᱠᱚᱣᱟ? |
| neema-dadi.l7 | 0.53 | 0.81 |  | शाम को नीमा खेलने जाती है। | ᱥᱤᱸᱜᱟᱹᱲ ᱨᱮ ᱱᱤᱢᱟ ᱠᱷᱮᱞᱳᱰ ᱞᱟᱹᱜᱤᱫ ᱮ ᱪᱟᱞᱟᱜᱼᱟ ᱾ | ᱪᱟᱸᱫᱳᱨᱮ, ᱱᱮᱢᱟ ᱠᱷᱮᱞᱳᱰ ᱞᱟᱹᱜᱤᱫ ᱮ ᱥᱮᱱ ᱞᱮᱱᱟ ᱾ |
| p26 | 0.54 | 0.59 |  | क्या तुम्हें कोई सवाल है? | ᱟᱢᱟᱜ ᱠᱤ ᱡᱟᱦᱟᱱ ᱠᱩᱠᱞᱤ ᱢᱮᱱᱟᱜᱼᱟ | ᱟᱢᱟᱜ ᱪᱮᱫ ᱡᱟᱦᱟᱱ ᱠᱟᱛᱷᱟ ᱢᱮᱱᱟᱜᱼᱟ? |
| p09 | 0.54 | 0.20 |  | मेरे पीछे दोहराओ। | ᱤᱧ ᱛᱟᱭᱚᱢ ᱵᱟᱨ ᱫᱷᱟᱣ ᱢᱮ ᱾ | ᱤᱧ ᱛᱟᱭᱚᱢ ᱟᱨᱦᱚᱸ ᱢᱮ ᱾ |
| neema-dadi.c3 | 0.55 | 0.69 |  | अंत में नीमा और दादी कहाँ गईं? | ᱢᱩᱪᱟᱹᱫ ᱨᱮ ᱱᱮᱢᱟ ᱟᱨ ᱜᱚᱲᱚᱢ ᱟᱭᱳ ᱫᱚ ᱚᱠᱟᱨᱮ ᱪᱟᱞᱟᱣ ᱮᱱᱟ | ᱢᱩᱪᱟᱹᱫ ᱨᱮ ᱱᱤᱢ ᱟᱨ ᱫᱟᱫᱤ ᱫᱚ ᱪᱮᱫ ᱮ ᱪᱟᱞᱟᱣ ᱮᱱᱟ ᱾ |
| p25 | 0.55 | 0.40 |  | कौन बताएगा? | ᱚᱠᱚᱭ ᱠᱚ ᱞᱟᱹᱭᱼᱟ ᱢᱮᱹ | ᱡᱟᱦᱟᱸᱭ ᱠᱚ ᱞᱟᱹᱭᱼᱟ? |
| neema-dadi.l2 | 0.58 | 0.52 |  | इस समय घर पर सिर्फ़ दादी होती हैं। | ᱚᱱᱟ ᱚᱠᱛᱚ ᱥᱩᱢᱩᱝ ᱟᱡ ᱜᱚᱲᱚᱢ ᱟᱭᱳ ᱚᱲᱟᱜ ᱨᱮ ᱛᱟᱦᱮᱱᱟ ᱾ | ᱱᱚᱶᱟ ᱚᱠᱛᱚ, ᱚᱲᱟᱜ ᱨᱮ ᱥᱩᱢᱩᱝ ᱟᱭᱢᱟᱜᱮ ᱛᱟᱦᱮᱸᱱᱟ ᱾ |
| p33 | 0.59 | 0.18 |  | यह शब्द पढ़ो। | ᱱᱚᱣᱟ ᱟᱹᱲᱟᱹ ᱫᱚ ᱯᱟᱲᱦᱟᱣ ᱢᱮ ᱾ | ᱱᱚᱶᱟ ᱥᱟᱵᱟᱫ ᱫᱚ ᱯᱟᱲᱦᱟᱣ ᱢᱮ ᱾ |
| p32 | 0.63 | 0.72 |  | यह अक्षर पढ़ो। | ᱱᱚᱶᱟ ᱪᱤᱴᱷᱤ ᱫᱚ ᱯᱟᱲᱦᱟᱣ ᱢᱮ ᱾ | ᱱᱚᱶᱟ ᱞᱟᱹᱞᱤᱥ ᱫᱚ ᱯᱟᱲᱦᱟᱣ ᱢᱮ ᱾ |
| neema-dadi.l1 | 0.65 | 0.86 |  | नीमा दोपहर में दो बजे स्कूल से लौटती है। | ᱱᱤᱢᱟ ᱫᱚ ᱛᱤᱠᱤᱱ ᱵᱟᱨ ᱴᱟᱲᱟᱝ ᱨᱮ ᱵᱤᱨᱫᱟᱹᱜᱟᱲ ᱠᱷᱚᱱ ᱚᱲᱟᱜ ᱮ ᱦᱤᱡᱩᱜᱼᱟ ᱾ | ᱱᱤᱢᱟ ᱵᱤᱨᱫᱟᱹᱜᱟᱲ ᱠᱷᱚᱱ ᱵᱟᱨ ᱴᱟᱲᱟᱝ ᱨᱩᱣᱟᱹᱲ ᱦᱮᱡ ᱮᱱᱟ ᱾ |
| p30 | 0.65 | 0.62 |  | कौन सी संख्या बड़ी है? | ᱚᱠᱟ ᱮᱞ ᱫᱚ ᱢᱟᱨᱟᱝ ᱠᱟᱱᱟ | ᱚᱠᱟ ᱮᱞ ᱢᱟᱨᱟᱝ? |
| p36 | 0.66 | 0.67 |  | अपनी भाषा में बताओ। | ᱟᱢᱟᱜ ᱯᱟᱹᱨᱥᱤ ᱛᱮ ᱞᱟᱹᱭ ᱤᱧᱢᱮ ᱾ | ᱟᱢᱟᱜ ᱯᱟᱹᱨᱥᱤ ᱛᱮ ᱠᱟᱛᱷᱟ ᱞᱟᱹᱭ ᱢᱮ ᱾ |
| neema-dadi.l10 | 0.67 | 0.37 |  | फिर वे दोनों खेल के मैदान की ओर चल पड़े। | ᱚᱱᱟ ᱛᱟᱭᱚᱢ ᱩᱱᱠᱤᱱ ᱵᱟᱱᱟ ᱦᱚᱲ ᱠᱷᱮᱞᱳᱰ ᱴᱷᱟᱶ ᱥᱮᱫ ᱠᱚ ᱞᱟᱦᱟ ᱞᱮᱱᱟ ᱾ | ᱚᱱᱟ ᱛᱟᱭᱚᱢ ᱵᱟᱱᱟᱨ ᱠᱷᱮᱞᱳᱰ ᱴᱷᱟᱶ ᱥᱮᱫ ᱮ ᱪᱟᱞᱟᱣ ᱮᱱᱟ ᱾ |
| p31 | 0.68 | 0.47 |  | इन्हें गिनकर बताओ। | ᱱᱚᱶᱟ ᱠᱚ ᱞᱮᱠᱷᱟ ᱢᱮ ᱟᱨ ᱤᱧ ᱞᱟᱹᱭ ᱤᱧᱢᱮ ᱾ | ᱚᱱᱟ ᱠᱚ ᱞᱮᱠᱷᱟ ᱢᱮ ᱾ |
| p01 | 0.70 | 0.53 |  | नमस्ते बच्चों। | ᱡᱚᱦᱟᱨ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾ | ᱦᱚᱞᱮ ᱜᱤᱫᱽᱨᱟᱹᱠᱚ ᱾ |
| p24 | 0.76 | 0.62 |  | तुम्हारा नाम क्या है? | ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱫᱚ ᱪᱮᱫ | ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱪᱮᱫ? |
| p17 | 0.78 | 0.40 |  | एक बार फिर कोशिश करो। | ᱟᱨᱦᱚᱸ ᱢᱤᱫ ᱫᱷᱟᱣ ᱨᱤᱠᱟᱹᱭ ᱢᱮ ᱾ | ᱟᱨᱦᱚᱸ ᱨᱤᱠᱟᱹᱭ ᱢᱮ ᱾ |
| p13 | 0.81 | 0.76 |  | चित्र देखो। | ᱪᱤᱛᱟᱹᱨ ᱫᱚ ᱧᱮᱞ ᱢᱮ ᱾ | ᱪᱤᱛᱟᱹᱨ ᱧᱮᱞ ᱢᱮ ᱾ |
| p18 | 0.88 | 0.85 |  | बहुत अच्छा! | ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ ᱾ | ᱟᱹᱰᱤ ᱱᱟᱯᱟᱭ! |
| p23 | 0.90 | 0.35 |  | यह क्या है? | ᱱᱚᱶᱟ ᱫᱚ ᱪᱮᱫ ᱠᱟᱱᱟ | ᱱᱚᱶᱟ ᱫᱚ ᱪᱮᱫ |
| p04 | 0.95 | 0.64 |  | कक्षा शुरू करते हैं। | ᱟᱞᱮ ᱠᱞᱟᱥ ᱮᱛᱚᱦᱚᱵ ᱢᱮ ᱾ | ᱠᱞᱟᱥ ᱮᱛᱚᱦᱚᱵ ᱢᱮ ᱾ |
| p12 | 1.00 | 0.24 |  | अपना नाम लिखो। | ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱚᱞ ᱢᱮ ᱾ | ᱟᱢᱟᱜ ᱧᱩᱛᱩᱢ ᱚᱞ ᱢᱮ ᱾ |
| p27 | 1.00 | 0.73 |  | एक से दस तक गिनो। | ᱢᱤᱫ ᱠᱷᱚᱱ ᱜᱮᱞ ᱫᱷᱟᱹᱵᱤᱡ ᱞᱮᱠᱷᱟ ᱢᱮ ᱾ | ᱢᱤᱫ ᱠᱷᱚᱱ ᱜᱮᱞ ᱫᱷᱟᱹᱵᱤᱡ ᱞᱮᱠᱷᱟ ᱢᱮ ᱾ |
