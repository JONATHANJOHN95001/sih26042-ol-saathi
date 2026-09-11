# -*- coding: utf-8 -*-
"""
Give every pack entry a published NIPUN Bharat learning-outcome code.

The pack originally shipped labels like ROUTINE and FL-OL. Those were our own
shorthand. A later pass replaced them with goal-and-domain tags such as
EC-OL-G1, and deliberately stopped short of outcome codes, on the grounds that
an unverifiable code which looks official is worse than an honest mapping.

That reasoning was right, and the only thing missing was the source document.
It is now in hand: CBSE Academic publishes the official mapping of NIPUN Bharat
key competencies to learning outcomes, per class, and the codes below are taken
from the Class 1 sheet verbatim.

  https://cbseacademic.nic.in/hpc/pdf/mapping-NIPUN-Bharat-Key-Competencies-Learning-Outcomes-Class1.pdf

Code shape is <PREFIX> <LEVEL>.<NUMBER>[a|b], e.g. "ECL2 4.1a".
  ECL1  first language, which the framework says may be implemented for the
        mother tongue or regional language, naming Gondi among its examples
  ECL2  second language
  HW    health and wellbeing        IL / ILM  involved learners, ILM numeracy
  Level 4 is Class 1. Levels 5 and 6 are Classes 2 and 3.

Each entry also carries `nipunOutcome`, the verbatim outcome sentence, so a
printed worksheet can cite the outcome rather than a bare code that no reader
can look up.

Runs over every pack, not just Santali. Re-running is safe: entries already
carrying a published code keep it, and audio fields are never discarded.
"""
import collections
import glob
import io
import json
import os

PACK_GLOB = os.path.join('app', 'src', 'main', 'assets', 'pack', 'pack.*.json')

GOALS = {
    'HW': 'Children maintain good health and well-being',
    'EC': 'Children become effective communicators',
    'IL': 'Children become involved learners',
}

DOMAINS = {
    'OL': 'Oral Language Development',
    'DC': 'Decoding',
    'RC': 'Reading Comprehension',
    'WR': 'Writing',
    'NS': 'Number Sense',
    'NO': 'Number Operations',
    'CR': 'Classroom Routine',
    'HN': 'Health and Nutrition',
    'SE': 'Social and Emotional Development',
}

# The published outcomes this content maps onto: code -> (goal, domain, text).
OUTCOMES = {
    'ECL1 4.1': ('EC', 'OL',
        'Uses own language/school language to express their needs and ask '
        'questions to gain information.'),
    'ECL1 4.6': ('EC', 'RC',
        'Relates the picture with the text to predict and understand.'),
    'ECL1 4.8': ('EC', 'DC',
        'Shows awareness of figures of letters and sounds while reading '
        'stories and poems, and uses them while writing.'),
    'ECL2 4.1a': ('EC', 'CR',
        'Listens to words, greetings, polite forms of expression, and '
        'responding in home language.'),
    'ECL2 4.5': ('EC', 'WR',
        'Forms letters correctly, uses sound-symbol correspondence to write '
        'invented spellings.'),
    'HW 4.7': ('HW', 'SE',
        'Expresses her/his emotions in socially approved ways.'),
    'HW 4.13b': ('HW', 'HN',
        'Identifies locally available food items and understands the '
        'importance of food and water as a source of energy.'),
    'ILM 4.9': ('IL', 'NS',
        'Counts objects up to 20, concretely and pictorially.'),
    'ILM 4.14': ('IL', 'NO',
        'Constructs addition facts up to 18 by using concrete objects and '
        'applying them in daily life.'),
}

# Per-entry assignment, where the entry is more specific than its label.
EXPLICIT = {
    'p38': 'HW 4.7',                                          # are you all right
    'p39': 'HW 4.13b',                                        # drink some water
    'p32': 'ECL1 4.8', 'p33': 'ECL1 4.8', 'p34': 'ECL1 4.8',  # letters, sounds
    'p12': 'ECL2 4.5',                                        # write your name
    'p27': 'ILM 4.9', 'p28': 'ILM 4.9',
    'p30': 'ILM 4.9', 'p31': 'ILM 4.9',
    'p29': 'ILM 4.14',                                        # two and three
    'p13': 'ECL1 4.6',                                        # picture prompt
}

# Fallback by whichever label the entry currently carries. Covers the original
# shorthand and the intermediate goal-and-domain tags, so a pack sitting at any
# past revision converges on the same published code.
BY_OLD = {
    'ROUTINE': 'ECL2 4.1a', 'EC-CR-G1': 'ECL2 4.1a',
    'FL-OL': 'ECL1 4.1', 'EC-OL-G1': 'ECL1 4.1',
    'FL-WR': 'ECL2 4.5', 'EC-WR-G1': 'ECL2 4.5',
    'FL-RD': 'ECL1 4.6', 'EC-RC-G1': 'ECL1 4.6',
    'EC-DC-G1': 'ECL1 4.8',
    'FN-NS': 'ILM 4.9', 'IL-NS-G1': 'ILM 4.9',
    'FN-OP': 'ILM 4.14', 'IL-NO-G1': 'ILM 4.14',
    'HW-CR-G1': 'HW 4.7',
}

FRAMEWORK = {
    'name': 'NIPUN Bharat, Ministry of Education, 2021',
    'alignment': 'published learning outcome',
    'outcomeSource': (
        'CBSE Academic, Suggestive mapping of NIPUN Bharat Key Competencies '
        'with Learning Outcomes, Class 1'
    ),
    'outcomeSourceUrl': (
        'https://cbseacademic.nic.in/hpc/pdf/'
        'mapping-NIPUN-Bharat-Key-Competencies-Learning-Outcomes-Class1.pdf'
    ),
    'levelNote': (
        'Code level 4 corresponds to Class 1. Levels 5 and 6 are Classes 2 '
        'and 3.'
    ),
    'note': (
        'Each entry carries a published NIPUN Bharat learning-outcome code '
        'and the verbatim text of that outcome.'
    ),
    'goals': GOALS,
    'domains': DOMAINS,
}


def code_for(entry_id, entry):
    """Published code for one entry, or raise if nothing maps."""
    current = entry.get('nipun', '')
    if current in OUTCOMES:          # already published, leave it alone
        return current
    code = EXPLICIT.get(entry_id) or BY_OLD.get(current)
    if code is None:
        raise SystemExit(
            'no NIPUN mapping for %s (label %r)' % (entry_id, current))
    return code


def apply(entry_id, node, counts):
    """Walk one entry, which may be a phrase or a group of phrases."""
    if not isinstance(node, dict):
        return
    if 'source' in node or 'nipun' in node:
        code = code_for(entry_id, node)
        goal, domain, text = OUTCOMES[code]
        node['nipun'] = code
        node['nipunOutcome'] = text
        node['nipunGoal'] = GOALS[goal]
        node['nipunDomain'] = DOMAINS[domain]
        node['nipunGrade'] = 1
        # An empty audio field reads back from org.json as the string "null",
        # so absent has to mean absent. A real recording is never discarded:
        # apply_audio.py may well have run since the pack was built.
        for field in ('audio', 'audioProvenance'):
            if not node.get(field):
                node.pop(field, None)
        counts[code] += 1
        return
    for child_id, child in node.items():
        apply(child_id if child_id in EXPLICIT else entry_id, child, counts)


def main():
    packs = sorted(glob.glob(PACK_GLOB))
    if not packs:
        raise SystemExit('no packs found at %s' % PACK_GLOB)

    grand = collections.Counter()
    for path in packs:
        pack = json.load(io.open(path, encoding='utf-8'))
        counts = collections.Counter()
        for entry_id, entry in pack.get('entries', {}).items():
            apply(entry_id, entry, counts)
        if not counts:
            print('%-24s no entries, skipped' % os.path.basename(path))
            continue
        pack['nipunFramework'] = dict(FRAMEWORK)
        with io.open(path, 'w', encoding='utf-8', newline='\n') as fh:
            json.dump(pack, fh, ensure_ascii=False, indent=2, sort_keys=True)
            fh.write('\n')
        grand.update(counts)
        print('%-24s %3d entries' % (os.path.basename(path), sum(counts.values())))

    print('\n%d packs, %d entries' % (len(packs), sum(grand.values())))
    for code, n in sorted(grand.items()):
        goal, domain, _ = OUTCOMES[code]
        print('  %-10s %4d   %s / %s' % (code, n, goal, DOMAINS[domain]))


if __name__ == '__main__':
    main()
